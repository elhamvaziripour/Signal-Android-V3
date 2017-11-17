package org.BYUSecureSMS.messaging.util;

import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.annotation.UiThread;
import android.util.Log;

import org.BYUSecureSMS.messaging.crypto.storage.TextSecureIdentityKeyStore;
import org.BYUSecureSMS.messaging.database.IdentityDatabase;
import org.BYUSecureSMS.messaging.database.SmsDatabase;
import org.BYUSecureSMS.messaging.sms.OutgoingIdentityVerifiedMessage;
import org.BYUSecureSMS.messaging.sms.OutgoingTextMessage;
import org.BYUSecureSMS.messaging.util.concurrent.ListenableFuture;
import org.BYUSecureSMS.messaging.R;
import org.BYUSecureSMS.messaging.crypto.MasterSecretUnion;
import org.BYUSecureSMS.messaging.crypto.storage.TextSecureSessionStore;
import org.BYUSecureSMS.messaging.database.Address;
import org.BYUSecureSMS.messaging.database.DatabaseFactory;
import org.BYUSecureSMS.messaging.database.GroupDatabase;
import org.BYUSecureSMS.messaging.database.IdentityDatabase.IdentityRecord;
import org.BYUSecureSMS.messaging.database.MessagingDatabase.InsertResult;
import org.BYUSecureSMS.messaging.notifications.MessageNotifier;
import org.BYUSecureSMS.messaging.recipients.Recipient;
import org.BYUSecureSMS.messaging.recipients.RecipientFactory;
import org.BYUSecureSMS.messaging.recipients.Recipients;
import org.BYUSecureSMS.messaging.sms.IncomingIdentityDefaultMessage;
import org.BYUSecureSMS.messaging.sms.IncomingIdentityUpdateMessage;
import org.BYUSecureSMS.messaging.sms.IncomingIdentityVerifiedMessage;
import org.BYUSecureSMS.messaging.sms.IncomingTextMessage;
import org.BYUSecureSMS.messaging.sms.OutgoingIdentityDefaultMessage;
import org.BYUSecureSMS.messaging.util.concurrent.SettableFuture;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.IdentityKeyStore;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SessionStore;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.multidevice.VerifiedMessage;

import java.util.List;

import static org.whispersystems.libsignal.SessionCipher.SESSION_LOCK;

public class IdentityUtil {

  private static final String TAG = IdentityUtil.class.getSimpleName();

  @UiThread
  public static ListenableFuture<Optional<IdentityRecord>> getRemoteIdentityKey(final Context context, final Recipient recipient) {
    final SettableFuture<Optional<IdentityDatabase.IdentityRecord>> future = new SettableFuture<>();

    new AsyncTask<Recipient, Void, Optional<IdentityDatabase.IdentityRecord>>() {
      @Override
      protected Optional<IdentityDatabase.IdentityRecord> doInBackground(Recipient... recipient) {
        return DatabaseFactory.getIdentityDatabase(context)
                              .getIdentity(recipient[0].getAddress());
      }

      @Override
      protected void onPostExecute(Optional<IdentityDatabase.IdentityRecord> result) {
        future.set(result);
      }
    }.execute(recipient);

    return future;
  }

  public static void markIdentityVerified(Context context, MasterSecretUnion masterSecret,
                                          Recipient recipient, boolean verified, boolean remote)
  {
    long                 time          = System.currentTimeMillis();
    SmsDatabase smsDatabase   = DatabaseFactory.getSmsDatabase(context);
    GroupDatabase        groupDatabase = DatabaseFactory.getGroupDatabase(context);
    Recipients           recipients    = RecipientFactory.getRecipientsFor(context, recipient, true);
    GroupDatabase.Reader reader        = groupDatabase.getGroups();

    GroupDatabase.GroupRecord groupRecord;

    while ((groupRecord = reader.getNext()) != null) {
      if (groupRecord.getMembers().contains(recipient.getAddress()) && groupRecord.isActive()) {
        SignalServiceGroup group = new SignalServiceGroup(groupRecord.getId());

        if (remote) {
          IncomingTextMessage incoming = new IncomingTextMessage(recipient.getAddress(), 1, time, null, Optional.of(group), 0);

          if (verified) incoming = new IncomingIdentityVerifiedMessage(incoming);
          else          incoming = new IncomingIdentityDefaultMessage(incoming);

          smsDatabase.insertMessageInbox(incoming);
        } else {
          Recipients          groupRecipients = RecipientFactory.getRecipientsFor(context, new Address[] {Address.fromSerialized(GroupUtil.getEncodedId(group.getGroupId()))}, true);
          long                threadId        = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(groupRecipients);
          OutgoingTextMessage outgoing ;

          if (verified) outgoing = new OutgoingIdentityVerifiedMessage(recipients);
          else          outgoing = new OutgoingIdentityDefaultMessage(recipients);

          DatabaseFactory.getEncryptingSmsDatabase(context).insertMessageOutbox(masterSecret, threadId, outgoing, false, time, null);
        }
      }
    }

    if (remote) {
      IncomingTextMessage incoming = new IncomingTextMessage(recipient.getAddress(), 1, time, null, Optional.<SignalServiceGroup>absent(), 0);

      if (verified) incoming = new IncomingIdentityVerifiedMessage(incoming);
      else          incoming = new IncomingIdentityDefaultMessage(incoming);

      smsDatabase.insertMessageInbox(incoming);
    } else {
      OutgoingTextMessage outgoing;

      if (verified) outgoing = new OutgoingIdentityVerifiedMessage(recipients);
      else          outgoing = new OutgoingIdentityDefaultMessage(recipients);

      long threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipients);

      Log.w(TAG, "Inserting verified outbox...");
      DatabaseFactory.getEncryptingSmsDatabase(context)
                     .insertMessageOutbox(masterSecret, threadId, outgoing, false, time, null);
    }
  }

  public static void markIdentityUpdate(Context context, Recipient recipient) {
    long                 time          = System.currentTimeMillis();
    SmsDatabase          smsDatabase   = DatabaseFactory.getSmsDatabase(context);
    GroupDatabase        groupDatabase = DatabaseFactory.getGroupDatabase(context);
    GroupDatabase.Reader reader        = groupDatabase.getGroups();

    GroupDatabase.GroupRecord groupRecord;

    while ((groupRecord = reader.getNext()) != null) {
      if (groupRecord.getMembers().contains(recipient.getAddress()) && groupRecord.isActive()) {
        SignalServiceGroup            group       = new SignalServiceGroup(groupRecord.getId());
        IncomingTextMessage           incoming    = new IncomingTextMessage(recipient.getAddress(), 1, time, null, Optional.of(group), 0);
        IncomingIdentityUpdateMessage groupUpdate = new IncomingIdentityUpdateMessage(incoming);

        smsDatabase.insertMessageInbox(groupUpdate);
      }
    }

    IncomingTextMessage           incoming         = new IncomingTextMessage(recipient.getAddress(), 1, time, null, Optional.<SignalServiceGroup>absent(), 0);
    IncomingIdentityUpdateMessage individualUpdate = new IncomingIdentityUpdateMessage(incoming);
    Optional<InsertResult>        insertResult     = smsDatabase.insertMessageInbox(individualUpdate);

    if (insertResult.isPresent()) {
      MessageNotifier.updateNotification(context, null, insertResult.get().getThreadId());
    }
  }

  public static void saveIdentity(Context context, String number, IdentityKey identityKey) {
    synchronized (SESSION_LOCK) {
      IdentityKeyStore      identityKeyStore = new TextSecureIdentityKeyStore(context);
      SessionStore          sessionStore     = new TextSecureSessionStore(context);
      SignalProtocolAddress address          = new SignalProtocolAddress(number, 1);

      if (identityKeyStore.saveIdentity(address, identityKey)) {
        if (sessionStore.containsSession(address)) {
          SessionRecord sessionRecord = sessionStore.loadSession(address);
          sessionRecord.archiveCurrentState();

          sessionStore.storeSession(address, sessionRecord);
        }
      }
    }
  }

  public static void processVerifiedMessage(Context context, MasterSecretUnion masterSecret, VerifiedMessage verifiedMessage) {
    synchronized (SESSION_LOCK) {
      IdentityDatabase         identityDatabase = DatabaseFactory.getIdentityDatabase(context);
      Recipient                recipient        = RecipientFactory.getRecipientFor(context, Address.fromExternal(context, verifiedMessage.getDestination()), true);
      Optional<IdentityDatabase.IdentityRecord> identityRecord   = identityDatabase.getIdentity(recipient.getAddress());

      if (!identityRecord.isPresent() && verifiedMessage.getVerified() == VerifiedMessage.VerifiedState.DEFAULT) {
        Log.w(TAG, "No existing record for default status");
        return;
      }

      if (verifiedMessage.getVerified() == VerifiedMessage.VerifiedState.DEFAULT              &&
          identityRecord.isPresent()                                                          &&
          identityRecord.get().getIdentityKey().equals(verifiedMessage.getIdentityKey())      &&
          identityRecord.get().getVerifiedStatus() != IdentityDatabase.VerifiedStatus.DEFAULT)
      {
        identityDatabase.setVerified(recipient.getAddress(), identityRecord.get().getIdentityKey(), IdentityDatabase.VerifiedStatus.DEFAULT);
        markIdentityVerified(context, masterSecret, recipient, false, true);
      }

      if (verifiedMessage.getVerified() == VerifiedMessage.VerifiedState.VERIFIED &&
          (!identityRecord.isPresent() ||
              (identityRecord.isPresent() && !identityRecord.get().getIdentityKey().equals(verifiedMessage.getIdentityKey())) ||
              (identityRecord.isPresent() && identityRecord.get().getVerifiedStatus() != IdentityDatabase.VerifiedStatus.VERIFIED)))
      {
        saveIdentity(context, verifiedMessage.getDestination(), verifiedMessage.getIdentityKey());
        identityDatabase.setVerified(recipient.getAddress(), verifiedMessage.getIdentityKey(), IdentityDatabase.VerifiedStatus.VERIFIED);
        markIdentityVerified(context, masterSecret, recipient, true, true);
      }
    }
  }


  public static @Nullable String getUnverifiedBannerDescription(@NonNull Context context,
                                                                @NonNull List<Recipient> unverified)
  {
    return getPluralizedIdentityDescription(context, unverified,
                                            R.string.IdentityUtil_unverified_banner_one,
                                            R.string.IdentityUtil_unverified_banner_two,
                                            R.string.IdentityUtil_unverified_banner_many);
  }

  public static @Nullable String getUnverifiedSendDialogDescription(@NonNull Context context,
                                                                    @NonNull List<Recipient> unverified)
  {
    return getPluralizedIdentityDescription(context, unverified,
                                            R.string.IdentityUtil_unverified_dialog_one,
                                            R.string.IdentityUtil_unverified_dialog_two,
                                            R.string.IdentityUtil_unverified_dialog_many);
  }

  public static @Nullable String getUntrustedSendDialogDescription(@NonNull Context context,
                                                                   @NonNull List<Recipient> untrusted)
  {
    return getPluralizedIdentityDescription(context, untrusted,
                                            R.string.IdentityUtil_untrusted_dialog_one,
                                            R.string.IdentityUtil_untrusted_dialog_two,
                                            R.string.IdentityUtil_untrusted_dialog_many);
  }

  private static @Nullable String getPluralizedIdentityDescription(@NonNull Context context,
                                                                   @NonNull List<Recipient> recipients,
                                                                   @StringRes int resourceOne,
                                                                   @StringRes int resourceTwo,
                                                                   @StringRes int resourceMany)
  {
    if (recipients.isEmpty()) return null;

    if (recipients.size() == 1) {
      String name = recipients.get(0).toShortString();
      return context.getString(resourceOne, name);
    } else {
      String firstName  = recipients.get(0).toShortString();
      String secondName = recipients.get(1).toShortString();

      if (recipients.size() == 2) {
        return context.getString(resourceTwo, firstName, secondName);
      } else {
        String nMore;

        if (recipients.size() == 3) {
          nMore = context.getResources().getQuantityString(R.plurals.identity_others, 1);
        } else {
          nMore = context.getResources().getQuantityString(R.plurals.identity_others, recipients.size() - 2);
        }

        return context.getString(resourceMany, firstName, secondName, nMore);
      }
    }
  }
}
