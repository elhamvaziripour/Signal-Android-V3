package org.BYUSecureSMS.messaging;

import android.support.annotation.NonNull;

import org.BYUSecureSMS.messaging.crypto.MasterSecret;
import org.BYUSecureSMS.messaging.database.model.MessageRecord;
import org.BYUSecureSMS.messaging.recipients.Recipients;

import java.util.Locale;
import java.util.Set;

public interface BindableConversationItem extends Unbindable {
  void bind(@NonNull MasterSecret masterSecret,
            @NonNull MessageRecord messageRecord,
            @NonNull Locale locale,
            @NonNull Set<MessageRecord> batchSelected,
            @NonNull Recipients recipients);

  MessageRecord getMessageRecord();
}
