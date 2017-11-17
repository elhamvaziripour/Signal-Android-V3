package org.BYUSecureSMS.messaging.jobs;

import android.content.Context;
import android.util.Log;

import org.BYUSecureSMS.messaging.database.TextSecureDirectory;
import org.BYUSecureSMS.messaging.events.PartProgressEvent;
import org.greenrobot.eventbus.EventBus;
import org.BYUSecureSMS.messaging.ApplicationContext;
import org.BYUSecureSMS.messaging.TextSecureExpiredException;
import org.BYUSecureSMS.messaging.attachments.Attachment;
import org.BYUSecureSMS.messaging.crypto.MasterSecret;
import org.BYUSecureSMS.messaging.database.Address;
import org.BYUSecureSMS.messaging.database.DatabaseFactory;
import org.BYUSecureSMS.messaging.jobs.requirements.MasterSecretRequirement;
import org.BYUSecureSMS.messaging.mms.PartAuthority;
import org.BYUSecureSMS.messaging.notifications.MessageNotifier;
import org.BYUSecureSMS.messaging.recipients.Recipients;
import org.BYUSecureSMS.messaging.util.TextSecurePreferences;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment.ProgressListener;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

public abstract class PushSendJob extends SendJob {

  private static final String TAG = PushSendJob.class.getSimpleName();

  protected PushSendJob(Context context, JobParameters parameters) {
    super(context, parameters);
  }

  protected static JobParameters constructParameters(Context context, Address destination) {
    JobParameters.Builder builder = JobParameters.newBuilder();
    builder.withPersistence();
    builder.withGroupId(destination.serialize());
    builder.withRequirement(new MasterSecretRequirement(context));
    builder.withRequirement(new NetworkRequirement(context));
    builder.withRetryCount(5);

    return builder.create();
  }

  @Override
  protected final void onSend(MasterSecret masterSecret) throws Exception {
    if (TextSecurePreferences.getSignedPreKeyFailureCount(context) > 5) {
      ApplicationContext.getInstance(context)
                        .getJobManager()
                        .add(new RotateSignedPreKeyJob(context));

      throw new TextSecureExpiredException("Too many signed prekey rotation failures");
    }

    onPushSend(masterSecret);
  }

  protected SignalServiceAddress getPushAddress(Address address) {
    String relay = TextSecureDirectory.getInstance(context).getRelay(address.toPhoneString());
    return new SignalServiceAddress(address.toPhoneString(), Optional.fromNullable(relay));
  }

  protected List<SignalServiceAttachment> getAttachmentsFor(MasterSecret masterSecret, List<Attachment> parts) {
    List<SignalServiceAttachment> attachments = new LinkedList<>();

    for (final Attachment attachment : parts) {
      try {
        if (attachment.getDataUri() == null || attachment.getSize() == 0) throw new IOException("Assertion failed, outgoing attachment has no data!");
        InputStream is = PartAuthority.getAttachmentStream(context, masterSecret, attachment.getDataUri());
        attachments.add(SignalServiceAttachment.newStreamBuilder()
                                               .withStream(is)
                                               .withContentType(attachment.getContentType())
                                               .withLength(attachment.getSize())
                                               .withFileName(attachment.getFileName())
                                               .withVoiceNote(attachment.isVoiceNote())
                                               .withListener(new ProgressListener() {
                                                 @Override
                                                 public void onAttachmentProgress(long total, long progress) {
                                                   EventBus.getDefault().postSticky(new PartProgressEvent(attachment, total, progress));
                                                 }
                                               })
                                               .build());
      } catch (IOException ioe) {
        Log.w(TAG, "Couldn't open attachment", ioe);
      }
    }

    return attachments;
  }

  protected void notifyMediaMessageDeliveryFailed(Context context, long messageId) {
    long       threadId   = DatabaseFactory.getMmsDatabase(context).getThreadIdForMessage(messageId);
    Recipients recipients = DatabaseFactory.getThreadDatabase(context).getRecipientsForThreadId(threadId);

    if (threadId != -1 && recipients != null) {
      MessageNotifier.notifyMessageDeliveryFailed(context, recipients, threadId);
    }
  }

  protected abstract void onPushSend(MasterSecret masterSecret) throws Exception;
}