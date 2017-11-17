package org.BYUSecureSMS.messaging.database.loaders;

import android.content.Context;
import android.database.Cursor;

import org.BYUSecureSMS.messaging.database.DatabaseFactory;
import org.BYUSecureSMS.messaging.util.AbstractCursorLoader;

public class BlockedContactsLoader extends AbstractCursorLoader {

  public BlockedContactsLoader(Context context) {
    super(context);
  }

  @Override
  public Cursor getCursor() {
    return DatabaseFactory.getRecipientPreferenceDatabase(getContext())
                          .getBlocked();
  }

}
