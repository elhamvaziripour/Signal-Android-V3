/**
 * Copyright (C) 2015 Open Whisper Systems
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
package org.BYUSecureSMS.messaging;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.database.Cursor;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import org.BYUSecureSMS.messaging.crypto.MasterSecret;
import org.BYUSecureSMS.messaging.database.Address;
import org.BYUSecureSMS.messaging.database.MediaDatabase;
import org.BYUSecureSMS.messaging.util.AbstractCursorLoader;
import org.BYUSecureSMS.messaging.util.DynamicLanguage;
import org.BYUSecureSMS.messaging.util.SaveAttachmentTask;
import org.BYUSecureSMS.messaging.database.CursorRecyclerViewAdapter;
import org.BYUSecureSMS.messaging.database.DatabaseFactory;
import org.BYUSecureSMS.messaging.recipients.RecipientFactory;
import org.BYUSecureSMS.messaging.recipients.Recipients;
import org.BYUSecureSMS.messaging.util.task.ProgressDialogAsyncTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity for displaying media attachments in-app
 */
public class MediaOverviewActivity extends PassphraseRequiredActionBarActivity implements LoaderManager.LoaderCallbacks<Cursor> {
  private final static String TAG = MediaOverviewActivity.class.getSimpleName();

  public static final String ADDRESSES_EXTRA = "addresses";
  public static final String THREAD_ID_EXTRA = "thread_id";

  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private MasterSecret masterSecret;

  private RecyclerView      gridView;
  private GridLayoutManager gridManager;
  private TextView          noImages;
  private Recipients        recipients;
  private long              threadId;

  @Override
  protected void onPreCreate() {
    this.setTheme(R.style.TextSecure_DarkTheme);
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle bundle, @NonNull MasterSecret masterSecret) {
    this.masterSecret = masterSecret;
    setFullscreenIfPossible();

    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    setContentView(R.layout.media_overview_activity);

    initializeResources();
    initializeActionBar();
    getSupportLoaderManager().initLoader(0, null, MediaOverviewActivity.this);
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    if (gridManager != null) gridManager.setSpanCount(getResources().getInteger(R.integer.media_overview_cols));
  }

  @TargetApi(VERSION_CODES.JELLY_BEAN)
  private void setFullscreenIfPossible() {
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                         WindowManager.LayoutParams.FLAG_FULLSCREEN);

    if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
      getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicLanguage.onResume(this);
  }

  private void initializeActionBar() {
    getSupportActionBar().setTitle(recipients == null
                                   ? getString(R.string.AndroidManifest__all_media)
                                   : getString(R.string.AndroidManifest__all_media_named, recipients.toShortString()));
  }

  @Override
  public void onPause() {
    super.onPause();
  }

  private void initializeResources() {
    threadId = getIntent().getLongExtra(THREAD_ID_EXTRA, -1);

    noImages = (TextView    ) findViewById(R.id.no_images );
    gridView = (RecyclerView) findViewById(R.id.media_grid);
    gridManager = new GridLayoutManager(this, getResources().getInteger(R.integer.media_overview_cols));
    gridView.setLayoutManager(gridManager);
    gridView.setHasFixedSize(true);

    Parcelable[] parcelables = getIntent().getParcelableArrayExtra(ADDRESSES_EXTRA);

    if (parcelables != null) {
      recipients = RecipientFactory.getRecipientsFor(this, Address.fromParcelable(parcelables), true);
    } else if (threadId > -1) {
      recipients = DatabaseFactory.getThreadDatabase(this).getRecipientsForThreadId(threadId);
    } else {
      recipients = null;
    }

    if (recipients != null) {
      recipients.addListener(new Recipients.RecipientsModifiedListener() {
        @Override
        public void onModified(Recipients recipients) {
          initializeActionBar();
        }
      });
    }
  }

  private void saveToDisk() {
    final Context c = this;

    SaveAttachmentTask.showWarningDialog(this, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialogInterface, int i) {
        new ProgressDialogAsyncTask<Void, Void, List<SaveAttachmentTask.Attachment>>(c,
                                                                                     R.string.ConversationFragment_collecting_attahments,
                                                                                     R.string.please_wait) {
          @Override
          protected List<SaveAttachmentTask.Attachment> doInBackground(Void... params) {
            Cursor cursor                                   = DatabaseFactory.getMediaDatabase(c).getMediaForThread(threadId);
            List<SaveAttachmentTask.Attachment> attachments = new ArrayList<>(cursor.getCount());

            while (cursor != null && cursor.moveToNext()) {
              MediaDatabase.MediaRecord record = MediaDatabase.MediaRecord.from(c, masterSecret, cursor);
              attachments.add(new SaveAttachmentTask.Attachment(record.getAttachment().getDataUri(),
                                                                record.getContentType(),
                                                                record.getDate(),
                                                                null));
            }

            return attachments;
          }

          @Override
          protected void onPostExecute(List<SaveAttachmentTask.Attachment> attachments) {
            super.onPostExecute(attachments);

            SaveAttachmentTask saveTask = new SaveAttachmentTask(c, masterSecret, gridView, attachments.size());
            saveTask.execute(attachments.toArray(new SaveAttachmentTask.Attachment[attachments.size()]));
          }
        }.execute();
      }
    }, gridView.getAdapter().getItemCount());
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    super.onPrepareOptionsMenu(menu);

    menu.clear();
    if (gridView.getAdapter() != null && gridView.getAdapter().getItemCount() > 0) {
      MenuInflater inflater = this.getMenuInflater();
      inflater.inflate(R.menu.media_overview, menu);
    }

    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    switch (item.getItemId()) {
    case R.id.save:         saveToDisk(); return true;
    case android.R.id.home: finish();     return true;
    }

    return false;
  }

  @Override
  public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
    return new ThreadMediaLoader(this, threadId);
  }

  @Override
  public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
    Log.w(TAG, "onLoadFinished()");
    gridView.setAdapter(new MediaAdapter(this, masterSecret, cursor, threadId));
    noImages.setVisibility(gridView.getAdapter().getItemCount() > 0 ? View.GONE : View.VISIBLE);
    invalidateOptionsMenu();
  }

  @Override
  public void onLoaderReset(Loader<Cursor> cursorLoader) {
    ((CursorRecyclerViewAdapter)gridView.getAdapter()).changeCursor(null);
  }

  public static class ThreadMediaLoader extends AbstractCursorLoader {
    private final long threadId;

    public ThreadMediaLoader(Context context, long threadId) {
      super(context);
      this.threadId = threadId;
    }

    @Override
    public Cursor getCursor() {
      return DatabaseFactory.getMediaDatabase(getContext()).getMediaForThread(threadId);
    }
  }
}