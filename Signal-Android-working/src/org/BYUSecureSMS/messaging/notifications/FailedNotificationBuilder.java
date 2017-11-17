package org.BYUSecureSMS.messaging.notifications;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;

import org.BYUSecureSMS.messaging.database.RecipientPreferenceDatabase;
import org.BYUSecureSMS.messaging.R;
import org.BYUSecureSMS.messaging.preferences.NotificationPrivacyPreference;

public class FailedNotificationBuilder extends AbstractNotificationBuilder {

  public FailedNotificationBuilder(Context context, NotificationPrivacyPreference privacy, Intent intent) {
    super(context, privacy);

    setSmallIcon(R.drawable.icon_notification);
    setLargeIcon(BitmapFactory.decodeResource(context.getResources(),
                                              R.drawable.ic_action_warning_red));
    setContentTitle(context.getString(R.string.MessageNotifier_message_delivery_failed));
    setContentText(context.getString(R.string.MessageNotifier_failed_to_deliver_message));
    setTicker(context.getString(R.string.MessageNotifier_error_delivering_message));
    setContentIntent(PendingIntent.getActivity(context, 0, intent, 0));
    setAutoCancel(true);
    setAlarms(null, RecipientPreferenceDatabase.VibrateState.DEFAULT);
  }



}
