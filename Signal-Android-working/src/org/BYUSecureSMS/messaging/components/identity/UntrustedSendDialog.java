package org.BYUSecureSMS.messaging.components.identity;


import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;

import org.BYUSecureSMS.messaging.database.DatabaseFactory;
import org.BYUSecureSMS.messaging.database.IdentityDatabase;
import org.BYUSecureSMS.messaging.R;

import java.util.List;

import static org.whispersystems.libsignal.SessionCipher.SESSION_LOCK;

public class UntrustedSendDialog extends AlertDialog.Builder implements DialogInterface.OnClickListener {

  private final List<IdentityDatabase.IdentityRecord> untrustedRecords;
  private final ResendListener       resendListener;

  public UntrustedSendDialog(@NonNull Context context,
                             @NonNull String message,
                             @NonNull List<IdentityDatabase.IdentityRecord> untrustedRecords,
                             @NonNull ResendListener resendListener)
  {
    super(context);
    this.untrustedRecords = untrustedRecords;
    this.resendListener   = resendListener;

    setTitle(R.string.UntrustedSendDialog_send_message);
    setIconAttribute(R.attr.dialog_alert_icon);
    setMessage(message);
    setPositiveButton(R.string.UntrustedSendDialog_send, this);
    setNegativeButton(android.R.string.cancel, null);
  }

  @Override
  public void onClick(DialogInterface dialog, int which) {
    final IdentityDatabase identityDatabase = DatabaseFactory.getIdentityDatabase(getContext());

    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        synchronized (SESSION_LOCK) {
          for (IdentityDatabase.IdentityRecord identityRecord : untrustedRecords) {
            identityDatabase.setApproval(identityRecord.getAddress(), true);
          }
        }

        return null;
      }

      @Override
      protected void onPostExecute(Void result) {
        resendListener.onResendMessage();
      }
    }.execute();
  }

  public interface ResendListener {
    public void onResendMessage();
  }
}
