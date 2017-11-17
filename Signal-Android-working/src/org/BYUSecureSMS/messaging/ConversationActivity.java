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
package org.BYUSecureSMS.messaging;

import android.annotation.TargetApi;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.provider.Browser;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.WindowCompat;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnKeyListener;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.places.ui.PlacePicker;
import com.google.protobuf.ByteString;

import org.BYUSecureSMS.messaging.audio.AudioRecorder;
import org.BYUSecureSMS.messaging.audio.AudioSlidePlayer;
import org.BYUSecureSMS.messaging.color.MaterialColor;
import org.BYUSecureSMS.messaging.components.AnimatingToggle;
import org.BYUSecureSMS.messaging.components.AttachmentTypeSelector;
import org.BYUSecureSMS.messaging.components.ComposeText;
import org.BYUSecureSMS.messaging.components.HidingLinearLayout;
import org.BYUSecureSMS.messaging.components.InputAwareLayout;
import org.BYUSecureSMS.messaging.components.InputPanel;
import org.BYUSecureSMS.messaging.components.KeyboardAwareLinearLayout;
import org.BYUSecureSMS.messaging.components.SendButton;
import org.BYUSecureSMS.messaging.components.camera.QuickAttachmentDrawer;
import org.BYUSecureSMS.messaging.components.emoji.EmojiDrawer;
import org.BYUSecureSMS.messaging.components.identity.UntrustedSendDialog;
import org.BYUSecureSMS.messaging.components.identity.UnverifiedSendDialog;
import org.BYUSecureSMS.messaging.components.location.SignalPlace;
import org.BYUSecureSMS.messaging.components.reminder.InviteReminder;
import org.BYUSecureSMS.messaging.contacts.ContactAccessor;
import org.BYUSecureSMS.messaging.crypto.IdentityKeyParcelable;
import org.BYUSecureSMS.messaging.crypto.MasterCipher;
import org.BYUSecureSMS.messaging.crypto.MasterSecret;
import org.BYUSecureSMS.messaging.crypto.SecurityEvent;
import org.BYUSecureSMS.messaging.database.Address;
import org.BYUSecureSMS.messaging.database.DatabaseFactory;
import org.BYUSecureSMS.messaging.database.DraftDatabase;
import org.BYUSecureSMS.messaging.database.GroupDatabase;
import org.BYUSecureSMS.messaging.database.IdentityDatabase;
import org.BYUSecureSMS.messaging.database.MessagingDatabase;
import org.BYUSecureSMS.messaging.database.MmsSmsColumns;
import org.BYUSecureSMS.messaging.database.RecipientPreferenceDatabase;
import org.BYUSecureSMS.messaging.database.SmsDatabase;
import org.BYUSecureSMS.messaging.database.ThreadDatabase;
import org.BYUSecureSMS.messaging.database.identity.IdentityRecordList;
import org.BYUSecureSMS.messaging.jobs.MultiDeviceBlockedUpdateJob;
import org.BYUSecureSMS.messaging.jobs.RetrieveProfileJob;
import org.BYUSecureSMS.messaging.mms.AttachmentManager;
import org.BYUSecureSMS.messaging.mms.AudioSlide;
import org.BYUSecureSMS.messaging.mms.LocationSlide;
import org.BYUSecureSMS.messaging.mms.MediaConstraints;
import org.BYUSecureSMS.messaging.mms.OutgoingExpirationUpdateMessage;
import org.BYUSecureSMS.messaging.mms.OutgoingGroupMediaMessage;
import org.BYUSecureSMS.messaging.mms.OutgoingMediaMessage;
import org.BYUSecureSMS.messaging.mms.OutgoingSecureMediaMessage;
import org.BYUSecureSMS.messaging.mms.Slide;
import org.BYUSecureSMS.messaging.mms.SlideDeck;
import org.BYUSecureSMS.messaging.notifications.MarkReadReceiver;
import org.BYUSecureSMS.messaging.notifications.MessageNotifier;
import org.BYUSecureSMS.messaging.providers.PersistentBlobProvider;
import org.BYUSecureSMS.messaging.recipients.Recipient;
import org.BYUSecureSMS.messaging.recipients.RecipientFactory;
import org.BYUSecureSMS.messaging.recipients.RecipientFormattingException;
import org.BYUSecureSMS.messaging.scribbles.ScribbleActivity;
import org.BYUSecureSMS.messaging.service.KeyCachingService;
import org.BYUSecureSMS.messaging.service.WebRtcCallService;
import org.BYUSecureSMS.messaging.sms.MessageSender;
import org.BYUSecureSMS.messaging.sms.OutgoingEncryptedMessage;
import org.BYUSecureSMS.messaging.sms.OutgoingEndSessionMessage;
import org.BYUSecureSMS.messaging.sms.OutgoingTextMessage;
import org.BYUSecureSMS.messaging.util.CharacterCalculator;
import org.BYUSecureSMS.messaging.util.Dialogs;
import org.BYUSecureSMS.messaging.util.DirectoryHelper;
import org.BYUSecureSMS.messaging.util.DynamicLanguage;
import org.BYUSecureSMS.messaging.util.DynamicTheme;
import org.BYUSecureSMS.messaging.util.ExpirationUtil;
import org.BYUSecureSMS.messaging.util.GroupUtil;
import org.BYUSecureSMS.messaging.util.IdentityUtil;
import org.BYUSecureSMS.messaging.util.MediaUtil;
import org.BYUSecureSMS.messaging.util.TextSecurePreferences;
import org.BYUSecureSMS.messaging.util.Util;
import org.BYUSecureSMS.messaging.util.ViewUtil;
import org.BYUSecureSMS.messaging.util.concurrent.AssertedSuccessListener;
import org.BYUSecureSMS.messaging.util.concurrent.ListenableFuture;
import org.BYUSecureSMS.messaging.util.concurrent.SettableFuture;
import org.BYUSecureSMS.messaging.util.views.Stub;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.BYUSecureSMS.messaging.components.identity.UnverifiedBannerView;
import org.BYUSecureSMS.messaging.components.reminder.ReminderView;
import org.BYUSecureSMS.messaging.recipients.Recipients;
import org.BYUSecureSMS.messaging.recipients.Recipients.RecipientsModifiedListener;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.whispersystems.libsignal.SessionCipher.SESSION_LOCK;
import static org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContext;

/**
 * Activity for displaying a message thread, as well as
 * composing/sending a new message into that thread.
 *
 * @author Moxie Marlinspike
 *
 */
public class ConversationActivity extends PassphraseRequiredActionBarActivity
    implements ConversationFragment.ConversationFragmentListener,
        AttachmentManager.AttachmentListener,
               RecipientsModifiedListener,
        KeyboardAwareLinearLayout.OnKeyboardShownListener,
        QuickAttachmentDrawer.AttachmentDrawerListener,
        InputPanel.Listener,
               InputPanel.MediaListener
{
  private static final String TAG = ConversationActivity.class.getSimpleName();

  public static final String ADDRESSES_EXTRA         = "addresses";
  public static final String THREAD_ID_EXTRA         = "thread_id";
  public static final String IS_ARCHIVED_EXTRA       = "is_archived";
  public static final String TEXT_EXTRA              = "draft_text";
  public static final String DISTRIBUTION_TYPE_EXTRA = "distribution_type";
  public static final String TIMING_EXTRA            = "timing";
  public static final String LAST_SEEN_EXTRA         = "last_seen";

  private static final int PICK_GALLERY      = 1;
  private static final int PICK_DOCUMENT     = 2;
  private static final int PICK_AUDIO        = 3;
  private static final int PICK_CONTACT_INFO = 4;
  private static final int GROUP_EDIT        = 5;
  private static final int TAKE_PHOTO        = 6;
  private static final int ADD_CONTACT       = 7;
  private static final int PICK_LOCATION     = 8;
  private static final int PICK_GIF          = 9;
  private static final int SMS_DEFAULT       = 10;

  private MasterSecret masterSecret;
  protected ComposeText composeText;
  private AnimatingToggle buttonToggle;
  private SendButton sendButton;
  private   ImageButton                attachButton;
  protected ConversationTitleView      titleView;
  private   TextView                   charactersLeft;
  private   ConversationFragment       fragment;
  private   Button                     unblockButton;
  private   Button                     makeDefaultSmsButton;
  private InputAwareLayout container;
  private   View                       composePanel;
  protected Stub<ReminderView> reminderView;
  private   Stub<UnverifiedBannerView> unverifiedBannerView;
  private   Button                     verifiedStatusButton;
  private AttachmentTypeSelector attachmentTypeSelector;
  private   AttachmentManager      attachmentManager;
  private AudioRecorder audioRecorder;
  private   BroadcastReceiver      securityUpdateReceiver;
  private   BroadcastReceiver      recipientsStaleReceiver;
  private   Stub<EmojiDrawer>      emojiDrawerStub;
  protected HidingLinearLayout quickAttachmentToggle;
  private   QuickAttachmentDrawer  quickAttachmentDrawer;
  private   InputPanel             inputPanel;

  private Recipients recipients;
  private long       threadId;
  private int        distributionType;
  private boolean    archived;
  private boolean    isSecureText;
  private boolean    isDefaultSms = true;
  private boolean    isMmsEnabled = true;

  private final IdentityRecordList identityRecords = new IdentityRecordList();
  private final DynamicTheme dynamicTheme    = new DynamicTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle state, @NonNull MasterSecret masterSecret) {
    Log.w(TAG, "onCreate()");
    this.masterSecret = masterSecret;

    supportRequestWindowFeature(WindowCompat.FEATURE_ACTION_BAR_OVERLAY);
    setContentView(R.layout.conversation_activity);

    fragment = initFragment(R.id.fragment_content, new ConversationFragment(),
                            masterSecret, dynamicLanguage.getCurrentLocale());

    initializeReceivers();
    initializeActionBar();
    initializeViews();
    initializeResources();
    initializeSecurity(false, isDefaultSms).addListener(new AssertedSuccessListener<Boolean>() {
      @Override
      public void onSuccess(Boolean result) {
        initializeProfiles();
        initializeDraft();
      }
    });

     }

  @Override
  protected void onNewIntent(Intent intent) {
    Log.w(TAG, "onNewIntent()");
    
    if (isFinishing()) {
      Log.w(TAG, "Activity is finishing...");
      return;
    }

    if (!Util.isEmpty(composeText) || attachmentManager.isAttachmentPresent()) {
      saveDraft();
      attachmentManager.clear(false);
      composeText.setText("");
    }

    setIntent(intent);
    initializeResources();
    initializeSecurity(false, isDefaultSms).addListener(new AssertedSuccessListener<Boolean>() {
      @Override
      public void onSuccess(Boolean result) {
        initializeDraft();
      }
    });

    if (fragment != null) {
      fragment.onNewIntent();
    }
  }

  @Override
  protected void onStart() {
    super.onStart();
    EventBus.getDefault().register(this);

    Log.d(TAG, "Heeeeeeeeeeeeeeeeeeeeeeeeeer1");



    IdentityUtil.getRemoteIdentityKey(ConversationActivity.this, recipients.getPrimaryRecipient()).addListener(new ListenableFuture.Listener<Optional<IdentityDatabase.IdentityRecord>>() {

      @Override
      public void onSuccess(Optional<IdentityDatabase.IdentityRecord> result) {
        Log.d(TAG, "Heeeeeeeeeeeeeeeeeeeeeeeeeer2");

        if (result.isPresent()) {
          Log.d(TAG, "Heeeeeeeeeeeeeeeeeeeeeeeeeer3");

          if (result.get().getVerifiedStatus() == IdentityDatabase.VerifiedStatus.DEFAULT) {
            // Finger print is not verified yet
            verifiedStatusButton.setText("Action Needed. Click to verify your safety number.");
            verifiedStatusButton.setAllCaps(false);
            verifiedStatusButton.setTextColor(Color.WHITE);
            verifiedStatusButton.setBackgroundColor(Color.RED);
            verifiedStatusButton.setPadding(0, 10, 0, 0);
            verifiedStatusButton.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.white_warning, 0, 0, 0);

          }
          if (result.get().getVerifiedStatus() == IdentityDatabase.VerifiedStatus.VERIFIED)
          {
            verifiedStatusButton.setText("Conversation is secure. Safety number is verified.");
            verifiedStatusButton.setTextColor( getResources().getColor(R.color.textsecure_primary));
            verifiedStatusButton.setBackgroundColor(Color.WHITE);
            verifiedStatusButton.setPadding(0, 10, 0, 0);
            verifiedStatusButton.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.verified_icon, 0, 0, 0);
            verifiedStatusButton.setAllCaps(false);

          }
        }
        else{
          verifiedStatusButton.setText("Action Needed. Click to verify your safety number.");
          verifiedStatusButton.setAllCaps(false);
          verifiedStatusButton.setTextColor(Color.WHITE);
          verifiedStatusButton.setBackgroundColor(Color.RED);
          verifiedStatusButton.setPadding(0, 10, 0, 0);
          verifiedStatusButton.setCompoundDrawablesWithIntrinsicBounds(
                  R.drawable.white_warning, 0, 0, 0);
        }
      }

      @Override
      public void onFailure(ExecutionException e) {
        Log.w(TAG, e);
        Log.d(TAG, "Heeeeeeeeeeeeeeeeeeeeeeeeeer4");

      }
    });



  }

  @Override
  protected void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
    quickAttachmentDrawer.onResume();

    initializeEnabledCheck();
    initializeMmsEnabledCheck();
    initializeIdentityRecords();
    composeText.setTransport(sendButton.getSelectedTransport());

    titleView.setTitle(recipients);
    setActionBarColor(recipients.getColor());
    setBlockedUserState(recipients, isSecureText, isDefaultSms);
    calculateCharactersRemaining();

    MessageNotifier.setVisibleThread(threadId);
    markThreadAsRead();

    Log.w(TAG, "onResume() Finished: " + (System.currentTimeMillis() - getIntent().getLongExtra(TIMING_EXTRA, 0)));
  }

  @Override
  protected void onPause() {
    super.onPause();
    MessageNotifier.setVisibleThread(-1L);
    if (isFinishing()) overridePendingTransition(R.anim.fade_scale_in, R.anim.slide_to_right);
    quickAttachmentDrawer.onPause();
    inputPanel.onPause();

    fragment.setLastSeen(System.currentTimeMillis());
    markLastSeen();
    AudioSlidePlayer.stopAll();
    EventBus.getDefault().unregister(this);
  }

  @Override
  protected void onStop() {
    super.onStop();
    EventBus.getDefault().unregister(this);
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    Log.w(TAG, "onConfigurationChanged(" + newConfig.orientation + ")");
    super.onConfigurationChanged(newConfig);
    composeText.setTransport(sendButton.getSelectedTransport());
    quickAttachmentDrawer.onConfigurationChanged();

    if (emojiDrawerStub.resolved() && container.getCurrentInput() == emojiDrawerStub.get()) {
      container.hideAttachedInput(true);
    }
  }

  @Override
  protected void onDestroy() {
    saveDraft();
    if (recipients != null)              recipients.removeListener(this);
    if (securityUpdateReceiver != null)  unregisterReceiver(securityUpdateReceiver);
    if (recipientsStaleReceiver != null) unregisterReceiver(recipientsStaleReceiver);
    super.onDestroy();
  }

  @Override
  public void onActivityResult(final int reqCode, int resultCode, Intent data) {
    Log.w(TAG, "onActivityResult called: " + reqCode + ", " + resultCode + " , " + data);
    super.onActivityResult(reqCode, resultCode, data);

    if ((data == null && reqCode != TAKE_PHOTO && reqCode != SMS_DEFAULT) ||
        (resultCode != RESULT_OK && reqCode != SMS_DEFAULT))
    {
      return;
    }

    switch (reqCode) {
    case PICK_GALLERY:
      AttachmentManager.MediaType mediaType;

      String mimeType = MediaUtil.getMimeType(this, data.getData());

      if      (MediaUtil.isGif(mimeType))   mediaType = AttachmentManager.MediaType.GIF;
      else if (MediaUtil.isVideo(mimeType)) mediaType = AttachmentManager.MediaType.VIDEO;
      else                                  mediaType = AttachmentManager.MediaType.IMAGE;

      setMedia(data.getData(), mediaType);
      break;
    case PICK_DOCUMENT:
      setMedia(data.getData(), AttachmentManager.MediaType.DOCUMENT);
      break;
    case PICK_AUDIO:
      setMedia(data.getData(), AttachmentManager.MediaType.AUDIO);
      break;
    case PICK_CONTACT_INFO:
      addAttachmentContactInfo(data.getData());
      break;
    case GROUP_EDIT:
      recipients = RecipientFactory.getRecipientsFor(this, RecipientFactory.getRecipientFor(this, (Address)data.getParcelableExtra(GroupCreateActivity.GROUP_ADDRESS_EXTRA), true), true);
      recipients.addListener(this);
      titleView.setTitle(recipients);
      setBlockedUserState(recipients, isSecureText, isDefaultSms);
      supportInvalidateOptionsMenu();
      break;
    case TAKE_PHOTO:
      if (attachmentManager.getCaptureUri() != null) {
        setMedia(attachmentManager.getCaptureUri(), AttachmentManager.MediaType.IMAGE);
      }
      break;
    case ADD_CONTACT:
      recipients = RecipientFactory.getRecipientsFor(this, recipients.getAddresses(), true);
      recipients.addListener(this);
      fragment.reloadList();
      break;
    case PICK_LOCATION:
      SignalPlace place = new SignalPlace(PlacePicker.getPlace(data, this));
      attachmentManager.setLocation(masterSecret, place, getCurrentMediaConstraints());
      break;
    case PICK_GIF:
      setMedia(data.getData(), AttachmentManager.MediaType.GIF);
      break;
    case ScribbleActivity.SCRIBBLE_REQUEST_CODE:
      setMedia(data.getData(), AttachmentManager.MediaType.IMAGE);
      break;
    case SMS_DEFAULT:
      initializeSecurity(isSecureText, isDefaultSms);
      break;
    }
  }

  @Override
  public void startActivity(Intent intent) {
    if (intent.getStringExtra(Browser.EXTRA_APPLICATION_ID) != null) {
      intent.removeExtra(Browser.EXTRA_APPLICATION_ID);
    }

    try {
      super.startActivity(intent);
    } catch (ActivityNotFoundException e) {
      Log.w(TAG, e);
      Toast.makeText(this, R.string.ConversationActivity_there_is_no_app_available_to_handle_this_link_on_your_device, Toast.LENGTH_LONG).show();
    }
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getMenuInflater();
    menu.clear();

    if (isSecureText) {
      if (recipients.getExpireMessages() > 0) {
        inflater.inflate(R.menu.conversation_expiring_on, menu);

        final MenuItem item       = menu.findItem(R.id.menu_expiring_messages);
        final View     actionView = MenuItemCompat.getActionView(item);
        final TextView badgeView  = (TextView)actionView.findViewById(R.id.expiration_badge);

        badgeView.setText(ExpirationUtil.getExpirationAbbreviatedDisplayValue(this, recipients.getExpireMessages()));
        actionView.setOnClickListener(new OnClickListener() {
          @Override
          public void onClick(View v) {
            onOptionsItemSelected(item);
          }
        });
      } else {
        inflater.inflate(R.menu.conversation_expiring_off, menu);
      }
    }

    if (isSingleConversation()) {
      if (isSecureText) inflater.inflate(R.menu.conversation_callable_secure, menu);
      else              inflater.inflate(R.menu.conversation_callable_insecure, menu);
    } else if (isGroupConversation()) {
      inflater.inflate(R.menu.conversation_group_options, menu);

      if (!isPushGroupConversation()) {
        inflater.inflate(R.menu.conversation_mms_group_options, menu);
        if (distributionType == ThreadDatabase.DistributionTypes.BROADCAST) {
          menu.findItem(R.id.menu_distribution_broadcast).setChecked(true);
        } else {
          menu.findItem(R.id.menu_distribution_conversation).setChecked(true);
        }
      } else if (isActiveGroup()) {
        inflater.inflate(R.menu.conversation_push_group_options, menu);
      }
    }

    inflater.inflate(R.menu.conversation, menu);

    if (isSingleConversation() && isSecureText) {
      inflater.inflate(R.menu.conversation_secure, menu);
    } else if (isSingleConversation()) {
      inflater.inflate(R.menu.conversation_insecure, menu);
    }

    if (recipients != null && recipients.isMuted()) inflater.inflate(R.menu.conversation_muted, menu);
    else                                            inflater.inflate(R.menu.conversation_unmuted, menu);

    if (isSingleConversation() && getRecipients().getPrimaryRecipient().getContactUri() == null) {
      inflater.inflate(R.menu.conversation_add_to_contacts, menu);
    }

    super.onPrepareOptionsMenu(menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    switch (item.getItemId()) {
    case R.id.menu_call_secure:
    case R.id.menu_call_insecure:             handleDial(getRecipients().getPrimaryRecipient()); return true;
    case R.id.menu_add_attachment:            handleAddAttachment();                             return true;
    case R.id.menu_view_media:                handleViewMedia();                                 return true;
    case R.id.menu_add_to_contacts:           handleAddToContacts();                             return true;
    case R.id.menu_reset_secure_session:      handleResetSecureSession();                        return true;
    case R.id.menu_group_recipients:          handleDisplayGroupRecipients();                    return true;
    case R.id.menu_distribution_broadcast:    handleDistributionBroadcastEnabled(item);          return true;
    case R.id.menu_distribution_conversation: handleDistributionConversationEnabled(item);       return true;
    case R.id.menu_edit_group:                handleEditPushGroup();                             return true;
    case R.id.menu_leave:                     handleLeavePushGroup();                            return true;
    case R.id.menu_invite:                    handleInviteLink();                                return true;
    case R.id.menu_mute_notifications:        handleMuteNotifications();                         return true;
    case R.id.menu_unmute_notifications:      handleUnmuteNotifications();                       return true;
    case R.id.menu_conversation_settings:     handleConversationSettings();                      return true;
    case R.id.menu_expiring_messages_off:
    case R.id.menu_expiring_messages:         handleSelectMessageExpiration();                   return true;
    case android.R.id.home:                   handleReturnToConversationList();                  return true;
    }

    return false;
  }

  @Override
  public void onBackPressed() {
    Log.w(TAG, "onBackPressed()");
    if (container.isInputOpen()) container.hideCurrentInput(composeText);
    else                         super.onBackPressed();
  }

  @Override
  public void onKeyboardShown() {
    inputPanel.onKeyboardShown();
  }

  //////// Event Handlers

  private void handleReturnToConversationList() {
    Intent intent = new Intent(this, (archived ? ConversationListArchiveActivity.class : ConversationListActivity.class));
    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    startActivity(intent);
    finish();
  }

  private void handleSelectMessageExpiration() {
    if (isPushGroupConversation() && !isActiveGroup()) {
      return;
    }

    ExpirationDialog.show(this, recipients.getExpireMessages(), new ExpirationDialog.OnClickListener() {
      @Override
      public void onClick(final int expirationTime) {
        new AsyncTask<Void, Void, Void>() {
          @Override
          protected Void doInBackground(Void... params) {
            DatabaseFactory.getRecipientPreferenceDatabase(ConversationActivity.this)
                           .setExpireMessages(recipients, expirationTime);
            recipients.setExpireMessages(expirationTime);

            OutgoingExpirationUpdateMessage outgoingMessage = new OutgoingExpirationUpdateMessage(getRecipients(), System.currentTimeMillis(), expirationTime * 1000);
            MessageSender.send(ConversationActivity.this, masterSecret, outgoingMessage, threadId, false, null);

            return null;
          }

          @Override
          protected void onPostExecute(Void result) {
            invalidateOptionsMenu();
            if (fragment != null) fragment.setLastSeen(0);
          }
        }.execute();
      }
    });
  }

  private void handleMuteNotifications() {
    MuteDialog.show(this, new MuteDialog.MuteSelectionListener() {
      @Override
      public void onMuted(final long until) {
        recipients.setMuted(until);

        new AsyncTask<Void, Void, Void>() {
          @Override
          protected Void doInBackground(Void... params) {
            DatabaseFactory.getRecipientPreferenceDatabase(ConversationActivity.this)
                           .setMuted(recipients, until);

            return null;
          }
        }.execute();
      }
    });
  }

  private void handleConversationSettings() {
    titleView.performClick();
  }

  private void handleUnmuteNotifications() {
    recipients.setMuted(0);

    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        DatabaseFactory.getRecipientPreferenceDatabase(ConversationActivity.this)
                       .setMuted(recipients, 0);

        return null;
      }
    }.execute();
  }

  private void handleUnblock() {
    new AlertDialog.Builder(this)
        .setTitle(R.string.ConversationActivity_unblock_this_contact_question)
        .setMessage(R.string.ConversationActivity_you_will_once_again_be_able_to_receive_messages_and_calls_from_this_contact)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(R.string.ConversationActivity_unblock, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            recipients.setBlocked(false);

            new AsyncTask<Void, Void, Void>() {
              @Override
              protected Void doInBackground(Void... params) {
                DatabaseFactory.getRecipientPreferenceDatabase(ConversationActivity.this)
                               .setBlocked(recipients, false);

                ApplicationContext.getInstance(ConversationActivity.this)
                                  .getJobManager()
                                  .add(new MultiDeviceBlockedUpdateJob(ConversationActivity.this));

                return null;
              }
            }.execute();
          }
        }).show();
  }

  @TargetApi(Build.VERSION_CODES.KITKAT)
  private void handleMakeDefaultSms() {
    Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
    intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, getPackageName());
    startActivityForResult(intent, SMS_DEFAULT);
  }

  private void handleInviteLink() {
    try {
      String inviteText;

      boolean a = SecureRandom.getInstance("SHA1PRNG").nextBoolean();
      if (a) inviteText = getString(R.string.ConversationActivity_lets_switch_to_signal, "https://sgnl.link/1LoIMUl");
      else   inviteText = getString(R.string.ConversationActivity_lets_use_this_to_chat, "https://sgnl.link/1MF56H1");

      if (isDefaultSms) {
        composeText.appendInvite(inviteText);
      } else {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("smsto:" + recipients.getPrimaryRecipient().getAddress().serialize()));
        intent.putExtra("sms_body", inviteText);
        intent.putExtra(Intent.EXTRA_TEXT, inviteText);
        startActivity(intent);
      }
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  private void handleResetSecureSession() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(R.string.ConversationActivity_reset_secure_session_question);
    builder.setIconAttribute(R.attr.dialog_alert_icon);
    builder.setCancelable(true);
    builder.setMessage(R.string.ConversationActivity_this_may_help_if_youre_having_encryption_problems);
    builder.setPositiveButton(R.string.ConversationActivity_reset, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        if (isSingleConversation()) {
          final Context context = getApplicationContext();

          OutgoingEndSessionMessage endSessionMessage =
              new OutgoingEndSessionMessage(new OutgoingTextMessage(getRecipients(), "TERMINATE", 0, -1));

          new AsyncTask<OutgoingEndSessionMessage, Void, Long>() {
            @Override
            protected Long doInBackground(OutgoingEndSessionMessage... messages) {
              return MessageSender.send(context, masterSecret, messages[0], threadId, false, null);
            }

            @Override
            protected void onPostExecute(Long result) {
              sendComplete(result);
            }
          }.execute(endSessionMessage);
        }
      }
    });
    builder.setNegativeButton(android.R.string.cancel, null);
    builder.show();
  }

  private void handleViewMedia() {
    Intent intent = new Intent(this, MediaOverviewActivity.class);
    intent.putExtra(MediaOverviewActivity.THREAD_ID_EXTRA, threadId);
    intent.putExtra(MediaOverviewActivity.ADDRESSES_EXTRA, recipients.getAddresses());
    startActivity(intent);
  }

  private void handleLeavePushGroup() {
    if (getRecipients() == null) {
      Toast.makeText(this, getString(R.string.ConversationActivity_invalid_recipient),
                     Toast.LENGTH_LONG).show();
      return;
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(getString(R.string.ConversationActivity_leave_group));
    builder.setIconAttribute(R.attr.dialog_info_icon);
    builder.setCancelable(true);
    builder.setMessage(getString(R.string.ConversationActivity_are_you_sure_you_want_to_leave_this_group));
    builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        Context self = ConversationActivity.this;
        try {
          byte[] groupId = GroupUtil.getDecodedId(getRecipients().getPrimaryRecipient().getAddress().toGroupString());
          DatabaseFactory.getGroupDatabase(self).setActive(groupId, false);

          GroupContext context = GroupContext.newBuilder()
                                             .setId(ByteString.copyFrom(groupId))
                                             .setType(GroupContext.Type.QUIT)
                                             .build();

          OutgoingGroupMediaMessage outgoingMessage = new OutgoingGroupMediaMessage(getRecipients(), context, null, System.currentTimeMillis(), 0);
          MessageSender.send(self, masterSecret, outgoingMessage, threadId, false, null);
          DatabaseFactory.getGroupDatabase(self).remove(groupId, Address.fromSerialized(TextSecurePreferences.getLocalNumber(self)));
          initializeEnabledCheck();
        } catch (IOException e) {
          Log.w(TAG, e);
          Toast.makeText(self, R.string.ConversationActivity_error_leaving_group, Toast.LENGTH_LONG).show();
        }
      }
    });

    builder.setNegativeButton(R.string.no, null);
    builder.show();
  }

  private void handleEditPushGroup() {
    Intent intent = new Intent(ConversationActivity.this, GroupCreateActivity.class);
    intent.putExtra(GroupCreateActivity.GROUP_ADDRESS_EXTRA, recipients.getPrimaryRecipient().getAddress());
    startActivityForResult(intent, GROUP_EDIT);
  }

  private void handleDistributionBroadcastEnabled(MenuItem item) {
    distributionType = ThreadDatabase.DistributionTypes.BROADCAST;
    item.setChecked(true);

    if (threadId != -1) {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          DatabaseFactory.getThreadDatabase(ConversationActivity.this)
                         .setDistributionType(threadId, ThreadDatabase.DistributionTypes.BROADCAST);
          return null;
        }
      }.execute();
    }
  }

  private void handleDistributionConversationEnabled(MenuItem item) {
    distributionType = ThreadDatabase.DistributionTypes.CONVERSATION;
    item.setChecked(true);

    if (threadId != -1) {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          DatabaseFactory.getThreadDatabase(ConversationActivity.this)
                         .setDistributionType(threadId, ThreadDatabase.DistributionTypes.CONVERSATION);
          return null;
        }
      }.execute();
    }
  }

  private void handleDial(final Recipient recipient) {
    if (recipient == null) return;

    if (isSecureText) {

      IdentityUtil.getRemoteIdentityKey(ConversationActivity.this, recipients.getPrimaryRecipient()).addListener(new ListenableFuture.Listener<Optional<IdentityDatabase.IdentityRecord>>() {
        @Override
        public void onSuccess(Optional<IdentityDatabase.IdentityRecord> result) {
          if (result.isPresent()) {

            Intent intent = new Intent(ConversationActivity.this, WebRtcCallService.class);
            intent.putExtra(VerifyIdentityActivity.ADDRESS_EXTRA, recipients.getPrimaryRecipient().getAddress());
            intent.putExtra(VerifyIdentityActivity.IDENTITY_EXTRA, new IdentityKeyParcelable(result.get().getIdentityKey()));
            intent.putExtra(VerifyIdentityActivity.VERIFIED_EXTRA, result.get().getVerifiedStatus() == IdentityDatabase.VerifiedStatus.VERIFIED);

            intent.setAction(WebRtcCallService.ACTION_OUTGOING_CALL);
            intent.putExtra(WebRtcCallService.EXTRA_REMOTE_ADDRESS, recipient.getAddress());
            startService(intent);

            Intent activityIntent = new Intent(ConversationActivity.this, WebRtcCallActivity.class);
            activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(activityIntent);
          }
        }

        @Override
        public void onFailure(ExecutionException e) {
          Log.w(TAG, e);
        }
      });
   } else {
      try {
        Intent dialIntent = new Intent(Intent.ACTION_DIAL,
                                       Uri.parse("tel:" + recipient.getAddress().serialize()));
        startActivity(dialIntent);
      } catch (ActivityNotFoundException anfe) {
        Log.w(TAG, anfe);
        Dialogs.showAlertDialog(this,
                                getString(R.string.ConversationActivity_calls_not_supported),
                                getString(R.string.ConversationActivity_this_device_does_not_appear_to_support_dial_actions));
      }
    }
  }

  private void handleDisplayGroupRecipients() {
    new GroupMembersDialog(this, getRecipients()).display();
  }

  private void handleAddToContacts() {
    if (recipients.getPrimaryRecipient().getAddress().isGroup()) return;

    try {
      final Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
      if (recipients.getPrimaryRecipient().getAddress().isEmail()) {
        intent.putExtra(ContactsContract.Intents.Insert.EMAIL, recipients.getPrimaryRecipient().getAddress().toEmailString());
      } else {
        intent.putExtra(ContactsContract.Intents.Insert.PHONE, recipients.getPrimaryRecipient().getAddress().toPhoneString());
      }
      intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
      startActivityForResult(intent, ADD_CONTACT);
    } catch (ActivityNotFoundException e) {
      Log.w(TAG, e);
    }
  }

  private void handleAddAttachment() {
    if (this.isMmsEnabled || isSecureText) {
      if (attachmentTypeSelector == null) {
        attachmentTypeSelector = new AttachmentTypeSelector(this, getSupportLoaderManager(), new AttachmentTypeListener());
      }
      attachmentTypeSelector.show(this, attachButton);
    } else {
      handleManualMmsRequired();
    }
  }

  private void handleManualMmsRequired() {
    Toast.makeText(this, R.string.MmsDownloader_error_reading_mms_settings, Toast.LENGTH_LONG).show();

    Intent intent = new Intent(this, PromptMmsActivity.class);
    intent.putExtras(getIntent().getExtras());
    startActivity(intent);
  }

  private void handleUnverifiedRecipients() {
    List<Recipient>      unverifiedRecipients = identityRecords.getUnverifiedRecipients(this);
    List<IdentityDatabase.IdentityRecord> unverifiedRecords    = identityRecords.getUnverifiedRecords();
    String               message              = IdentityUtil.getUnverifiedSendDialogDescription(this, unverifiedRecipients);

    if (message == null) return;

    new UnverifiedSendDialog(this, message, unverifiedRecords, new UnverifiedSendDialog.ResendListener() {
      @Override
      public void onResendMessage() {
        initializeIdentityRecords().addListener(new ListenableFuture.Listener<Boolean>() {
          @Override
          public void onSuccess(Boolean result) {
            sendMessage();
          }

          @Override
          public void onFailure(ExecutionException e) {
            throw new AssertionError(e);
          }
        });
      }
    }).show();
  }

  private void handleUntrustedRecipients() {
    List<Recipient>      untrustedRecipients = identityRecords.getUntrustedRecipients(this);
    List<IdentityDatabase.IdentityRecord> untrustedRecords    = identityRecords.getUntrustedRecords();
    String               untrustedMessage    = IdentityUtil.getUntrustedSendDialogDescription(this, untrustedRecipients);

    if (untrustedMessage == null) return;

    new UntrustedSendDialog(this, untrustedMessage, untrustedRecords, new UntrustedSendDialog.ResendListener() {
      @Override
      public void onResendMessage() {
        initializeIdentityRecords().addListener(new ListenableFuture.Listener<Boolean>() {
          @Override
          public void onSuccess(Boolean result) {
            sendMessage();
          }

          @Override
          public void onFailure(ExecutionException e) {
            throw new AssertionError(e);
          }
        });
      }
    }).show();
  }

  private void handleSecurityChange(boolean isSecureText, boolean isDefaultSms) {
    this.isSecureText  = isSecureText;
    this.isDefaultSms  = isDefaultSms;

    boolean isMediaMessage = !recipients.isSingleRecipient() || attachmentManager.isAttachmentPresent();

    sendButton.resetAvailableTransports(isMediaMessage);

    if (!isSecureText)                 sendButton.disableTransport(TransportOption.Type.TEXTSECURE);
    if (recipients.isGroupRecipient()) sendButton.disableTransport(TransportOption.Type.SMS);

    if (isSecureText) sendButton.setDefaultTransport(TransportOption.Type.TEXTSECURE);
    else              sendButton.setDefaultTransport(TransportOption.Type.SMS);

    calculateCharactersRemaining();
    supportInvalidateOptionsMenu();
    setBlockedUserState(recipients, isSecureText, isDefaultSms);
  }

  ///// Initializers

  private void initializeDraft() {
    final String    draftText      = getIntent().getStringExtra(TEXT_EXTRA);
    final Uri       draftMedia     = getIntent().getData();
    final AttachmentManager.MediaType draftMediaType = AttachmentManager.MediaType.from(getIntent().getType());
    
    if (draftText != null)                            composeText.setText(draftText);
    if (draftMedia != null && draftMediaType != null) setMedia(draftMedia, draftMediaType);

    if (draftText == null && draftMedia == null && draftMediaType == null) {
      initializeDraftFromDatabase();
    } else {
      updateToggleButtonState();
    }
  }

  private void initializeEnabledCheck() {
    boolean enabled = !(isPushGroupConversation() && !isActiveGroup());
    inputPanel.setEnabled(enabled);
    sendButton.setEnabled(enabled);
    attachButton.setEnabled(enabled);
  }

  private void initializeDraftFromDatabase() {
    new AsyncTask<Void, Void, List<DraftDatabase.Draft>>() {
      @Override
      protected List<DraftDatabase.Draft> doInBackground(Void... params) {
        MasterCipher masterCipher   = new MasterCipher(masterSecret);
        DraftDatabase draftDatabase = DatabaseFactory.getDraftDatabase(ConversationActivity.this);
        List<DraftDatabase.Draft> results         = draftDatabase.getDrafts(masterCipher, threadId);

        draftDatabase.clearDrafts(threadId);

        return results;
      }

      @Override
      protected void onPostExecute(List<DraftDatabase.Draft> drafts) {
        for (DraftDatabase.Draft draft : drafts) {
          try {
            if (draft.getType().equals(DraftDatabase.Draft.TEXT)) {
              composeText.setText(draft.getValue());
            } else if (draft.getType().equals(DraftDatabase.Draft.LOCATION)) {
              attachmentManager.setLocation(masterSecret, SignalPlace.deserialize(draft.getValue()), getCurrentMediaConstraints());
            } else if (draft.getType().equals(DraftDatabase.Draft.IMAGE)) {
              setMedia(Uri.parse(draft.getValue()), AttachmentManager.MediaType.IMAGE);
            } else if (draft.getType().equals(DraftDatabase.Draft.AUDIO)) {
              setMedia(Uri.parse(draft.getValue()), AttachmentManager.MediaType.AUDIO);
            } else if (draft.getType().equals(DraftDatabase.Draft.VIDEO)) {
              setMedia(Uri.parse(draft.getValue()), AttachmentManager.MediaType.VIDEO);
            }
          } catch (IOException e) {
            Log.w(TAG, e);
          }
        }

        updateToggleButtonState();
      }
    }.execute();
  }

  private ListenableFuture<Boolean> initializeSecurity(final boolean currentSecureText,
                                                       final boolean currentIsDefaultSms)
  {
    final SettableFuture<Boolean> future = new SettableFuture<>();

    handleSecurityChange(currentSecureText || isPushGroupConversation(), currentIsDefaultSms);

    new AsyncTask<Recipients, Void, boolean[]>() {
      @Override
      protected boolean[] doInBackground(Recipients... params) {
        Context           context      = ConversationActivity.this;
        Recipients        recipients   = params[0];
        DirectoryHelper.UserCapabilities capabilities = DirectoryHelper.getUserCapabilities(context, recipients);

        if (capabilities.getTextCapability() == DirectoryHelper.UserCapabilities.Capability.UNKNOWN ||
            capabilities.getVideoCapability() == DirectoryHelper.UserCapabilities.Capability.UNKNOWN)
        {
          try {
            capabilities = DirectoryHelper.refreshDirectoryFor(context, masterSecret, recipients);
          } catch (IOException e) {
            Log.w(TAG, e);
          }
        }

        return new boolean[] {capabilities.getTextCapability() == DirectoryHelper.UserCapabilities.Capability.SUPPORTED, Util.isDefaultSmsProvider(context)};
      }

      @Override
      protected void onPostExecute(boolean[] result) {
        if (result[0] != currentSecureText || result[1] != currentIsDefaultSms) {
          handleSecurityChange(result[0], result[1]);
        }
        future.set(true);
        onSecurityUpdated();
      }
    }.execute(recipients);

    return future;
  }

  private void onSecurityUpdated() {
    updateRecipientPreferences();
  }

  private void updateRecipientPreferences() {
    new RecipientPreferencesTask().execute(recipients);
  }

  protected void updateInviteReminder(boolean seenInvite) {
    Log.w(TAG, "updateInviteReminder(" + seenInvite+")");
    if (TextSecurePreferences.isPushRegistered(this)      &&
        TextSecurePreferences.isShowInviteReminders(this) &&
        !isSecureText                                     &&
        !seenInvite                                       &&
        recipients.isSingleRecipient()                    &&
        recipients.getPrimaryRecipient() != null          &&
        recipients.getPrimaryRecipient().getContactUri() != null)
    {
      InviteReminder reminder = new InviteReminder(this, recipients);
      reminder.setOkListener(new OnClickListener() {
        @Override
        public void onClick(View v) {
          handleInviteLink();
          reminderView.get().requestDismiss();
        }
      });
      reminderView.get().showReminder(reminder);
    } else if (reminderView.resolved()) {
      reminderView.get().hide();
    }
  }

  private void updateDefaultSubscriptionId(Optional<Integer> defaultSubscriptionId) {
    Log.w(TAG, "updateDefaultSubscriptionId(" + defaultSubscriptionId.orNull() + ")");
    sendButton.setDefaultSubscriptionId(defaultSubscriptionId);
  }

  private void initializeMmsEnabledCheck() {
    new AsyncTask<Void, Void, Boolean>() {
      @Override
      protected Boolean doInBackground(Void... params) {
        return Util.isMmsCapable(ConversationActivity.this);
      }

      @Override
      protected void onPostExecute(Boolean isMmsEnabled) {
        ConversationActivity.this.isMmsEnabled = isMmsEnabled;
      }
    }.execute();
  }

  private ListenableFuture<Boolean> initializeIdentityRecords() {
    final SettableFuture<Boolean> future = new SettableFuture<>();

    new AsyncTask<Recipients, Void, Pair<IdentityRecordList, String>>() {
      @Override
      protected @NonNull Pair<IdentityRecordList, String> doInBackground(Recipients... params) {
        try {
          IdentityDatabase   identityDatabase   = DatabaseFactory.getIdentityDatabase(ConversationActivity.this);
          IdentityRecordList identityRecordList = new IdentityRecordList();
          Recipients         recipients         = params[0];

          if (recipients.isGroupRecipient()) {
            recipients = DatabaseFactory.getGroupDatabase(ConversationActivity.this)
                                        .getGroupMembers(GroupUtil.getDecodedId(recipients.getPrimaryRecipient().getAddress().toGroupString()), false);
          }

          for (Address recipientAddress : recipients.getAddresses()) {
            Log.w(TAG, "Loading identity for: " + recipientAddress);
            identityRecordList.add(identityDatabase.getIdentity(recipientAddress));
          }

          String message = null;

          if (identityRecordList.isUnverified()) {
            message = IdentityUtil.getUnverifiedBannerDescription(ConversationActivity.this, identityRecordList.getUnverifiedRecipients(ConversationActivity.this));
          }

          return new Pair<>(identityRecordList, message);
        } catch (IOException e) {
          throw new AssertionError(e);
        }
      }

      @Override
      protected void onPostExecute(@NonNull Pair<IdentityRecordList, String> result) {
        Log.w(TAG, "Got identity records: " + result.first.isUnverified());
        identityRecords.replaceWith(result.first);

        if (result.second != null) {
          Log.w(TAG, "Replacing banner...");
          unverifiedBannerView.get().display(result.second, result.first.getUnverifiedRecords(),
                                             new UnverifiedClickedListener(),
                                             new UnverifiedDismissedListener());
        } else if (unverifiedBannerView.resolved()) {
          Log.w(TAG, "Clearing banner...");
          unverifiedBannerView.get().hide();
        }

        titleView.setVerified(isSecureText && identityRecords.isVerified());

        future.set(true);
      }

    }.execute(recipients);

    return future;
  }

  private void initializeViews() {
    titleView             = (ConversationTitleView) getSupportActionBar().getCustomView();
    buttonToggle          = ViewUtil.findById(this, R.id.button_toggle);
    sendButton            = ViewUtil.findById(this, R.id.send_button);
    attachButton          = ViewUtil.findById(this, R.id.attach_button);
    composeText           = ViewUtil.findById(this, R.id.embedded_text_editor);
    charactersLeft        = ViewUtil.findById(this, R.id.space_left);
    emojiDrawerStub       = ViewUtil.findStubById(this, R.id.emoji_drawer_stub);
    unblockButton         = ViewUtil.findById(this, R.id.unblock_button);
    makeDefaultSmsButton  = ViewUtil.findById(this, R.id.make_default_sms_button);
    composePanel          = ViewUtil.findById(this, R.id.bottom_panel);
    container             = ViewUtil.findById(this, R.id.layout_container);
    reminderView          = ViewUtil.findStubById(this, R.id.reminder_stub);
    unverifiedBannerView  = ViewUtil.findStubById(this, R.id.unverified_banner_stub);
    quickAttachmentDrawer = ViewUtil.findById(this, R.id.quick_attachment_drawer);
    quickAttachmentToggle = ViewUtil.findById(this, R.id.quick_attachment_toggle);
    inputPanel            = ViewUtil.findById(this, R.id.bottom_panel);
      // Elham code starts

      verifiedStatusButton = ViewUtil.findById(this, R.id.verified_status);
     // verifiedStatusButton.setBackgroundColor(Color.RED);



    verifiedStatusButton.setOnClickListener(
              new OnClickListener() {
                  @Override
                  public void onClick(View v) {


                    IdentityUtil.getRemoteIdentityKey(ConversationActivity.this, recipients.getPrimaryRecipient()).addListener(new ListenableFuture.Listener<Optional<IdentityDatabase.IdentityRecord>>() {

                      @Override
                      public void onSuccess(Optional<IdentityDatabase.IdentityRecord> result) {
                        if (result.isPresent()) {
                          if (result.get().getVerifiedStatus() == IdentityDatabase.VerifiedStatus.DEFAULT) {
                                // Finger print is not verified yet
                            DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                              @Override
                              public void onClick(DialogInterface dialog, int which) {
                                switch (which) {
                                  case DialogInterface.BUTTON_POSITIVE:
                                    //Yes button clicked
                                    IdentityUtil.getRemoteIdentityKey(ConversationActivity.this, recipients.getPrimaryRecipient()).addListener(new ListenableFuture.Listener<Optional<IdentityDatabase.IdentityRecord>>() {
                                      @Override
                                      public void onSuccess(Optional<IdentityDatabase.IdentityRecord> result) {
                                        if (result.isPresent()) {
                                          Intent intent = new Intent(ConversationActivity.this, VerifyIdentityActivity.class);
                                          intent.putExtra(VerifyIdentityActivity.ADDRESS_EXTRA, recipients.getPrimaryRecipient().getAddress());
                                          intent.putExtra(VerifyIdentityActivity.IDENTITY_EXTRA, new IdentityKeyParcelable(result.get().getIdentityKey()));
                                          intent.putExtra(VerifyIdentityActivity.VERIFIED_EXTRA, result.get().getVerifiedStatus() == IdentityDatabase.VerifiedStatus.VERIFIED);

                                          startActivity(intent);
                                        }
                                      }

                                      @Override
                                      public void onFailure(ExecutionException e) {
                                        Log.w(TAG, e);
                                      }
                                    });
                                    //  Toast.makeText(ConversationActivity.this, "Your toast message", Toast.LENGTH_LONG).show();


                                    break;


                                  case DialogInterface.BUTTON_NEGATIVE:
                                    //No button clicked
                                    Intent intent = new Intent(ConversationActivity.this, WebRtcCallService.class);
                                    DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                                      @Override
                                      public void onClick(DialogInterface dialog, int which) {
                                        switch (which) {
                                          case DialogInterface.BUTTON_POSITIVE:
                                            //Yes button clicked

                                            break;


                                          case DialogInterface.BUTTON_NEGATIVE:
                                            //call button clicked
                                            Intent intent = new Intent(ConversationActivity.this, WebRtcCallService.class);
                                            intent.setAction(WebRtcCallService.ACTION_OUTGOING_CALL);
                                            intent.putExtra(WebRtcCallService.EXTRA_REMOTE_ADDRESS, recipients.getPrimaryRecipient().getAddress());
                                            ConversationActivity.this.startService(intent);

                                            Intent activityIntent = new Intent(ConversationActivity.this, WebRtcCallActivity.class);
                                            activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                                           startActivity(activityIntent);

                                            break;


                                        }
                                      }
                                    };
                                    AlertDialog.Builder builder = new AlertDialog.Builder(ConversationActivity.this);
                                    builder.setMessage("Your safety number is visible during the call. Verify that your contact's safety number and yours are identical.").setPositiveButton("Cancel", dialogClickListener)
                                            .setNegativeButton("Free Signal Call", dialogClickListener).setTitle("Verify Safety Number").show();


                                    break;


                                }
                              }
                            };

                            AlertDialog.Builder builder = new AlertDialog.Builder(ConversationActivity.this);
                            builder.setMessage("Signal needs additional information from your contact's device to secure your conversations. Would you rather do it in person or over a phone call?").setPositiveButton("In person", dialogClickListener)
                                    .setNegativeButton("Over a phone call", dialogClickListener).setTitle("Verification Needed").show();


                          }
                          else{
                            DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                              @Override
                              public void onClick(DialogInterface dialog, int which) {
                                switch (which) {
                                  case DialogInterface.BUTTON_POSITIVE:
                                    //Yes button clicked

/*
                                    //Toast.makeText(ConversationActivity.this, "Your toast message", Toast.LENGTH_LONG).show();
                                    Log.w(TAG, "Remove identity: " + this.recipient.getAddress());
 */                              IdentityUtil.getRemoteIdentityKey(ConversationActivity.this, recipients.getPrimaryRecipient()).addListener(new ListenableFuture.Listener<Optional<IdentityDatabase.IdentityRecord>>() {
                                    @Override
                                    public void onSuccess(Optional<IdentityDatabase.IdentityRecord> result) {
                                      if (result.isPresent()) {
                                        Log.w(TAG, "Saving identity QR code scan data: " + recipients.getPrimaryRecipient().getAddress());
                                        DatabaseFactory.getIdentityDatabase(ConversationActivity.this)
                                                .setVerified(recipients.getPrimaryRecipient().getAddress(),
                                                        result.get().getIdentityKey(),
                                                        IdentityDatabase.VerifiedStatus.DEFAULT);

                                      }
                                      verifiedStatusButton.setText("Action Needed. Click to verify your safety number.");
                                      verifiedStatusButton.setAllCaps(false);
                                      verifiedStatusButton.setTextColor(Color.WHITE);
                                      verifiedStatusButton.setBackgroundColor(Color.RED);
                                      verifiedStatusButton.setPadding(0, 10, 0, 0);
                                      verifiedStatusButton.setCompoundDrawablesWithIntrinsicBounds(
                                              R.drawable.white_warning, 0, 0, 0);

                                    }

                                    @Override
                                    public void onFailure(ExecutionException e) {
                                      Log.w(TAG, e);
                                    }
                                  });
                                    //   Toast.makeText(getContext(), "Your toast message", Toast.LENGTH_LONG).show();

                                    break;


                                  case DialogInterface.BUTTON_NEGATIVE:
                                    //No button clicked
                                    break;


                                }
                              }
                            };
                            AlertDialog.Builder builder = new AlertDialog.Builder(ConversationActivity.this);
                            builder.setMessage("You already verified this contact, do you want to clear the verification?").setPositiveButton("Clear Verification", dialogClickListener)
                                    .setNegativeButton("Cancel", dialogClickListener).show();
                          }
                        }
                        else{

                          AlertDialog.Builder builder = new AlertDialog.Builder(ConversationActivity.this);
                          builder.setMessage("You need to send at least one message to your contact first!")
                                  .setCancelable(false)
                                  .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                      //do things
                                      dialog.cancel();
                                    }
                                  });
                          AlertDialog alert = builder.create();
                          alert.show();
                                 }
                      }
                      @Override
                      public void onFailure(ExecutionException e) {
                        Log.w(TAG, e);
                      }
                    });





// Elham edits sends
                  }
              });


      ImageButton quickCameraToggle = ViewUtil.findById(this, R.id.quick_camera_toggle);
    View        composeBubble     = ViewUtil.findById(this, R.id.compose_bubble);

    container.addOnKeyboardShownListener(this);
    inputPanel.setListener(this);
    inputPanel.setMediaListener(this);

    int[]      attributes   = new int[]{R.attr.conversation_item_bubble_background};
    TypedArray colors       = obtainStyledAttributes(attributes);
    int        defaultColor = colors.getColor(0, Color.WHITE);
    composeBubble.getBackground().setColorFilter(defaultColor, PorterDuff.Mode.MULTIPLY);
    colors.recycle();

    attachmentTypeSelector = null;
    attachmentManager      = new AttachmentManager(this, this);
    audioRecorder          = new AudioRecorder(this, masterSecret);

    SendButtonListener        sendButtonListener        = new SendButtonListener();
    ComposeKeyPressedListener composeKeyPressedListener = new ComposeKeyPressedListener();

    composeText.setOnEditorActionListener(sendButtonListener);
    attachButton.setOnClickListener(new AttachButtonListener());
    attachButton.setOnLongClickListener(new AttachButtonLongClickListener());
    sendButton.setOnClickListener(sendButtonListener);
    sendButton.setEnabled(true);
    sendButton.addOnTransportChangedListener(new TransportOptions.OnTransportChangedListener() {
      @Override
      public void onChange(TransportOption newTransport, boolean manuallySelected) {
        calculateCharactersRemaining();
        composeText.setTransport(newTransport);
        buttonToggle.getBackground().setColorFilter(newTransport.getBackgroundColor(), Mode.MULTIPLY);
        buttonToggle.getBackground().invalidateSelf();
        if (manuallySelected) recordSubscriptionIdPreference(newTransport.getSimSubscriptionId());
      }
    });

    titleView.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        Intent intent = new Intent(ConversationActivity.this, RecipientPreferenceActivity.class);
        intent.putExtra(RecipientPreferenceActivity.ADDRESSES_EXTRA, recipients.getAddresses());
        intent.putExtra(RecipientPreferenceActivity.CAN_HAVE_SAFETY_NUMBER_EXTRA,
                        isSecureText && !isSelfConversation());

        startActivitySceneTransition(intent, titleView.findViewById(R.id.title), "recipient_name");
      }
    });

    unblockButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        handleUnblock();
      }
    });

    makeDefaultSmsButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        handleMakeDefaultSms();
      }
    });

    composeText.setOnKeyListener(composeKeyPressedListener);
    composeText.addTextChangedListener(composeKeyPressedListener);
    composeText.setOnEditorActionListener(sendButtonListener);
    composeText.setOnClickListener(composeKeyPressedListener);
    composeText.setOnFocusChangeListener(composeKeyPressedListener);

    if (QuickAttachmentDrawer.isDeviceSupported(this)) {
      quickAttachmentDrawer.setListener(this);
      quickCameraToggle.setOnClickListener(new QuickCameraToggleListener());
    } else {
      quickCameraToggle.setVisibility(View.GONE);
      quickCameraToggle.setEnabled(false);
    }
  }

  protected void initializeActionBar() {
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setCustomView(R.layout.conversation_title_view);
    getSupportActionBar().setDisplayShowCustomEnabled(true);
    getSupportActionBar().setDisplayShowTitleEnabled(false);
  }

  private void initializeResources() {
    if (recipients != null) recipients.removeListener(this);

    recipients       = RecipientFactory.getRecipientsFor(this, Address.fromParcelable(getIntent().getParcelableArrayExtra(ADDRESSES_EXTRA)), true);
    threadId         = getIntent().getLongExtra(THREAD_ID_EXTRA, -1);
    archived         = getIntent().getBooleanExtra(IS_ARCHIVED_EXTRA, false);
    distributionType = getIntent().getIntExtra(DISTRIBUTION_TYPE_EXTRA, ThreadDatabase.DistributionTypes.DEFAULT);

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
      LinearLayout conversationContainer = ViewUtil.findById(this, R.id.conversation_container);
      conversationContainer.setClipChildren(true);
      conversationContainer.setClipToPadding(true);
    }

    recipients.addListener(this);
  }

  private void initializeProfiles() {
    if (!isSecureText) {
      Log.w(TAG, "SMS contact, no profile fetch needed.");
      return;
    }

    ApplicationContext.getInstance(this)
                      .getJobManager()
                      .add(new RetrieveProfileJob(this, recipients));
  }

  @Override
  public void onModified(final Recipients recipients) {
    titleView.post(new Runnable() {
      @Override
      public void run() {
        titleView.setTitle(recipients);
        titleView.setVerified(identityRecords.isVerified());
        setBlockedUserState(recipients, isSecureText, isDefaultSms);
        setActionBarColor(recipients.getColor());
        invalidateOptionsMenu();
        updateRecipientPreferences();
      }
    });
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onRecipientPreferenceUpdate(final RecipientPreferenceDatabase.RecipientPreferenceEvent event) {
    if (Arrays.equals(event.getRecipients().getAddresses(), this.recipients.getAddresses())) {
      new RecipientPreferencesTask().execute(this.recipients);
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onIdentityRecordUpdate(final IdentityDatabase.IdentityRecord event) {
    initializeIdentityRecords();
  }

  private void initializeReceivers() {
    securityUpdateReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        initializeSecurity(isSecureText, isDefaultSms);
        calculateCharactersRemaining();
      }
    };

    recipientsStaleReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        Log.w(TAG, "Group update received...");
        if (recipients != null) {
          Log.w(TAG, "Looking up new recipients...");
          recipients = RecipientFactory.getRecipientsFor(context, recipients.getAddresses(), true);
          recipients.addListener(ConversationActivity.this);
          onModified(recipients);
          fragment.reloadList();
        }
      }
    };

    IntentFilter staleFilter = new IntentFilter();
    staleFilter.addAction(GroupDatabase.DATABASE_UPDATE_ACTION);
    staleFilter.addAction(RecipientFactory.RECIPIENT_CLEAR_ACTION);

    registerReceiver(securityUpdateReceiver,
                     new IntentFilter(SecurityEvent.SECURITY_UPDATE_EVENT),
                     KeyCachingService.KEY_PERMISSION, null);

    registerReceiver(recipientsStaleReceiver, staleFilter);
  }

  //////// Helper Methods

  private void addAttachment(int type) {
    Log.w("ComposeMessageActivity", "Selected: " + type);
    switch (type) {
    case AttachmentTypeSelector.ADD_GALLERY:
      AttachmentManager.selectGallery(this, PICK_GALLERY); break;
    case AttachmentTypeSelector.ADD_DOCUMENT:
      AttachmentManager.selectDocument(this, PICK_DOCUMENT); break;
    case AttachmentTypeSelector.ADD_SOUND:
      AttachmentManager.selectAudio(this, PICK_AUDIO); break;
    case AttachmentTypeSelector.ADD_CONTACT_INFO:
      AttachmentManager.selectContactInfo(this, PICK_CONTACT_INFO); break;
    case AttachmentTypeSelector.ADD_LOCATION:
      AttachmentManager.selectLocation(this, PICK_LOCATION); break;
    case AttachmentTypeSelector.TAKE_PHOTO:
      attachmentManager.capturePhoto(this, TAKE_PHOTO); break;
    case AttachmentTypeSelector.ADD_GIF:
      AttachmentManager.selectGif(this, PICK_GIF, !isSecureText); break;
    }
  }

  private void setMedia(@Nullable Uri uri, @NonNull AttachmentManager.MediaType mediaType) {
    if (uri == null) return;
    attachmentManager.setMedia(masterSecret, uri, mediaType, getCurrentMediaConstraints());
  }

  private void addAttachmentContactInfo(Uri contactUri) {
    ContactAccessor contactDataList = ContactAccessor.getInstance();
    ContactAccessor.ContactData contactData = contactDataList.getContactData(this, contactUri);

    if      (contactData.numbers.size() == 1) composeText.append(contactData.numbers.get(0).number);
    else if (contactData.numbers.size() > 1)  selectContactInfo(contactData);
  }

  private void selectContactInfo(ContactAccessor.ContactData contactData) {
    final CharSequence[] numbers     = new CharSequence[contactData.numbers.size()];
    final CharSequence[] numberItems = new CharSequence[contactData.numbers.size()];

    for (int i = 0; i < contactData.numbers.size(); i++) {
      numbers[i]     = contactData.numbers.get(i).number;
      numberItems[i] = contactData.numbers.get(i).type + ": " + contactData.numbers.get(i).number;
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setIconAttribute(R.attr.conversation_attach_contact_info);
    builder.setTitle(R.string.ConversationActivity_select_contact_info);

    builder.setItems(numberItems, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        composeText.append(numbers[which]);
      }
    });
    builder.show();
  }

  private DraftDatabase.Drafts getDraftsForCurrentState() {
    DraftDatabase.Drafts drafts = new DraftDatabase.Drafts();

    if (!Util.isEmpty(composeText)) {
      drafts.add(new DraftDatabase.Draft(DraftDatabase.Draft.TEXT, composeText.getTextTrimmed()));
    }

    for (Slide slide : attachmentManager.buildSlideDeck().getSlides()) {
      if      (slide.hasAudio() && slide.getUri() != null)    drafts.add(new DraftDatabase.Draft(DraftDatabase.Draft.AUDIO, slide.getUri().toString()));
      else if (slide.hasVideo() && slide.getUri() != null)    drafts.add(new DraftDatabase.Draft(DraftDatabase.Draft.VIDEO, slide.getUri().toString()));
      else if (slide.hasLocation())                           drafts.add(new DraftDatabase.Draft(DraftDatabase.Draft.LOCATION, ((LocationSlide)slide).getPlace().serialize()));
      else if (slide.hasImage() && slide.getUri() != null)    drafts.add(new DraftDatabase.Draft(DraftDatabase.Draft.IMAGE, slide.getUri().toString()));
    }

    return drafts;
  }

  protected ListenableFuture<Long> saveDraft() {
    final SettableFuture<Long> future = new SettableFuture<>();

    if (this.recipients == null || this.recipients.isEmpty()) {
      future.set(threadId);
      return future;
    }

    final DraftDatabase.Drafts drafts               = getDraftsForCurrentState();
    final long         thisThreadId         = this.threadId;
    final MasterSecret thisMasterSecret     = this.masterSecret.parcelClone();
    final int          thisDistributionType = this.distributionType;

    new AsyncTask<Long, Void, Long>() {
      @Override
      protected Long doInBackground(Long... params) {
        ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(ConversationActivity.this);
        DraftDatabase  draftDatabase  = DatabaseFactory.getDraftDatabase(ConversationActivity.this);
        long           threadId       = params[0];

        if (drafts.size() > 0) {
          if (threadId == -1) threadId = threadDatabase.getThreadIdFor(getRecipients(), thisDistributionType);

          draftDatabase.insertDrafts(new MasterCipher(thisMasterSecret), threadId, drafts);
          threadDatabase.updateSnippet(threadId, drafts.getSnippet(ConversationActivity.this),
                                       drafts.getUriSnippet(ConversationActivity.this),
                                       System.currentTimeMillis(), MmsSmsColumns.Types.BASE_DRAFT_TYPE, true);
        } else if (threadId > 0) {
          threadDatabase.update(threadId, false);
        }

        return threadId;
      }

      @Override
      protected void onPostExecute(Long result) {
        future.set(result);
      }

    }.execute(thisThreadId);

    return future;
  }

  private void setActionBarColor(MaterialColor color) {
    getSupportActionBar().setBackgroundDrawable(new ColorDrawable(color.toActionBarColor(this)));
    setStatusBarColor(color.toStatusBarColor(this));
  }

  private void setBlockedUserState(Recipients recipients, boolean isSecureText, boolean isDefaultSms) {
    if (recipients.isBlocked()) {
      unblockButton.setVisibility(View.VISIBLE);
      composePanel.setVisibility(View.GONE);
      makeDefaultSmsButton.setVisibility(View.GONE);
    } else if (!isSecureText && !isDefaultSms) {
      unblockButton.setVisibility(View.GONE);
      composePanel.setVisibility(View.GONE);
      makeDefaultSmsButton.setVisibility(View.VISIBLE);
    } else {
      composePanel.setVisibility(View.VISIBLE);
      unblockButton.setVisibility(View.GONE);
      makeDefaultSmsButton.setVisibility(View.GONE);
    }
  }

  private void calculateCharactersRemaining() {
    String          messageBody     = composeText.getTextTrimmed();
    TransportOption transportOption = sendButton.getSelectedTransport();
    CharacterCalculator.CharacterState characterState  = transportOption.calculateCharacters(messageBody);

    if (characterState.charactersRemaining <= 15 || characterState.messagesSpent > 1) {
      charactersLeft.setText(characterState.charactersRemaining + "/" + characterState.maxMessageSize
                                 + " (" + characterState.messagesSpent + ")");
      charactersLeft.setVisibility(View.VISIBLE);
    } else {
      charactersLeft.setVisibility(View.GONE);
    }
  }

  private boolean isSingleConversation() {
    return getRecipients() != null && getRecipients().isSingleRecipient() && !getRecipients().isGroupRecipient();
  }

  private boolean isActiveGroup() {
    if (!isGroupConversation()) return false;

    try {
      byte[]      groupId = GroupUtil.getDecodedId(getRecipients().getPrimaryRecipient().getAddress().toGroupString());
      GroupDatabase.GroupRecord record  = DatabaseFactory.getGroupDatabase(this).getGroup(groupId);

      return record != null && record.isActive();
    } catch (IOException e) {
      Log.w("ConversationActivity", e);
      return false;
    }
  }

  private boolean isSelfConversation() {
    if (!TextSecurePreferences.isPushRegistered(this))       return false;
    if (!recipients.isSingleRecipient())                     return false;
    if (recipients.getPrimaryRecipient().isGroupRecipient()) return false;

    return Util.isOwnNumber(this, recipients.getPrimaryRecipient().getAddress());
  }

  private boolean isGroupConversation() {
    return getRecipients() != null &&
        (!getRecipients().isSingleRecipient() || getRecipients().isGroupRecipient());
  }

  private boolean isPushGroupConversation() {
    return getRecipients() != null && getRecipients().isGroupRecipient();
  }

  protected Recipients getRecipients() {
    return this.recipients;
  }

  protected long getThreadId() {
    return this.threadId;
  }

  private String getMessage() throws InvalidMessageException {
    String rawText = composeText.getTextTrimmed();

    if (rawText.length() < 1 && !attachmentManager.isAttachmentPresent())
      throw new InvalidMessageException(getString(R.string.ConversationActivity_message_is_empty_exclamation));

    return rawText;
  }

  private MediaConstraints getCurrentMediaConstraints() {
    return sendButton.getSelectedTransport().getType() == TransportOption.Type.TEXTSECURE
           ? MediaConstraints.getPushMediaConstraints()
           : MediaConstraints.getMmsMediaConstraints(sendButton.getSelectedTransport().getSimSubscriptionId().or(-1));
  }

  private void markThreadAsRead() {
    new AsyncTask<Long, Void, Void>() {
      @Override
      protected Void doInBackground(Long... params) {
        Context                 context    = ConversationActivity.this;
        List<MessagingDatabase.MarkedMessageInfo> messageIds = DatabaseFactory.getThreadDatabase(context).setRead(params[0], false);

        MessageNotifier.updateNotification(context, masterSecret);
        MarkReadReceiver.process(context, messageIds);

        return null;
      }
    }.execute(threadId);
  }

  private void markLastSeen() {
    new AsyncTask<Long, Void, Void>() {
      @Override
      protected Void doInBackground(Long... params) {
        DatabaseFactory.getThreadDatabase(ConversationActivity.this).setLastSeen(params[0]);
        return null;
      }
    }.execute(threadId);
  }

  protected void sendComplete(long threadId) {
    boolean refreshFragment = (threadId != this.threadId);
    this.threadId = threadId;

    if (fragment == null || !fragment.isVisible() || isFinishing()) {
      return;
    }

    fragment.setLastSeen(0);

    if (refreshFragment) {
      fragment.reload(recipients, threadId);
      MessageNotifier.setVisibleThread(threadId);
    }

    fragment.scrollToBottom();
    attachmentManager.cleanup();
  }

  private void sendMessage() {
    try {
      Recipients recipients = getRecipients();

      if (recipients == null) {
        throw new RecipientFormattingException("Badly formatted");
      }

      boolean    forceSms       = sendButton.isManualSelection() && sendButton.getSelectedTransport().isSms();
      int        subscriptionId = sendButton.getSelectedTransport().getSimSubscriptionId().or(-1);
      long       expiresIn      = recipients.getExpireMessages() * 1000;

      Log.w(TAG, "isManual Selection: " + sendButton.isManualSelection());
      Log.w(TAG, "forceSms: " + forceSms);

      if ((!recipients.isSingleRecipient() || recipients.isEmailRecipient()) && !isMmsEnabled) {
        handleManualMmsRequired();
      } else if (!forceSms && identityRecords.isUnverified()) {
        handleUnverifiedRecipients();
      } else if (!forceSms && identityRecords.isUntrusted()) {
        handleUntrustedRecipients();
      } else if (attachmentManager.isAttachmentPresent() || !recipients.isSingleRecipient() || recipients.isGroupRecipient() || recipients.isEmailRecipient()) {
        sendMediaMessage(forceSms, expiresIn, subscriptionId);
      } else {
        sendTextMessage(forceSms, expiresIn, subscriptionId);
      }
    } catch (RecipientFormattingException ex) {
      Toast.makeText(ConversationActivity.this,
                     R.string.ConversationActivity_recipient_is_not_a_valid_sms_or_email_address_exclamation,
                     Toast.LENGTH_LONG).show();
      Log.w(TAG, ex);
    } catch (InvalidMessageException ex) {
      Toast.makeText(ConversationActivity.this, R.string.ConversationActivity_message_is_empty_exclamation,
                     Toast.LENGTH_SHORT).show();
      Log.w(TAG, ex);
    }
  }

  private void sendMediaMessage(final boolean forceSms, final long expiresIn, final int subscriptionId)
      throws InvalidMessageException
  {
    sendMediaMessage(forceSms, getMessage(), attachmentManager.buildSlideDeck(), expiresIn, subscriptionId);
  }

  private ListenableFuture<Void> sendMediaMessage(final boolean forceSms, String body, SlideDeck slideDeck, final long expiresIn, final int subscriptionId)
      throws InvalidMessageException
  {
    final SettableFuture<Void> future          = new SettableFuture<>();
    final Context              context         = getApplicationContext();
          OutgoingMediaMessage outgoingMessage = new OutgoingMediaMessage(recipients,
                                                                          slideDeck,
                                                                          body,
                                                                          System.currentTimeMillis(),
                                                                          subscriptionId,
                                                                          expiresIn,
                                                                          distributionType);

    if (isSecureText && !forceSms) {
      outgoingMessage = new OutgoingSecureMediaMessage(outgoingMessage);
    }

    attachmentManager.clear(false);
    composeText.setText("");
    final long id = fragment.stageOutgoingMessage(outgoingMessage);

    new AsyncTask<OutgoingMediaMessage, Void, Long>() {
      @Override
      protected Long doInBackground(OutgoingMediaMessage... messages) {
        return MessageSender.send(context, masterSecret, messages[0], threadId, forceSms, new SmsDatabase.InsertListener() {
          @Override
          public void onComplete() {
            fragment.releaseOutgoingMessage(id);
          }
        });
      }

      @Override
      protected void onPostExecute(Long result) {
        sendComplete(result);
        future.set(null);
      }
    }.execute(outgoingMessage);

    return future;
  }

  private void sendTextMessage(final boolean forceSms, final long expiresIn, final int subscriptionId)
      throws InvalidMessageException
  {
    final Context context = getApplicationContext();
    OutgoingTextMessage message;

    if (isSecureText && !forceSms) {
      message = new OutgoingEncryptedMessage(recipients, getMessage(), expiresIn);
    } else {
      message = new OutgoingTextMessage(recipients, getMessage(), expiresIn, subscriptionId);
    }

    this.composeText.setText("");
    final long id = fragment.stageOutgoingMessage(message);

    new AsyncTask<OutgoingTextMessage, Void, Long>() {
      @Override
      protected Long doInBackground(OutgoingTextMessage... messages) {
        return MessageSender.send(context, masterSecret, messages[0], threadId, forceSms, new SmsDatabase.InsertListener() {
          @Override
          public void onComplete() {
            fragment.releaseOutgoingMessage(id);
          }
        });
      }

      @Override
      protected void onPostExecute(Long result) {
        sendComplete(result);
      }
    }.execute(message);
  }

  private void updateToggleButtonState() {
    if (composeText.getTextTrimmed().length() == 0 && !attachmentManager.isAttachmentPresent()) {
      buttonToggle.display(attachButton);
      quickAttachmentToggle.show();
    } else {
      buttonToggle.display(sendButton);
      quickAttachmentToggle.hide();
    }
  }

  private void recordSubscriptionIdPreference(final Optional<Integer> subscriptionId) {
    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        DatabaseFactory.getRecipientPreferenceDatabase(ConversationActivity.this)
                       .setDefaultSubscriptionId(recipients, subscriptionId.or(-1));
        return null;
      }
    }.execute();
  }

  @Override
  public void onAttachmentDrawerStateChanged(QuickAttachmentDrawer.DrawerState drawerState) {
    if (drawerState == QuickAttachmentDrawer.DrawerState.FULL_EXPANDED) {
      getSupportActionBar().hide();
    } else {
      getSupportActionBar().show();
    }

    if (drawerState == QuickAttachmentDrawer.DrawerState.COLLAPSED) {
      container.hideAttachedInput(true);
    }
  }

  @Override
  public void onImageCapture(@NonNull final byte[] imageBytes) {
    setMedia(PersistentBlobProvider.getInstance(this)
                                   .create(masterSecret, imageBytes, MediaUtil.IMAGE_JPEG, null),
             AttachmentManager.MediaType.IMAGE);
    quickAttachmentDrawer.hide(false);
  }

  @Override
  public void onCameraFail() {
    Toast.makeText(this, R.string.ConversationActivity_quick_camera_unavailable, Toast.LENGTH_SHORT).show();
    quickAttachmentDrawer.hide(false);
    quickAttachmentToggle.disable();
  }

  @Override
  public void onCameraStart() {}

  @Override
  public void onCameraStop() {}

  @Override
  public void onRecorderStarted() {
    Vibrator vibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
    vibrator.vibrate(20);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    audioRecorder.startRecording();
  }

  @Override
  public void onRecorderFinished() {
    Vibrator vibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
    vibrator.vibrate(20);
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    ListenableFuture<Pair<Uri, Long>> future = audioRecorder.stopRecording();
    future.addListener(new ListenableFuture.Listener<Pair<Uri, Long>>() {
      @Override
      public void onSuccess(final @NonNull Pair<Uri, Long> result) {
        try {
          boolean    forceSms       = sendButton.isManualSelection() && sendButton.getSelectedTransport().isSms();
          int        subscriptionId = sendButton.getSelectedTransport().getSimSubscriptionId().or(-1);
          long       expiresIn      = recipients.getExpireMessages() * 1000;
          AudioSlide audioSlide     = new AudioSlide(ConversationActivity.this, result.first, result.second, MediaUtil.AUDIO_AAC, true);
          SlideDeck  slideDeck      = new SlideDeck();
          slideDeck.addSlide(audioSlide);

          sendMediaMessage(forceSms, "", slideDeck, expiresIn, subscriptionId).addListener(new AssertedSuccessListener<Void>() {
            @Override
            public void onSuccess(Void nothing) {
              new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                  PersistentBlobProvider.getInstance(ConversationActivity.this).delete(result.first);
                  return null;
                }
              }.execute();
            }
          });
        } catch (InvalidMessageException e) {
          Log.w(TAG, e);
          Toast.makeText(ConversationActivity.this, R.string.ConversationActivity_error_sending_voice_message, Toast.LENGTH_LONG).show();
        }
      }

      @Override
      public void onFailure(ExecutionException e) {
        Toast.makeText(ConversationActivity.this, R.string.ConversationActivity_unable_to_record_audio, Toast.LENGTH_LONG).show();
      }
    });
  }

  @Override
  public void onRecorderCanceled() {
    Vibrator vibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
    vibrator.vibrate(50);
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    ListenableFuture<Pair<Uri, Long>> future = audioRecorder.stopRecording();
    future.addListener(new ListenableFuture.Listener<Pair<Uri, Long>>() {
      @Override
      public void onSuccess(final Pair<Uri, Long> result) {
        new AsyncTask<Void, Void, Void>() {
          @Override
          protected Void doInBackground(Void... params) {
            PersistentBlobProvider.getInstance(ConversationActivity.this).delete(result.first);
            return null;
          }
        }.execute();
      }

      @Override
      public void onFailure(ExecutionException e) {}
    });
  }

  @Override
  public void onEmojiToggle() {
    if (!emojiDrawerStub.resolved()) {
      inputPanel.setEmojiDrawer(emojiDrawerStub.get());
      emojiDrawerStub.get().setEmojiEventListener(inputPanel);
    }

    if (container.getCurrentInput() == emojiDrawerStub.get()) {
      container.showSoftkey(composeText);
    } else {
      container.show(composeText, emojiDrawerStub.get());
    }
  }

  @Override
  public void onMediaSelected(@NonNull Uri uri, String contentType) {
    if (!TextUtils.isEmpty(contentType) && contentType.trim().equals("image/gif")) {
      setMedia(uri, AttachmentManager.MediaType.GIF);
    } else if (MediaUtil.isImageType(contentType)) {
      setMedia(uri, AttachmentManager.MediaType.IMAGE);
    } else if (MediaUtil.isVideoType(contentType)) {
      setMedia(uri, AttachmentManager.MediaType.VIDEO);
    } else if (MediaUtil.isAudioType(contentType)) {
      setMedia(uri, AttachmentManager.MediaType.AUDIO);
    }
  }


  // Listeners

  private class AttachmentTypeListener implements AttachmentTypeSelector.AttachmentClickedListener {
    @Override
    public void onClick(int type) {
      addAttachment(type);
    }

    @Override
    public void onQuickAttachment(Uri uri) {
      Intent intent = new Intent();
      intent.setData(uri);

      onActivityResult(PICK_GALLERY, RESULT_OK, intent);
    }
  }

  private class QuickCameraToggleListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      if (!quickAttachmentDrawer.isShowing()) {
        composeText.clearFocus();
        container.show(composeText, quickAttachmentDrawer);
      } else {
        container.hideAttachedInput(false);
      }
    }
  }

  private class SendButtonListener implements OnClickListener, TextView.OnEditorActionListener {
    @Override
    public void onClick(View v) {
      sendMessage();
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
      if (actionId == EditorInfo.IME_ACTION_SEND) {
        sendButton.performClick();
        return true;
      }
      return false;
    }
  }

  private class AttachButtonListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      handleAddAttachment();
    }
  }

  private class AttachButtonLongClickListener implements View.OnLongClickListener {
    @Override
    public boolean onLongClick(View v) {
      return sendButton.performLongClick();
    }
  }

  private class ComposeKeyPressedListener implements OnKeyListener, OnClickListener, TextWatcher, OnFocusChangeListener {

    int beforeLength;

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
      if (event.getAction() == KeyEvent.ACTION_DOWN) {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
          if (TextSecurePreferences.isEnterSendsEnabled(ConversationActivity.this)) {
            sendButton.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            sendButton.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
            return true;
          }
        }
      }
      return false;
    }

    @Override
    public void onClick(View v) {
      container.showSoftkey(composeText);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count,int after) {
      beforeLength = composeText.getTextTrimmed().length();
    }

    @Override
    public void afterTextChanged(Editable s) {
      calculateCharactersRemaining();

      if (composeText.getTextTrimmed().length() == 0 || beforeLength == 0) {
        composeText.postDelayed(new Runnable() {
          @Override
          public void run() {
            updateToggleButtonState();
          }
        }, 50);
      }
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before,int count) {}

    @Override
    public void onFocusChange(View v, boolean hasFocus) {}
  }

  @Override
  public void setThreadId(long threadId) {
    this.threadId = threadId;
  }

  @Override
  public void onAttachmentChanged() {
    handleSecurityChange(isSecureText, isDefaultSms);
    updateToggleButtonState();
  }

  private class RecipientPreferencesTask extends AsyncTask<Recipients, Void, Pair<Recipients,RecipientPreferenceDatabase.RecipientsPreferences>> {
    @Override
    protected Pair<Recipients, RecipientPreferenceDatabase.RecipientsPreferences> doInBackground(Recipients... recipients) {
      if (recipients.length != 1 || recipients[0] == null) {
        throw new AssertionError("task needs exactly one Recipients object");
      }

      Optional<RecipientPreferenceDatabase.RecipientsPreferences> prefs = DatabaseFactory.getRecipientPreferenceDatabase(ConversationActivity.this)
                                                             .getRecipientsPreferences(recipients[0].getAddresses());
      return new Pair<>(recipients[0], prefs.orNull());
    }

    @Override
    protected void onPostExecute(@NonNull  Pair<Recipients, RecipientPreferenceDatabase.RecipientsPreferences> result) {
      if (result.first == recipients) {
        updateInviteReminder(result.second != null && result.second.hasSeenInviteReminder());
        updateDefaultSubscriptionId(result.second != null ? result.second.getDefaultSubscriptionId() : Optional.<Integer>absent());
      }
    }
  }

  private class UnverifiedDismissedListener implements UnverifiedBannerView.DismissListener {
    @Override
    public void onDismissed(final List<IdentityDatabase.IdentityRecord> unverifiedIdentities) {
      final IdentityDatabase identityDatabase = DatabaseFactory.getIdentityDatabase(ConversationActivity.this);

      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          synchronized (SESSION_LOCK) {
            for (IdentityDatabase.IdentityRecord identityRecord : unverifiedIdentities) {
              identityDatabase.setVerified(identityRecord.getAddress(),
                                           identityRecord.getIdentityKey(),
                                           IdentityDatabase.VerifiedStatus.DEFAULT);
            }
          }

          return null;
        }

        @Override
        protected void onPostExecute(Void result) {
          initializeIdentityRecords();
        }
      }.execute();
    }
  }

  private class UnverifiedClickedListener implements UnverifiedBannerView.ClickListener {
    @Override
    public void onClicked(final List<IdentityDatabase.IdentityRecord> unverifiedIdentities) {
      Log.w(TAG, "onClicked: " + unverifiedIdentities.size());
      if (unverifiedIdentities.size() == 1) {
        Intent intent = new Intent(ConversationActivity.this, VerifyIdentityActivity.class);
        intent.putExtra(VerifyIdentityActivity.ADDRESS_EXTRA, unverifiedIdentities.get(0).getAddress());
        intent.putExtra(VerifyIdentityActivity.IDENTITY_EXTRA, new IdentityKeyParcelable(unverifiedIdentities.get(0).getIdentityKey()));
        intent.putExtra(VerifyIdentityActivity.VERIFIED_EXTRA, false);

        startActivity(intent);
      } else {
        String[] unverifiedNames = new String[unverifiedIdentities.size()];

        for (int i=0;i<unverifiedIdentities.size();i++) {
          unverifiedNames[i] = RecipientFactory.getRecipientFor(ConversationActivity.this, unverifiedIdentities.get(i).getAddress(), false).toShortString();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(ConversationActivity.this);
        builder.setIconAttribute(R.attr.dialog_alert_icon);
        builder.setTitle("No longer verified");
        builder.setItems(unverifiedNames, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            Intent intent = new Intent(ConversationActivity.this, VerifyIdentityActivity.class);
            intent.putExtra(VerifyIdentityActivity.ADDRESS_EXTRA, unverifiedIdentities.get(which).getAddress());
            intent.putExtra(VerifyIdentityActivity.IDENTITY_EXTRA, new IdentityKeyParcelable(unverifiedIdentities.get(which).getIdentityKey()));
            intent.putExtra(VerifyIdentityActivity.VERIFIED_EXTRA, false);

            startActivity(intent);
          }
        });
        builder.show();
      }
    }
  }
}
