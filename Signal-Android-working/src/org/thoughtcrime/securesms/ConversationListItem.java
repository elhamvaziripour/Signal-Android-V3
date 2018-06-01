/*
 * Copyright (C) 2014-2017 Open Whisper Systems
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
package org.thoughtcrime.securesms;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.IdentityUtil;
import org.thoughtcrime.securesms.util.concurrent.ListenableFuture;
import org.whispersystems.libsignal.util.guava.Optional;
import java.util.concurrent.ExecutionException;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.RippleDrawable;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import org.thoughtcrime.securesms.recipients.*;
import com.amulyakhare.textdrawable.TextDrawable;

import org.thoughtcrime.securesms.components.AlertView;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.components.DeliveryStatusView;
import org.thoughtcrime.securesms.components.FromTextView;
import org.thoughtcrime.securesms.components.ThumbnailView;
import org.thoughtcrime.securesms.crypto.IdentityKeyParcelable;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientModifiedListener;
import org.thoughtcrime.securesms.search.model.MessageResult;
import org.thoughtcrime.securesms.service.WebRtcCallService;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.IdentityUtil;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static org.thoughtcrime.securesms.util.SpanUtil.color;

public class ConversationListItem extends RelativeLayout
                                  implements RecipientModifiedListener,
                                             BindableConversationListItem, Unbindable
{
  @SuppressWarnings("unused")
  private final static String TAG = ConversationListItem.class.getSimpleName();

  private final static Typeface  BOLD_TYPEFACE  = Typeface.create("sans-serif", Typeface.BOLD);
  private final static Typeface  LIGHT_TYPEFACE = Typeface.create("sans-serif-light", Typeface.NORMAL);
  private final static StyleSpan BOLD_SPAN      = new StyleSpan(Typeface.BOLD);

  private Set<Long>          selectedThreads;
  private Recipient          recipient;
  private long               threadId;
  private GlideRequests      glideRequests;
  private TextView           subjectView;
  private FromTextView       fromView;
  private TextView           dateView;
  private TextView           archivedView;
  private DeliveryStatusView deliveryStatusIndicator;
  private AlertView          alertView;
  private ImageView          unreadIndicator;
  private long               lastSeen;
// Elham edits starts

  private TextView          verifiedStatusIndicator;
  // private ImageView         verifiedIndicator;


  // Elham edits ends
  private int             unreadCount;
  private AvatarImageView contactPhotoImage;
  private ThumbnailView   thumbnailView;

  private int distributionType;

  public ConversationListItem(Context context) {
    this(context, null);
  }

  public ConversationListItem(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    this.subjectView             = findViewById(R.id.subject);
    this.fromView                = findViewById(R.id.from);
    this.dateView                = findViewById(R.id.date);
    this.deliveryStatusIndicator = findViewById(R.id.delivery_status);
    this.alertView               = findViewById(R.id.indicators_parent);
    this.contactPhotoImage       = findViewById(R.id.contact_photo_image);
    this.thumbnailView           = findViewById(R.id.thumbnail);
    this.archivedView            = findViewById(R.id.archived);
    this.unreadIndicator         = findViewById(R.id.unread_indicator);
    thumbnailView.setClickable(false);
    // Elham Edit
    this.verifiedStatusIndicator  = (TextView)          findViewById(R.id.verify_status);
    ViewUtil.setTextViewGravityStart(this.fromView, getContext());
    ViewUtil.setTextViewGravityStart(this.subjectView, getContext());






    // Elham edits start
    //  RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)verifiedIndicator.getLayoutParams();
    //  params.setMargins(0, 0, 43, 0); //substitute parameters for left, top, right, bottom
    //  verifiedIndicator.setLayoutParams(params);

    this.verifiedStatusIndicator.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {

        if (verifiedStatusIndicator.getText() == "Verified") {

          // Revoke the verification


          DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                  //Yes button clicked

                  IdentityUtil.getRemoteIdentityKey(getContext(), recipient).addListener(new ListenableFuture.Listener<Optional<IdentityDatabase.IdentityRecord>>() {
                    @Override
                    public void onSuccess(Optional<IdentityDatabase.IdentityRecord> result) {
                      if (result.isPresent()) {
                        DatabaseFactory.getIdentityDatabase(getContext())
                                .setVerified(recipient.getAddress(),
                                        result.get().getIdentityKey(),
                                        IdentityDatabase.VerifiedStatus.DEFAULT);

                      }
                      verifiedStatusIndicator.setText("Action Needed");
                      verifiedStatusIndicator.setCompoundDrawablesWithIntrinsicBounds(
                              R.drawable.warning1, 0, 0, 0);
                      verifiedStatusIndicator.setTextColor(Color.RED);
                      //     verifiedIndicator.setImageResource(R.drawable.warning1);

                   //   contactPhotoImage.setAvatar(, recipient, true);
                      //   RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)verifiedIndicator.getLayoutParams();
                      //    params.setMargins(0, 0, 76, 0); //substitute parameters for left, top, right, bottom
                      //   verifiedIndicator.setLayoutParams(params);

                    }

                    @Override
                    public void onFailure(ExecutionException e) {
                      Log.w(TAG, e);
                    }
                  });
                  //   Toast.makeText(getContext(), "Your toast message", Toast.LENGTH_LONG).show();

                  break;


                case DialogInterface.BUTTON_NEGATIVE:
                  // Canceled
                  break;


              }
            }
          };

          AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
          builder.setMessage("You already verified this contact, do you want to clear the verification?").setPositiveButton("Clear Verification", dialogClickListener)
                  .setNegativeButton("Cancel", dialogClickListener).setTitle("Clear Verification").show();


        } else {

          DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                  //scan button clicked
                  IdentityUtil.getRemoteIdentityKey(getContext(), recipient).addListener(new ListenableFuture.Listener<Optional<IdentityDatabase.IdentityRecord>>() {
                    @Override
                    public void onSuccess(Optional<IdentityDatabase.IdentityRecord> result) {
                      if (result.isPresent()) {
                        Intent intent = new Intent(getContext(), VerifyIdentityActivity.class);
                        intent.putExtra(VerifyIdentityActivity.ADDRESS_EXTRA, recipient.getAddress());
                        intent.putExtra(VerifyIdentityActivity.IDENTITY_EXTRA, new IdentityKeyParcelable(result.get().getIdentityKey()));
                        intent.putExtra(VerifyIdentityActivity.VERIFIED_EXTRA, result.get().getVerifiedStatus() == IdentityDatabase.VerifiedStatus.VERIFIED);

                        getContext().startActivity(intent);
                      } else {

                        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
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
                  //   Toast.makeText(getContext(), "Your toast message", Toast.LENGTH_LONG).show();


                  break;


                case DialogInterface.BUTTON_NEGATIVE:
                  //call button clicked
                  IdentityUtil.getRemoteIdentityKey(getContext(), recipient).addListener(new ListenableFuture.Listener<Optional<IdentityDatabase.IdentityRecord>>() {
                    @Override
                    public void onSuccess(Optional<IdentityDatabase.IdentityRecord> result) {
                      if (result.isPresent()) {
                        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                          @Override
                          public void onClick(DialogInterface dialog, int which) {
                            switch (which) {
                              case DialogInterface.BUTTON_POSITIVE:
                                //Yes button clicked

                                break;


                              case DialogInterface.BUTTON_NEGATIVE:
                                //call button clicked
                                Intent intent = new Intent(getContext(), WebRtcCallService.class);
                                intent.setAction(WebRtcCallService.ACTION_OUTGOING_CALL);
                                intent.putExtra(WebRtcCallService.EXTRA_REMOTE_ADDRESS, recipient.getAddress());
                                getContext().startService(intent);

                                Intent activityIntent = new Intent(getContext(), WebRtcCallActivity.class);
                                activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                                getContext().startActivity(activityIntent);

                                break;


                            }
                          }
                        };
                        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                        builder.setMessage("Your safety number is visible during the call. Verify that your contact's safety number and yours are identical.").setPositiveButton("Cancel", dialogClickListener)
                                .setNegativeButton("Free Signal Call", dialogClickListener).setTitle("Verify Safety Number").show();

                      } else {

                        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
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
                  //   Toast.makeText(getContext(), "Your toast message", Toast.LENGTH_LONG).show();


                  break;


              }
            }
          };

          AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
          builder.setMessage("Signal needs additional information from your contact's device to secure your conversations. Would you rather do it in person or over a phone call?").setPositiveButton("In person", dialogClickListener)
                  .setNegativeButton("Over a phone call", dialogClickListener).setTitle("Verification Needed").show();

        }

// Elham edits ends


      }

    });
//    this.contactPhotoImage.setAvatar(recipients, true);

  }


  @Override
  public void bind(@NonNull ThreadRecord thread,
                   @NonNull GlideRequests glideRequests,
                   @NonNull Locale locale,
                   @NonNull Set<Long> selectedThreads,
                   boolean batchMode)
  {
    bind(thread, glideRequests, locale, selectedThreads, batchMode, null);
  }

  public void bind(@NonNull ThreadRecord thread,
                   @NonNull GlideRequests glideRequests,
                   @NonNull Locale locale,
                   @NonNull Set<Long> selectedThreads,
                   boolean batchMode,
                   @Nullable String highlightSubstring)
  {
    this.selectedThreads  = selectedThreads;
    this.recipient        = thread.getRecipient();
    this.threadId         = thread.getThreadId();
    this.glideRequests    = glideRequests;
    this.unreadCount      = thread.getUnreadCount();
    this.distributionType = thread.getDistributionType();
    this.lastSeen         = thread.getLastSeen();

    this.recipient.addListener(this);
    if (highlightSubstring != null) {
      this.fromView.setText(getHighlightedSpan(locale, recipient.getName(), highlightSubstring));
    } else {
      this.fromView.setText(recipient, unreadCount == 0);
    }

    this.subjectView.setText(thread.getDisplayBody());
//    this.subjectView.setTypeface(read ? LIGHT_TYPEFACE : BOLD_TYPEFACE);

    if (thread.getDate() > 0) {
      CharSequence date = DateUtils.getBriefRelativeTimeSpanString(getContext(), locale, thread.getDate());
      dateView.setText(unreadCount == 0 ? date : color(getResources().getColor(R.color.textsecure_primary_dark), date));
      dateView.setTypeface(unreadCount == 0 ? LIGHT_TYPEFACE : BOLD_TYPEFACE);
    }

    if (thread.isArchived()) {
      this.archivedView.setVisibility(View.VISIBLE);
    } else {
      this.archivedView.setVisibility(View.GONE);
    }

    setStatusIcons(thread);
    setThumbnailSnippet(thread);
    setBatchState(batchMode);
    setRippleColor(recipient);
    setUnreadIndicator(thread);
    this.contactPhotoImage.setAvatar(glideRequests, recipient, true);
  }

  public void bind(@NonNull  Recipient     contact,
                   @NonNull  GlideRequests glideRequests,
                   @NonNull  Locale        locale,
                   @Nullable String        highlightSubstring)
  {
    this.selectedThreads = Collections.emptySet();
    this.recipient       = contact;
    this.glideRequests   = glideRequests;

    this.recipient.addListener(this);

    fromView.setText(getHighlightedSpan(locale, recipient.getName(), highlightSubstring));
    subjectView.setText(contact.getAddress().toPhoneString());
    dateView.setText("");
    archivedView.setVisibility(GONE);
    unreadIndicator.setVisibility(GONE);
    deliveryStatusIndicator.setNone();
    alertView.setNone();
    thumbnailView.setVisibility(GONE);

    setBatchState(false);
    setRippleColor(contact);
    contactPhotoImage.setAvatar(glideRequests, recipient, true);
  }

  public void bind(@NonNull  MessageResult messageResult,
                   @NonNull  GlideRequests glideRequests,
                   @NonNull  Locale        locale,
                   @Nullable String        highlightSubstring)
  {
    this.selectedThreads = Collections.emptySet();
    this.recipient       = messageResult.recipient;
    this.glideRequests   = glideRequests;

    this.recipient.addListener(this);

    fromView.setText(recipient, true);
    subjectView.setText(getHighlightedSpan(locale, messageResult.bodySnippet, highlightSubstring));
    dateView.setText(DateUtils.getBriefRelativeTimeSpanString(getContext(), locale, messageResult.receivedTimestampMs));
    archivedView.setVisibility(GONE);
    unreadIndicator.setVisibility(GONE);
    deliveryStatusIndicator.setNone();
    alertView.setNone();
    thumbnailView.setVisibility(GONE);

    setBatchState(false);
    setRippleColor(recipient);
    contactPhotoImage.setAvatar(glideRequests, recipient, true);
  }

  @Override
  public void unbind() {
    if (this.recipient != null) this.recipient.removeListener(this);
  }

  private void setBatchState(boolean batch) {
    setSelected(batch && selectedThreads.contains(threadId));
  }

  public Recipient getRecipient() {
    return recipient;
  }

  public long getThreadId() {
    return threadId;
  }

  public int getUnreadCount() {
    return unreadCount;
  }

  public int getDistributionType() {
    return distributionType;
  }

  public long getLastSeen() {
    return lastSeen;
  }

  private void setThumbnailSnippet(ThreadRecord thread) {
    if (thread.getSnippetUri() != null) {
      this.thumbnailView.setVisibility(View.VISIBLE);
      this.thumbnailView.setImageResource(glideRequests, thread.getSnippetUri());

      LayoutParams subjectParams = (RelativeLayout.LayoutParams)this.subjectView.getLayoutParams();
      subjectParams.addRule(RelativeLayout.LEFT_OF, R.id.thumbnail);
      if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
        subjectParams.addRule(RelativeLayout.START_OF, R.id.thumbnail);
      }
      this.subjectView.setLayoutParams(subjectParams);
      this.post(new ThumbnailPositioner(thumbnailView, archivedView, deliveryStatusIndicator, dateView));
    } else {
      this.thumbnailView.setVisibility(View.GONE);

      LayoutParams subjectParams = (RelativeLayout.LayoutParams)this.subjectView.getLayoutParams();
      subjectParams.addRule(RelativeLayout.LEFT_OF, R.id.status);
      if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
        subjectParams.addRule(RelativeLayout.START_OF, R.id.status);
      }
      this.subjectView.setLayoutParams(subjectParams);
    }
  }

  private void setStatusIcons(ThreadRecord thread) {
    if (!thread.isOutgoing() || thread.isOutgoingCall() || thread.isVerificationStatusChange()) {
      deliveryStatusIndicator.setNone();
      alertView.setNone();
    } else if (thread.isFailed()) {
      deliveryStatusIndicator.setNone();
      alertView.setFailed();
    } else if (thread.isPendingInsecureSmsFallback()) {
      deliveryStatusIndicator.setNone();
      alertView.setPendingApproval();
    } else {
      alertView.setNone();

      if      (thread.isPending())    deliveryStatusIndicator.setPending();
      else if (thread.isRemoteRead()) deliveryStatusIndicator.setRead();
      else if (thread.isDelivered())  deliveryStatusIndicator.setDelivered();
      else                            deliveryStatusIndicator.setSent();
    }
  }

  private void setRippleColor(Recipient recipient) {
    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
      ((RippleDrawable)(getBackground()).mutate())
          .setColor(ColorStateList.valueOf(recipient.getColor().toConversationColor(getContext())));
    }
  }

  private void setUnreadIndicator(ThreadRecord thread) {
    if (thread.isOutgoing() || thread.getUnreadCount() == 0) {
      unreadIndicator.setVisibility(View.GONE);
      return;
    }

    unreadIndicator.setImageDrawable(TextDrawable.builder()
                                                 .beginConfig()
                                                 .width(ViewUtil.dpToPx(getContext(), 24))
                                                 .height(ViewUtil.dpToPx(getContext(), 24))
                                                 .textColor(Color.WHITE)
                                                 .bold()
                                                 .endConfig()
                                                 .buildRound(String.valueOf(thread.getUnreadCount()), getResources().getColor(R.color.textsecure_primary_dark)));
    unreadIndicator.setVisibility(View.VISIBLE);
  }

  private Spanned getHighlightedSpan(@NonNull Locale locale,
                                     @Nullable String value,
                                     @Nullable String highlight)
  {
    value = value != null ? value.replaceAll("\n", " ") : null;

    if (value == null || highlight == null) {
      return new SpannableString(value);
    }

    int startPosition = value.toLowerCase(locale).indexOf(highlight.toLowerCase());
    int endPosition   = startPosition + highlight.length();

    if (startPosition < 0) {
      return new SpannableString(value);
    }

    Spannable spanned = new SpannableString(value);
    spanned.setSpan(BOLD_SPAN, startPosition, endPosition, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
    return spanned;
  }

  @Override
  public void onModified(final Recipient recipient) {
    Util.runOnMain(() -> {
      fromView.setText(recipient, unreadCount == 0);
      contactPhotoImage.setAvatar(glideRequests, recipient, true);
      setRippleColor(recipient);
    });
  }

  private static class ThumbnailPositioner implements Runnable {

    private final View thumbnailView;
    private final View archivedView;
    private final View deliveryStatusView;
    private final View dateView;

    ThumbnailPositioner(View thumbnailView, View archivedView, View deliveryStatusView, View dateView) {
      this.thumbnailView      = thumbnailView;
      this.archivedView       = archivedView;
      this.deliveryStatusView = deliveryStatusView;
      this.dateView           = dateView;
    }

    @Override
    public void run() {
      LayoutParams thumbnailParams = (RelativeLayout.LayoutParams)thumbnailView.getLayoutParams();

      if (archivedView.getVisibility() == View.VISIBLE &&
          (archivedView.getWidth() + deliveryStatusView.getWidth()) > dateView.getWidth())
      {
        thumbnailParams.addRule(RelativeLayout.LEFT_OF, R.id.status);
        if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
          thumbnailParams.addRule(RelativeLayout.START_OF, R.id.status);
        }
      } else {
        thumbnailParams.addRule(RelativeLayout.LEFT_OF, R.id.date);
        if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
          thumbnailParams.addRule(RelativeLayout.START_OF, R.id.date);
        }
      }

      thumbnailView.setLayoutParams(thumbnailParams);
    }
  }

}
