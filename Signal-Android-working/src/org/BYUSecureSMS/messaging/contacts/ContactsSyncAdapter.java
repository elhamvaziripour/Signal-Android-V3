package org.BYUSecureSMS.messaging.contacts;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;
import android.util.Log;

import org.BYUSecureSMS.messaging.util.DirectoryHelper;
import org.BYUSecureSMS.messaging.service.KeyCachingService;
import org.BYUSecureSMS.messaging.util.TextSecurePreferences;

import java.io.IOException;

public class ContactsSyncAdapter extends AbstractThreadedSyncAdapter {

  private static final String TAG = ContactsSyncAdapter.class.getSimpleName();

  public ContactsSyncAdapter(Context context, boolean autoInitialize) {
    super(context, autoInitialize);
  }

  @Override
  public void onPerformSync(Account account, Bundle extras, String authority,
                            ContentProviderClient provider, SyncResult syncResult)
  {
    Log.w(TAG, "onPerformSync(" + authority +")");

    if (TextSecurePreferences.isPushRegistered(getContext())) {
      try {
        DirectoryHelper.refreshDirectory(getContext(), KeyCachingService.getMasterSecret(getContext()));
      } catch (IOException e) {
        Log.w(TAG, e);
      }
    }
  }

}
