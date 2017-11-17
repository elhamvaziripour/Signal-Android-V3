package org.BYUSecureSMS.messaging.service;

import android.content.Intent;
import android.support.annotation.Nullable;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import org.BYUSecureSMS.messaging.crypto.MasterSecret;
import org.BYUSecureSMS.messaging.database.Address;
import org.BYUSecureSMS.messaging.database.DatabaseFactory;
import org.BYUSecureSMS.messaging.database.RecipientPreferenceDatabase;
import org.BYUSecureSMS.messaging.mms.OutgoingMediaMessage;
import org.BYUSecureSMS.messaging.mms.SlideDeck;
import org.BYUSecureSMS.messaging.sms.MessageSender;
import org.BYUSecureSMS.messaging.sms.OutgoingTextMessage;
import org.BYUSecureSMS.messaging.R;
import org.BYUSecureSMS.messaging.database.ThreadDatabase;
import org.BYUSecureSMS.messaging.recipients.RecipientFactory;
import org.BYUSecureSMS.messaging.recipients.Recipients;
import org.BYUSecureSMS.messaging.util.Rfc5724Uri;
import org.whispersystems.libsignal.util.guava.Optional;

import java.net.URISyntaxException;
import java.net.URLDecoder;

public class QuickResponseService extends MasterSecretIntentService {

  private static final String TAG = QuickResponseService.class.getSimpleName();

  public QuickResponseService() {
    super("QuickResponseService");
  }

  @Override
  protected void onHandleIntent(Intent intent, @Nullable MasterSecret masterSecret) {
    if (!TelephonyManager.ACTION_RESPOND_VIA_MESSAGE.equals(intent.getAction())) {
      Log.w(TAG, "Received unknown intent: " + intent.getAction());
      return;
    }

    if (masterSecret == null) {
      Log.w(TAG, "Got quick response request when locked...");
      Toast.makeText(this, R.string.QuickResponseService_quick_response_unavailable_when_Signal_is_locked, Toast.LENGTH_LONG).show();
      return;
    }

    try {
      Rfc5724Uri uri        = new Rfc5724Uri(intent.getDataString());
      String     content    = intent.getStringExtra(Intent.EXTRA_TEXT);
      String     numbers    = uri.getPath();

      if (numbers.contains("%")){
        numbers = URLDecoder.decode(numbers);
      }

      String[]  numbersArray = numbers.split(",");
      Address[] addresses    = new Address[numbersArray.length];

      for (int i=0;i<numbersArray.length;i++) {
        addresses[i] = Address.fromExternal(this, numbersArray[i]);
      }

      Recipients                      recipients     = RecipientFactory.getRecipientsFor(this, addresses, false);
      Optional<RecipientPreferenceDatabase.RecipientsPreferences> preferences    = DatabaseFactory.getRecipientPreferenceDatabase(this).getRecipientsPreferences(recipients.getAddresses());
      int                             subscriptionId = preferences.isPresent() ? preferences.get().getDefaultSubscriptionId().or(-1) : -1;
      long                            expiresIn      = preferences.isPresent() ? preferences.get().getExpireMessages() * 1000 : 0;

      if (!TextUtils.isEmpty(content)) {
        if (recipients.isSingleRecipient()) {
          MessageSender.send(this, masterSecret, new OutgoingTextMessage(recipients, content, expiresIn, subscriptionId), -1, false, null);
        } else {
          MessageSender.send(this, masterSecret, new OutgoingMediaMessage(recipients, new SlideDeck(), content, System.currentTimeMillis(),
                                                                          subscriptionId, expiresIn, ThreadDatabase.DistributionTypes.DEFAULT), -1, false, null);
        }
      }
    } catch (URISyntaxException e) {
      Toast.makeText(this, R.string.QuickResponseService_problem_sending_message, Toast.LENGTH_LONG).show();
      Log.w(TAG, e);
    }
  }
}
