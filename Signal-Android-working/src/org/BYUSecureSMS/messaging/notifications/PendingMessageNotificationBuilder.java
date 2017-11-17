package org.BYUSecureSMS.messaging.notifications;


import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;

import org.BYUSecureSMS.messaging.ConversationListActivity;
import org.BYUSecureSMS.messaging.R;
import org.BYUSecureSMS.messaging.database.RecipientPreferenceDatabase;
import org.BYUSecureSMS.messaging.preferences.NotificationPrivacyPreference;
import org.BYUSecureSMS.messaging.util.TextSecurePreferences;

public class PendingMessageNotificationBuilder extends AbstractNotificationBuilder {

  public PendingMessageNotificationBuilder(Context context, NotificationPrivacyPreference privacy) {
    super(context, privacy);

    Intent intent = new Intent(context, ConversationListActivity.class);

    setSmallIcon(R.drawable.icon_notification);
    setColor(context.getResources().getColor(R.color.textsecure_primary));
    setPriority(TextSecurePreferences.getNotificationPriority(context));
    setCategory(NotificationCompat.CATEGORY_MESSAGE);

    setContentTitle(context.getString(R.string.MessageNotifier_pending_signal_messages));
    setContentText(context.getString(R.string.MessageNotifier_you_have_pending_signal_messages));
    setTicker(context.getString(R.string.MessageNotifier_you_have_pending_signal_messages));

    setContentIntent(PendingIntent.getActivity(context, 0, intent, 0));
    setAutoCancel(true);
    setAlarms(null, RecipientPreferenceDatabase.VibrateState.DEFAULT);
  }
}
