package org.BYUSecureSMS.messaging;

import android.support.annotation.NonNull;

import org.BYUSecureSMS.messaging.crypto.MasterSecret;
import org.BYUSecureSMS.messaging.database.model.ThreadRecord;

import java.util.Locale;
import java.util.Set;

public interface BindableConversationListItem extends Unbindable {

  public void bind(@NonNull MasterSecret masterSecret, @NonNull ThreadRecord thread,
                   @NonNull Locale locale, @NonNull Set<Long> selectedThreads, boolean batchMode);
}
