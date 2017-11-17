package org.BYUSecureSMS.messaging.dependencies;

import android.content.Context;

import org.BYUSecureSMS.messaging.DeviceListFragment;
import org.BYUSecureSMS.messaging.crypto.storage.SignalProtocolStoreImpl;
import org.BYUSecureSMS.messaging.jobs.AttachmentDownloadJob;
import org.BYUSecureSMS.messaging.jobs.AvatarDownloadJob;
import org.BYUSecureSMS.messaging.jobs.CleanPreKeysJob;
import org.BYUSecureSMS.messaging.jobs.CreateSignedPreKeyJob;
import org.BYUSecureSMS.messaging.jobs.DeliveryReceiptJob;
import org.BYUSecureSMS.messaging.jobs.MultiDeviceBlockedUpdateJob;
import org.BYUSecureSMS.messaging.jobs.MultiDeviceContactUpdateJob;
import org.BYUSecureSMS.messaging.jobs.MultiDeviceGroupUpdateJob;
import org.BYUSecureSMS.messaging.jobs.MultiDeviceReadUpdateJob;
import org.BYUSecureSMS.messaging.jobs.MultiDeviceVerifiedUpdateJob;
import org.BYUSecureSMS.messaging.jobs.PushGroupSendJob;
import org.BYUSecureSMS.messaging.jobs.PushGroupUpdateJob;
import org.BYUSecureSMS.messaging.jobs.PushMediaSendJob;
import org.BYUSecureSMS.messaging.jobs.PushNotificationReceiveJob;
import org.BYUSecureSMS.messaging.jobs.PushTextSendJob;
import org.BYUSecureSMS.messaging.jobs.RefreshAttributesJob;
import org.BYUSecureSMS.messaging.jobs.RefreshPreKeysJob;
import org.BYUSecureSMS.messaging.jobs.RequestGroupInfoJob;
import org.BYUSecureSMS.messaging.jobs.RetrieveProfileJob;
import org.BYUSecureSMS.messaging.jobs.RotateSignedPreKeyJob;
import org.BYUSecureSMS.messaging.push.SecurityEventListener;
import org.BYUSecureSMS.messaging.push.SignalServiceNetworkAccess;
import org.BYUSecureSMS.messaging.service.MessageRetrievalService;
import org.BYUSecureSMS.messaging.service.WebRtcCallService;
import org.BYUSecureSMS.messaging.util.TextSecurePreferences;
import org.BYUSecureSMS.messaging.BuildConfig;
import org.BYUSecureSMS.messaging.jobs.GcmRefreshJob;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.util.CredentialsProvider;

import dagger.Module;
import dagger.Provides;

@Module(complete = false, injects = {CleanPreKeysJob.class,
                                     CreateSignedPreKeyJob.class,
                                     DeliveryReceiptJob.class,
                                     PushGroupSendJob.class,
                                     PushTextSendJob.class,
                                     PushMediaSendJob.class,
                                     AttachmentDownloadJob.class,
                                     RefreshPreKeysJob.class,
                                     MessageRetrievalService.class,
                                     PushNotificationReceiveJob.class,
                                     MultiDeviceContactUpdateJob.class,
                                     MultiDeviceGroupUpdateJob.class,
                                     MultiDeviceReadUpdateJob.class,
                                     MultiDeviceBlockedUpdateJob.class,
                                     DeviceListFragment.class,
                                     RefreshAttributesJob.class,
                                     GcmRefreshJob.class,
                                     RequestGroupInfoJob.class,
                                     PushGroupUpdateJob.class,
                                     AvatarDownloadJob.class,
                                     RotateSignedPreKeyJob.class,
                                     WebRtcCallService.class,
                                     RetrieveProfileJob.class,
                                     MultiDeviceVerifiedUpdateJob.class})
public class SignalCommunicationModule {

  private final Context                    context;
  private final SignalServiceNetworkAccess networkAccess;

  public SignalCommunicationModule(Context context, SignalServiceNetworkAccess networkAccess) {
    this.context       = context;
    this.networkAccess = networkAccess;
  }

  @Provides SignalServiceAccountManager provideSignalAccountManager() {
    return new SignalServiceAccountManager(networkAccess.getConfiguration(context),
                                           TextSecurePreferences.getLocalNumber(context),
                                           TextSecurePreferences.getPushServerPassword(context),
                                           BuildConfig.USER_AGENT);
  }

  @Provides
  SignalMessageSenderFactory provideSignalMessageSenderFactory() {
    return new SignalMessageSenderFactory() {
      @Override
      public SignalServiceMessageSender create() {
        return new SignalServiceMessageSender(networkAccess.getConfiguration(context),
                                              TextSecurePreferences.getLocalNumber(context),
                                              TextSecurePreferences.getPushServerPassword(context),
                                              new SignalProtocolStoreImpl(context),
                                              BuildConfig.USER_AGENT,
                                              Optional.fromNullable(MessageRetrievalService.getPipe()),
                                              Optional.<SignalServiceMessageSender.EventListener>of(new SecurityEventListener(context)));
      }
    };
  }

  @Provides SignalServiceMessageReceiver provideSignalMessageReceiver() {
    return new SignalServiceMessageReceiver(networkAccess.getConfiguration(context),
                                            new DynamicCredentialsProvider(context),
                                            BuildConfig.USER_AGENT);
  }

  public static interface SignalMessageSenderFactory {
    public SignalServiceMessageSender create();
  }

  private static class DynamicCredentialsProvider implements CredentialsProvider {

    private final Context context;

    private DynamicCredentialsProvider(Context context) {
      this.context = context.getApplicationContext();
    }

    @Override
    public String getUser() {
      return TextSecurePreferences.getLocalNumber(context);
    }

    @Override
    public String getPassword() {
      return TextSecurePreferences.getPushServerPassword(context);
    }

    @Override
    public String getSignalingKey() {
      return TextSecurePreferences.getSignalingKey(context);
    }
  }

}
