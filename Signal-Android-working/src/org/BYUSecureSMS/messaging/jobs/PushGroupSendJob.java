package org.BYUSecureSMS.messaging.jobs;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.BYUSecureSMS.messaging.ApplicationContext;
import org.BYUSecureSMS.messaging.attachments.Attachment;
import org.BYUSecureSMS.messaging.crypto.MasterSecret;
import org.BYUSecureSMS.messaging.database.Address;
import org.BYUSecureSMS.messaging.database.DatabaseFactory;
import org.BYUSecureSMS.messaging.database.MmsDatabase;
import org.BYUSecureSMS.messaging.database.NoSuchMessageException;
import org.BYUSecureSMS.messaging.database.documents.NetworkFailure;
import org.BYUSecureSMS.messaging.mms.MediaConstraints;
import org.BYUSecureSMS.messaging.mms.MmsException;
import org.BYUSecureSMS.messaging.mms.OutgoingGroupMediaMessage;
import org.BYUSecureSMS.messaging.mms.OutgoingMediaMessage;
import org.BYUSecureSMS.messaging.recipients.RecipientFormattingException;
import org.BYUSecureSMS.messaging.transport.UndeliverableMessageException;
import org.BYUSecureSMS.messaging.util.GroupUtil;
import org.BYUSecureSMS.messaging.dependencies.InjectableType;
import org.BYUSecureSMS.messaging.jobs.requirements.MasterSecretRequirement;
import org.BYUSecureSMS.messaging.recipients.Recipient;
import org.BYUSecureSMS.messaging.recipients.Recipients;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.EncapsulatedExceptions;
import org.whispersystems.signalservice.api.push.exceptions.NetworkFailureException;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContext;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import javax.inject.Inject;

import static org.BYUSecureSMS.messaging.dependencies.SignalCommunicationModule.SignalMessageSenderFactory;

public class PushGroupSendJob extends PushSendJob implements InjectableType {

  private static final long serialVersionUID = 1L;

  private static final String TAG = PushGroupSendJob.class.getSimpleName();

  @Inject transient SignalMessageSenderFactory messageSenderFactory;

  private final long   messageId;
  private final long   filterRecipientId; // Deprecated
  private final String filterAddress;

  public PushGroupSendJob(Context context, long messageId, @NonNull Address destination, @Nullable Address filterAddress) {
    super(context, JobParameters.newBuilder()
                                .withPersistence()
                                .withGroupId(destination.toGroupString())
                                .withRequirement(new MasterSecretRequirement(context))
                                .withRequirement(new NetworkRequirement(context))
                                .withRetryCount(5)
                                .create());

    this.messageId         = messageId;
    this.filterAddress     = filterAddress == null ? null :filterAddress.toPhoneString();
    this.filterRecipientId = -1;
  }

  @Override
  public void onAdded() {
  }

  @Override
  public void onPushSend(MasterSecret masterSecret)
      throws MmsException, IOException, NoSuchMessageException
  {
    MmsDatabase database = DatabaseFactory.getMmsDatabase(context);
    OutgoingMediaMessage message  = database.getOutgoingMessage(masterSecret, messageId);

    try {
      deliver(masterSecret, message, filterAddress == null ? null : Address.fromSerialized(filterAddress));

      database.markAsSent(messageId, true);
      markAttachmentsUploaded(messageId, message.getAttachments());

      if (message.getExpiresIn() > 0 && !message.isExpirationUpdate()) {
        database.markExpireStarted(messageId);
        ApplicationContext.getInstance(context)
                          .getExpiringMessageManager()
                          .scheduleDeletion(messageId, true, message.getExpiresIn());
      }
    } catch (InvalidNumberException | RecipientFormattingException | UndeliverableMessageException e) {
      Log.w(TAG, e);
      database.markAsSentFailed(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
    } catch (EncapsulatedExceptions e) {
      Log.w(TAG, e);
      List<NetworkFailure> failures = new LinkedList<>();

      for (NetworkFailureException nfe : e.getNetworkExceptions()) {
        failures.add(new NetworkFailure(Address.fromSerialized(nfe.getE164number())));
      }

      for (UntrustedIdentityException uie : e.getUntrustedIdentityExceptions()) {
        database.addMismatchedIdentity(messageId, Address.fromSerialized(uie.getE164Number()), uie.getIdentityKey());
      }

      database.addFailures(messageId, failures);

      if (e.getNetworkExceptions().isEmpty() && e.getUntrustedIdentityExceptions().isEmpty()) {
        database.markAsSent(messageId, true);
        markAttachmentsUploaded(messageId, message.getAttachments());
      } else {
        database.markAsSentFailed(messageId);
        notifyMediaMessageDeliveryFailed(context, messageId);
      }
    }
  }

  @Override
  public boolean onShouldRetryThrowable(Exception exception) {
    if (exception instanceof IOException) return true;
    return false;
  }

  @Override
  public void onCanceled() {
    DatabaseFactory.getMmsDatabase(context).markAsSentFailed(messageId);
  }

  private void deliver(MasterSecret masterSecret, OutgoingMediaMessage message, @Nullable Address filterAddress)
      throws IOException, RecipientFormattingException, InvalidNumberException,
      EncapsulatedExceptions, UndeliverableMessageException
  {
    SignalServiceMessageSender    messageSender     = messageSenderFactory.create();
    byte[]                        groupId           = GroupUtil.getDecodedId(message.getRecipients().getPrimaryRecipient().getAddress().toGroupString());
    Recipients                    recipients        = DatabaseFactory.getGroupDatabase(context).getGroupMembers(groupId, false);
    MediaConstraints mediaConstraints  = MediaConstraints.getPushMediaConstraints();
    List<Attachment>              scaledAttachments = scaleAttachments(masterSecret, mediaConstraints, message.getAttachments());
    List<SignalServiceAttachment> attachmentStreams = getAttachmentsFor(masterSecret, scaledAttachments);

    List<SignalServiceAddress>    addresses;

    if (filterAddress != null) addresses = getPushAddresses(filterAddress);
    else                       addresses = getPushAddresses(recipients);

    if (message.isGroup()) {
      OutgoingGroupMediaMessage groupMessage     = (OutgoingGroupMediaMessage) message;
      GroupContext              groupContext     = groupMessage.getGroupContext();
      SignalServiceAttachment   avatar           = attachmentStreams.isEmpty() ? null : attachmentStreams.get(0);
      SignalServiceGroup.Type   type             = groupMessage.isGroupQuit() ? SignalServiceGroup.Type.QUIT : SignalServiceGroup.Type.UPDATE;
      SignalServiceGroup        group            = new SignalServiceGroup(type, groupId, groupContext.getName(), groupContext.getMembersList(), avatar);
      SignalServiceDataMessage  groupDataMessage = new SignalServiceDataMessage(message.getSentTimeMillis(), group, null, null);

      messageSender.sendMessage(addresses, groupDataMessage);
    } else {
      SignalServiceGroup       group        = new SignalServiceGroup(groupId);
      SignalServiceDataMessage groupMessage = new SignalServiceDataMessage(message.getSentTimeMillis(), group,
                                                                           attachmentStreams, message.getBody(), false,
                                                                           (int)(message.getExpiresIn() / 1000),
                                                                           message.isExpirationUpdate());

      messageSender.sendMessage(addresses, groupMessage);
    }
  }

  private List<SignalServiceAddress> getPushAddresses(Address address) {
    List<SignalServiceAddress> addresses = new LinkedList<>();
    addresses.add(getPushAddress(address));
    return addresses;
  }

  private List<SignalServiceAddress> getPushAddresses(Recipients recipients) {
    List<SignalServiceAddress> addresses = new LinkedList<>();

    for (Recipient recipient : recipients.getRecipientsList()) {
      addresses.add(getPushAddress(recipient.getAddress()));
    }

    return addresses;
  }
}
