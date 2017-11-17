package org.BYUSecureSMS.messaging.service;


import android.content.ComponentName;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.service.chooser.ChooserTarget;
import android.service.chooser.ChooserTargetService;
import android.support.annotation.RequiresApi;

import org.BYUSecureSMS.messaging.ShareActivity;
import org.BYUSecureSMS.messaging.crypto.MasterCipher;
import org.BYUSecureSMS.messaging.crypto.MasterSecret;
import org.BYUSecureSMS.messaging.database.DatabaseFactory;
import org.BYUSecureSMS.messaging.database.ThreadDatabase;
import org.BYUSecureSMS.messaging.database.model.ThreadRecord;
import org.BYUSecureSMS.messaging.recipients.RecipientFactory;
import org.BYUSecureSMS.messaging.recipients.Recipients;
import org.BYUSecureSMS.messaging.util.BitmapUtil;

import java.util.LinkedList;
import java.util.List;

@RequiresApi(api = Build.VERSION_CODES.M)
public class DirectShareService extends ChooserTargetService {
  @Override
  public List<ChooserTarget> onGetChooserTargets(ComponentName targetActivityName,
                                                 IntentFilter matchedFilter)
  {
    List<ChooserTarget> results        = new LinkedList<>();
    MasterSecret        masterSecret   = KeyCachingService.getMasterSecret(this);

    if (masterSecret == null) {
      return results;
    }

    ComponentName  componentName  = new ComponentName(this, ShareActivity.class);
    ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(this);
    Cursor         cursor         = threadDatabase.getDirectShareList();

    try {
      ThreadDatabase.Reader reader = threadDatabase.readerFor(cursor, new MasterCipher(masterSecret));
      ThreadRecord record;

      while ((record = reader.getNext()) != null && results.size() < 10) {
        Recipients recipients = RecipientFactory.getRecipientsFor(this, record.getRecipients().getAddresses(), false);
        String     name       = recipients.toShortString();
        Drawable   drawable   = recipients.getContactPhoto().asDrawable(this, recipients.getColor().toConversationColor(this));
        Bitmap     avatar     = BitmapUtil.createFromDrawable(drawable, 500, 500);

        Parcel parcel = Parcel.obtain();
        parcel.writeTypedArray(recipients.getAddresses(), 0);

        Bundle bundle = new Bundle();
        bundle.putLong(ShareActivity.EXTRA_THREAD_ID, record.getThreadId());
        bundle.putByteArray(ShareActivity.EXTRA_ADDRESSES_MARSHALLED, parcel.marshall());
        bundle.putInt(ShareActivity.EXTRA_DISTRIBUTION_TYPE, record.getDistributionType());
        bundle.setClassLoader(getClassLoader());

        results.add(new ChooserTarget(name, Icon.createWithBitmap(avatar), 1.0f, componentName, bundle));
        parcel.recycle();
      }

      return results;
    } finally {
      if (cursor != null) cursor.close();
    }
  }
}
