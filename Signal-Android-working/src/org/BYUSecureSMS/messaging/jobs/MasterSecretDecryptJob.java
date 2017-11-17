package org.BYUSecureSMS.messaging.jobs;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import org.BYUSecureSMS.messaging.crypto.MasterSecret;
import org.BYUSecureSMS.messaging.database.SmsDatabase;
import org.BYUSecureSMS.messaging.database.model.SmsMessageRecord;
import org.BYUSecureSMS.messaging.crypto.AsymmetricMasterCipher;
import org.BYUSecureSMS.messaging.crypto.AsymmetricMasterSecret;
import org.BYUSecureSMS.messaging.crypto.MasterSecretUnion;
import org.BYUSecureSMS.messaging.crypto.MasterSecretUtil;
import org.BYUSecureSMS.messaging.database.DatabaseFactory;
import org.BYUSecureSMS.messaging.database.EncryptingSmsDatabase;
import org.BYUSecureSMS.messaging.database.MmsDatabase;
import org.BYUSecureSMS.messaging.database.model.MessageRecord;
import org.BYUSecureSMS.messaging.jobs.requirements.MasterSecretRequirement;
import org.BYUSecureSMS.messaging.notifications.MessageNotifier;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.libsignal.InvalidMessageException;

import java.io.IOException;

public class MasterSecretDecryptJob extends MasterSecretJob {

  private static final long   serialVersionUID = 1L;
  private static final String TAG              = MasterSecretDecryptJob.class.getSimpleName();

  public MasterSecretDecryptJob(Context context) {
    super(context, JobParameters.newBuilder()
                                .withRequirement(new MasterSecretRequirement(context))
                                .create());
  }

  @Override
  public void onRun(MasterSecret masterSecret) {
    EncryptingSmsDatabase smsDatabase = DatabaseFactory.getEncryptingSmsDatabase(context);
    SmsDatabase.Reader smsReader   = smsDatabase.getDecryptInProgressMessages(masterSecret);

    SmsMessageRecord smsRecord;

    while ((smsRecord = smsReader.getNext()) != null) {
      try {
        String body = getAsymmetricDecryptedBody(masterSecret, smsRecord.getBody().getBody());
        smsDatabase.updateMessageBody(new MasterSecretUnion(masterSecret), smsRecord.getId(), body);
      } catch (InvalidMessageException e) {
        Log.w(TAG, e);
      }
    }

    MmsDatabase        mmsDatabase = DatabaseFactory.getMmsDatabase(context);
    MmsDatabase.Reader mmsReader   = mmsDatabase.getDecryptInProgressMessages(masterSecret);

    MessageRecord mmsRecord;

    while ((mmsRecord = mmsReader.getNext()) != null) {
      try {
        String body = getAsymmetricDecryptedBody(masterSecret, mmsRecord.getBody().getBody());
        mmsDatabase.updateMessageBody(new MasterSecretUnion(masterSecret), mmsRecord.getId(), body);
      } catch (InvalidMessageException e) {
        Log.w(TAG, e);
      }
    }

    smsReader.close();
    mmsReader.close();

    MessageNotifier.updateNotification(context, masterSecret);
  }

  @Override
  public boolean onShouldRetryThrowable(Exception exception) {
    return false;
  }

  @Override
  public void onAdded() {

  }

  @Override
  public void onCanceled() {

  }

  private String getAsymmetricDecryptedBody(MasterSecret masterSecret, String body)
      throws InvalidMessageException
  {
    try {
      AsymmetricMasterSecret asymmetricMasterSecret = MasterSecretUtil.getAsymmetricMasterSecret(context, masterSecret);
      AsymmetricMasterCipher asymmetricMasterCipher = new AsymmetricMasterCipher(asymmetricMasterSecret);

      if (TextUtils.isEmpty(body)) return "";
      else                         return asymmetricMasterCipher.decryptBody(body);

    } catch (IOException e) {
      throw new InvalidMessageException(e);
    }
  }


}
