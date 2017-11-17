package org.BYUSecureSMS.messaging.jobs;

import android.content.Context;
import android.util.Log;

import org.BYUSecureSMS.messaging.crypto.MasterSecret;
import org.BYUSecureSMS.messaging.database.MmsDatabase;
import org.BYUSecureSMS.messaging.database.NoSuchMessageException;
import org.BYUSecureSMS.messaging.mms.OutgoingMediaMessage;
import org.BYUSecureSMS.messaging.service.ExpiringMessageManager;
import org.BYUSecureSMS.messaging.transport.UndeliverableMessageException;
import org.BYUSecureSMS.messaging.ApplicationContext;
import org.BYUSecureSMS.messaging.attachments.Attachment;
import org.BYUSecureSMS.messaging.database.Address;
import org.BYUSecureSMS.messaging.database.DatabaseFactory;
import org.BYUSecureSMS.messaging.dependencies.InjectableType;
import org.BYUSecureSMS.messaging.mms.MediaConstraints;
import org.BYUSecureSMS.messaging.mms.MmsException;
import org.BYUSecureSMS.messaging.transport.InsecureFallbackApprovalException;
import org.BYUSecureSMS.messaging.transport.RetryLaterException;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import javax.inject.Inject;

import static org.BYUSecureSMS.messaging.dependencies.SignalCommunicationModule.SignalMessageSenderFactory;

public class PushMediaSendJob extends PushSendJob implements InjectableType {

  private static final long serialVersionUID = 1L;

  private static final String TAG = PushMediaSendJob.class.getSimpleName();

  @Inject transient SignalMessageSenderFactory messageSenderFactory;

  private final long messageId;

  public PushMediaSendJob(Context context, long messageId, Address destination) {
    super(context, constructParameters(context, destination));
    this.messageId = messageId;
  }

  @Override
  public void onAdded() {

  }

  @Override
  public void onPushSend(MasterSecret masterSecret)
      throws RetryLaterException, MmsException, NoSuchMessageException,
          UndeliverableMessageException
  {
    ExpiringMessageManager expirationManager = ApplicationContext.getInstance(context).getExpiringMessageManager();
    MmsDatabase database          = DatabaseFactory.getMmsDatabase(context);
    OutgoingMediaMessage message           = database.getOutgoingMessage(masterSecret, messageId);

    try {
      deliver(masterSecret, message);
      database.markAsSent(messageId, true);
      markAttachmentsUploaded(messageId, message.getAttachments());

      if (message.getExpiresIn() > 0 && !message.isExpirationUpdate()) {
        database.markExpireStarted(messageId);
        expirationManager.scheduleDeletion(messageId, true, message.getExpiresIn());
      }

    } catch (InsecureFallbackApprovalException ifae) {
      Log.w(TAG, ifae);
      database.markAsPendingInsecureSmsFallback(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
      ApplicationContext.getInstance(context).getJobManager().add(new DirectoryRefreshJob(context));
    } catch (UntrustedIdentityException uie) {
      Log.w(TAG, uie);
      database.addMismatchedIdentity(messageId, Address.fromSerialized(uie.getE164Number()), uie.getIdentityKey());
      database.markAsSentFailed(messageId);
    }
  }

  @Override
  public boolean onShouldRetryThrowable(Exception exception) {
    if (exception instanceof RequirementNotMetException) return true;
    if (exception instanceof RetryLaterException)        return true;

    return false;
  }

  @Override
  public void onCanceled() {
    DatabaseFactory.getMmsDatabase(context).markAsSentFailed(messageId);
    notifyMediaMessageDeliveryFailed(context, messageId);
  }

  private void deliver(MasterSecret masterSecret, OutgoingMediaMessage message)
      throws RetryLaterException, InsecureFallbackApprovalException, UntrustedIdentityException,
             UndeliverableMessageException
  {
    if (message.getRecipients() == null || message.getRecipients().getPrimaryRecipient() == null) {
      throw new UndeliverableMessageException("No destination address.");
    }

    SignalServiceMessageSender messageSender = messageSenderFactory.create();

    try {
      SignalServiceAddress          address           = getPushAddress(message.getRecipients().getPrimaryRecipient().getAddress());
      MediaConstraints              mediaConstraints  = MediaConstraints.getPushMediaConstraints();
      List<Attachment>              scaledAttachments = scaleAttachments(masterSecret, mediaConstraints, message.getAttachments());
      List<SignalServiceAttachment> attachmentStreams = getAttachmentsFor(masterSecret, scaledAttachments);
      SignalServiceDataMessage      mediaMessage      = SignalServiceDataMessage.newBuilder()
                                                                                .withBody(message.getBody())
                                                                                .withAttachments(attachmentStreams)
                                                                                .withTimestamp(message.getSentTimeMillis())
                                                                                .withExpiration((int)(message.getExpiresIn() / 1000))
                                                                                .asExpirationUpdate(message.isExpirationUpdate())
                                                                                .build();

      messageSender.sendMessage(address, mediaMessage);
    } catch (UnregisteredUserException e) {
      Log.w(TAG, e);
      throw new InsecureFallbackApprovalException(e);
    } catch (FileNotFoundException e) {
      Log.w(TAG, e);
      throw new UndeliverableMessageException(e);
    } catch (IOException e) {
      Log.w(TAG, e);
      throw new RetryLaterException(e);
    }
  }
}
