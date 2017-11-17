/**
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.BYUSecureSMS.messaging.sms;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import org.BYUSecureSMS.messaging.database.SmsDatabase;
import org.BYUSecureSMS.messaging.database.TextSecureDirectory;
import org.BYUSecureSMS.messaging.service.ExpiringMessageManager;
import org.BYUSecureSMS.messaging.ApplicationContext;
import org.BYUSecureSMS.messaging.crypto.MasterSecret;
import org.BYUSecureSMS.messaging.crypto.MasterSecretUnion;
import org.BYUSecureSMS.messaging.database.Address;
import org.BYUSecureSMS.messaging.database.DatabaseFactory;
import org.BYUSecureSMS.messaging.database.EncryptingSmsDatabase;
import org.BYUSecureSMS.messaging.database.MmsDatabase;
import org.BYUSecureSMS.messaging.database.NotInDirectoryException;
import org.BYUSecureSMS.messaging.database.ThreadDatabase;
import org.BYUSecureSMS.messaging.database.model.MessageRecord;
import org.BYUSecureSMS.messaging.jobs.MmsSendJob;
import org.BYUSecureSMS.messaging.jobs.PushGroupSendJob;
import org.BYUSecureSMS.messaging.jobs.PushMediaSendJob;
import org.BYUSecureSMS.messaging.jobs.PushTextSendJob;
import org.BYUSecureSMS.messaging.jobs.SmsSendJob;
import org.BYUSecureSMS.messaging.mms.MmsException;
import org.BYUSecureSMS.messaging.mms.OutgoingMediaMessage;
import org.BYUSecureSMS.messaging.push.AccountManagerFactory;
import org.BYUSecureSMS.messaging.recipients.Recipients;
import org.BYUSecureSMS.messaging.util.TextSecurePreferences;
import org.BYUSecureSMS.messaging.util.Util;
import org.whispersystems.jobqueue.JobManager;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.ContactTokenDetails;

import java.io.IOException;

public class MessageSender {

  private static final String TAG = MessageSender.class.getSimpleName();

  public static long send(final Context context,
                          final MasterSecret masterSecret,
                          final OutgoingTextMessage message,
                          final long threadId,
                          final boolean forceSms,
                          final SmsDatabase.InsertListener insertListener)
  {
    EncryptingSmsDatabase database    = DatabaseFactory.getEncryptingSmsDatabase(context);
    Recipients            recipients  = message.getRecipients();
    boolean               keyExchange = message.isKeyExchange();

    long allocatedThreadId;

    if (threadId == -1) {
      allocatedThreadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipients);
    } else {
      allocatedThreadId = threadId;
    }

    long messageId = database.insertMessageOutbox(new MasterSecretUnion(masterSecret), allocatedThreadId,
                                                  message, forceSms, System.currentTimeMillis(), insertListener);

    sendTextMessage(context, recipients, forceSms, keyExchange, messageId, message.getExpiresIn());

    return allocatedThreadId;
  }

  public static long send(final Context context,
                          final MasterSecret masterSecret,
                          final OutgoingMediaMessage message,
                          final long threadId,
                          final boolean forceSms,
                          final SmsDatabase.InsertListener insertListener)
  {
    try {
      ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(context);
      MmsDatabase    database       = DatabaseFactory.getMmsDatabase(context);

      long allocatedThreadId;

      if (threadId == -1) {
        allocatedThreadId = threadDatabase.getThreadIdFor(message.getRecipients(), message.getDistributionType());
      } else {
        allocatedThreadId = threadId;
      }

      Recipients recipients = message.getRecipients();
      long       messageId  = database.insertMessageOutbox(new MasterSecretUnion(masterSecret), message, allocatedThreadId, forceSms, insertListener);

      sendMediaMessage(context, masterSecret, recipients, forceSms, messageId, message.getExpiresIn());

      return allocatedThreadId;
    } catch (MmsException e) {
      Log.w(TAG, e);
      return threadId;
    }
  }

  public static void resendGroupMessage(Context context, MasterSecret masterSecret, MessageRecord messageRecord, Address filterAddress) {
    if (!messageRecord.isMms()) throw new AssertionError("Not Group");

    Recipients recipients = DatabaseFactory.getMmsAddressDatabase(context).getRecipientsForId(messageRecord.getId());
    sendGroupPush(context, recipients, messageRecord.getId(), filterAddress);
  }

  public static void resend(Context context, MasterSecret masterSecret, MessageRecord messageRecord) {
    try {
      long       messageId   = messageRecord.getId();
      boolean    forceSms    = messageRecord.isForcedSms();
      boolean    keyExchange = messageRecord.isKeyExchange();
      long       expiresIn   = messageRecord.getExpiresIn();

      if (messageRecord.isMms()) {
        Recipients recipients = DatabaseFactory.getMmsAddressDatabase(context).getRecipientsForId(messageId);
        sendMediaMessage(context, masterSecret, recipients, forceSms, messageId, expiresIn);
      } else {
        Recipients recipients  = messageRecord.getRecipients();
        sendTextMessage(context, recipients, forceSms, keyExchange, messageId, expiresIn);
      }
    } catch (MmsException e) {
      Log.w(TAG, e);
    }
  }

  private static void sendMediaMessage(Context context, MasterSecret masterSecret,
                                       Recipients recipients, boolean forceSms,
                                       long messageId, long expiresIn)
      throws MmsException
  {
    if (!forceSms && isSelfSend(context, recipients)) {
      sendMediaSelf(context, masterSecret, messageId, expiresIn);
    } else if (isGroupPushSend(recipients)) {
      sendGroupPush(context, recipients, messageId, null);
    } else if (!forceSms && isPushMediaSend(context, recipients)) {
      sendMediaPush(context, recipients, messageId);
    } else {
      sendMms(context, messageId);
    }
  }

  private static void sendTextMessage(Context context, Recipients recipients,
                                      boolean forceSms, boolean keyExchange,
                                      long messageId, long expiresIn)
  {
    if (!forceSms && isSelfSend(context, recipients)) {
      sendTextSelf(context, messageId, expiresIn);
    } else if (!forceSms && isPushTextSend(context, recipients, keyExchange)) {
      sendTextPush(context, recipients, messageId);
    } else {
      sendSms(context, recipients, messageId);
    }
  }

  private static void sendTextSelf(Context context, long messageId, long expiresIn) {
    EncryptingSmsDatabase database = DatabaseFactory.getEncryptingSmsDatabase(context);

    database.markAsSent(messageId, true);

    Pair<Long, Long> messageAndThreadId = database.copyMessageInbox(messageId);
    database.markAsPush(messageAndThreadId.first);

    if (expiresIn > 0) {
      ExpiringMessageManager expiringMessageManager = ApplicationContext.getInstance(context).getExpiringMessageManager();

      database.markExpireStarted(messageId);
      expiringMessageManager.scheduleDeletion(messageId, false, expiresIn);
    }
  }

  private static void sendMediaSelf(Context context, MasterSecret masterSecret,
                                    long messageId, long expiresIn)
      throws MmsException
  {
    ExpiringMessageManager expiringMessageManager = ApplicationContext.getInstance(context).getExpiringMessageManager();
    MmsDatabase            database               = DatabaseFactory.getMmsDatabase(context);

    database.markAsSent(messageId, true);
    database.copyMessageInbox(masterSecret, messageId);

    if (expiresIn > 0) {
      database.markExpireStarted(messageId);
      expiringMessageManager.scheduleDeletion(messageId, true, expiresIn);
    }
  }

  private static void sendTextPush(Context context, Recipients recipients, long messageId) {
    JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();
    jobManager.add(new PushTextSendJob(context, messageId, recipients.getPrimaryRecipient().getAddress()));
  }

  private static void sendMediaPush(Context context, Recipients recipients, long messageId) {
    JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();
    jobManager.add(new PushMediaSendJob(context, messageId, recipients.getPrimaryRecipient().getAddress()));
  }

  private static void sendGroupPush(Context context, Recipients recipients, long messageId, Address filterAddress) {
    JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();
    jobManager.add(new PushGroupSendJob(context, messageId, recipients.getPrimaryRecipient().getAddress(), filterAddress));
  }

  private static void sendSms(Context context, Recipients recipients, long messageId) {
    JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();
    jobManager.add(new SmsSendJob(context, messageId, recipients.getPrimaryRecipient().getName()));
  }

  private static void sendMms(Context context, long messageId) {
    JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();
    jobManager.add(new MmsSendJob(context, messageId));
  }

  private static boolean isPushTextSend(Context context, Recipients recipients, boolean keyExchange) {
    if (!TextSecurePreferences.isPushRegistered(context)) {
      return false;
    }

    if (keyExchange) {
      return false;
    }

    return isPushDestination(context, recipients.getPrimaryRecipient().getAddress());
  }

  private static boolean isPushMediaSend(Context context, Recipients recipients) {
    if (!TextSecurePreferences.isPushRegistered(context)) {
      return false;
    }

    if (recipients.getRecipientsList().size() > 1) {
      return false;
    }

    return isPushDestination(context, recipients.getPrimaryRecipient().getAddress());
  }

  private static boolean isGroupPushSend(Recipients recipients) {
    return recipients.getPrimaryRecipient().getAddress().isGroup();
  }

  private static boolean isSelfSend(Context context, Recipients recipients) {
    if (!TextSecurePreferences.isPushRegistered(context)) {
      return false;
    }

    if (!recipients.isSingleRecipient()) {
      return false;
    }

    if (recipients.isGroupRecipient()) {
      return false;
    }

    return Util.isOwnNumber(context, recipients.getPrimaryRecipient().getAddress());
  }

  private static boolean isPushDestination(Context context, Address destination) {
    TextSecureDirectory directory = TextSecureDirectory.getInstance(context);

    try {
      return directory.isSecureTextSupported(destination);
    } catch (NotInDirectoryException e) {
      try {
        SignalServiceAccountManager   accountManager = AccountManagerFactory.createManager(context);
        Optional<ContactTokenDetails> registeredUser = accountManager.getContact(destination.serialize());

        if (!registeredUser.isPresent()) {
          registeredUser = Optional.of(new ContactTokenDetails());
          registeredUser.get().setNumber(destination.serialize());
          directory.setNumber(registeredUser.get(), false);
          return false;
        } else {
          registeredUser.get().setNumber(destination.toPhoneString());
          directory.setNumber(registeredUser.get(), true);
          return true;
        }
      } catch (IOException e1) {
        Log.w(TAG, e1);
        return false;
      }
    }
  }

}
