package org.BYUSecureSMS.messaging.jobs;

import android.content.Context;

import org.BYUSecureSMS.messaging.crypto.MasterSecret;
import org.BYUSecureSMS.messaging.database.DatabaseFactory;
import org.BYUSecureSMS.messaging.database.RecipientPreferenceDatabase;
import org.BYUSecureSMS.messaging.database.RecipientPreferenceDatabase.BlockedReader;
import org.BYUSecureSMS.messaging.dependencies.InjectableType;
import org.BYUSecureSMS.messaging.dependencies.SignalCommunicationModule.SignalMessageSenderFactory;
import org.BYUSecureSMS.messaging.jobs.requirements.MasterSecretRequirement;
import org.BYUSecureSMS.messaging.recipients.Recipients;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.multidevice.BlockedListMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import javax.inject.Inject;

public class MultiDeviceBlockedUpdateJob extends MasterSecretJob implements InjectableType {

  private static final long serialVersionUID = 1L;

  private static final String TAG = MultiDeviceBlockedUpdateJob.class.getSimpleName();

  @Inject transient SignalMessageSenderFactory messageSenderFactory;

  public MultiDeviceBlockedUpdateJob(Context context) {
    super(context, JobParameters.newBuilder()
                                .withRequirement(new NetworkRequirement(context))
                                .withRequirement(new MasterSecretRequirement(context))
                                .withGroupId(MultiDeviceBlockedUpdateJob.class.getSimpleName())
                                .withPersistence()
                                .create());
  }

  @Override
  public void onRun(MasterSecret masterSecret)
      throws IOException, UntrustedIdentityException
  {
    RecipientPreferenceDatabase database      = DatabaseFactory.getRecipientPreferenceDatabase(context);
    SignalServiceMessageSender  messageSender = messageSenderFactory.create();
    BlockedReader               reader        = database.readerForBlocked(database.getBlocked());
    List<String>                blocked       = new LinkedList<>();

    Recipients recipients;

    while ((recipients = reader.getNext()) != null) {
      if (recipients.isSingleRecipient() && !recipients.isGroupRecipient()) {
        blocked.add(recipients.getPrimaryRecipient().getAddress().serialize());
      }
    }

    messageSender.sendMessage(SignalServiceSyncMessage.forBlocked(new BlockedListMessage(blocked)));
  }

  @Override
  public boolean onShouldRetryThrowable(Exception exception) {
    if (exception instanceof PushNetworkException) return true;
    return false;
  }

  @Override
  public void onAdded() {

  }

  @Override
  public void onCanceled() {

  }
}
