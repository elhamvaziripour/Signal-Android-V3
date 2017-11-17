package org.BYUSecureSMS.messaging.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.Nullable;

import org.BYUSecureSMS.messaging.crypto.MasterSecret;
import org.BYUSecureSMS.messaging.service.KeyCachingService;

public abstract class MasterSecretBroadcastReceiver extends BroadcastReceiver {

  @Override
  public final void onReceive(Context context, Intent intent) {
    onReceive(context, intent, KeyCachingService.getMasterSecret(context));
  }

  protected abstract void onReceive(Context context, Intent intent, @Nullable MasterSecret masterSecret);
}
