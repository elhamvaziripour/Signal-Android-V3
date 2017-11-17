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

import org.BYUSecureSMS.messaging.components.AvatarImageView;
import org.BYUSecureSMS.messaging.components.ThumbnailView;
import org.BYUSecureSMS.messaging.crypto.MasterSecret;
import org.BYUSecureSMS.messaging.database.IdentityDatabase;
import org.BYUSecureSMS.messaging.util.IdentityUtil;
import org.BYUSecureSMS.messaging.util.ResUtil;
import org.BYUSecureSMS.messaging.util.SpanUtil;
import org.BYUSecureSMS.messaging.util.concurrent.ListenableFuture;
import org.BYUSecureSMS.messaging.database.DatabaseFactory;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.RippleDrawable;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.BYUSecureSMS.messaging.service.WebRtcCallService;
import org.BYUSecureSMS.messaging.components.DeliveryStatusView;
import org.BYUSecureSMS.messaging.components.AlertView;
import org.BYUSecureSMS.messaging.components.FromTextView;
import org.BYUSecureSMS.messaging.crypto.IdentityKeyParcelable;
import org.BYUSecureSMS.messaging.database.model.ThreadRecord;
import org.BYUSecureSMS.messaging.recipients.Recipients;
import org.BYUSecureSMS.messaging.util.DateUtils;
import org.BYUSecureSMS.messaging.util.ViewUtil;
import org.whispersystems.libsignal.fingerprint.Fingerprint;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * A view that displays the element in a list of multiple conversation threads.
 * Used by SecureSMS's ListActivity via a ConversationListAdapter.
 *
 * @author Moxie Marlinspike
 */

public class ConversationListItem extends RelativeLayout
                                  implements Recipients.RecipientsModifiedListener,
                                             BindableConversationListItem, Unbindable
{
  private final static String TAG = ConversationListItem.class.getSimpleName();

  private final static Typeface BOLD_TYPEFACE  = Typeface.create("sans-serif", Typeface.BOLD);
  private final static Typeface LIGHT_TYPEFACE = Typeface.create("sans-serif-light", Typeface.NORMAL);

  private Set<Long>          selectedThreads;
  private Recipients         recipients;
  private long               threadId;
  private TextView           subjectView;
  private FromTextView       fromView;
  private TextView           dateView;
  private TextView           archivedView;
  private DeliveryStatusView deliveryStatusIndicator;
  private AlertView          alertView;
  private long               lastSeen;


  // Elham edits starts

  private TextView          verifiedStatusIndicator;
 // private ImageView         verifiedIndicator;


  // Elham edits ends
  private boolean         read;
  private AvatarImageView contactPhotoImage;
  private ThumbnailView thumbnailView;

  private final @DrawableRes int readBackground;
  private final @DrawableRes int unreadBackround;

  private final Handler handler = new Handler();
  private int distributionType;

  public ConversationListItem(Context context) {
    this(context, null);
  }

  public ConversationListItem(Context context, AttributeSet attrs) {
    super(context, attrs);
    readBackground  = ResUtil.getDrawableRes(context, R.attr.conversation_list_item_background_read);
    unreadBackround = ResUtil.getDrawableRes(context, R.attr.conversation_list_item_background_unread);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    this.subjectView             = (TextView)           findViewById(R.id.subject);
    this.fromView                = (FromTextView)       findViewById(R.id.from);
    this.dateView                = (TextView)           findViewById(R.id.date);
    this.deliveryStatusIndicator = (DeliveryStatusView) findViewById(R.id.delivery_status);
    this.alertView               = (AlertView)          findViewById(R.id.indicators_parent);
    this.contactPhotoImage       = (AvatarImageView)    findViewById(R.id.contact_photo_image);
    this.thumbnailView           = (ThumbnailView)      findViewById(R.id.thumbnail);
    this.archivedView            = ViewUtil.findById(this, R.id.archived);
   // this.verifiedIndicator        = (ImageView)         findViewById(R.id.verified_indicator);
    this.verifiedStatusIndicator  = (TextView)          findViewById(R.id.verify_status);
//  this.verifiedIndicator = (ImageView) findViewById(R.id.verify_icon_status);
    thumbnailView.setClickable(false);

    ViewUtil.setTextViewGravityStart(this.fromView, getContext());
    ViewUtil.setTextViewGravityStart(this.subjectView, getContext());

    // Elham edits start
  //  RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)verifiedIndicator.getLayoutParams();
  //  params.setMargins(0, 0, 43, 0); //substitute parameters for left, top, right, bottom
  //  verifiedIndicator.setLayoutParams(params);

    this.verifiedStatusIndicator.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {

        if(verifiedStatusIndicator.getText()=="Verified"){

          // Revoke the verification


          DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                  //Yes button clicked

                  IdentityUtil.getRemoteIdentityKey(getContext(), recipients.getPrimaryRecipient()).addListener(new ListenableFuture.Listener<Optional<IdentityDatabase.IdentityRecord>>() {
                    @Override
                    public void onSuccess(Optional<IdentityDatabase.IdentityRecord> result) {
                      if (result.isPresent()) {
                        Log.w(TAG, "Saving identity QR code scan data: " + recipients.getPrimaryRecipient().getAddress());
                        DatabaseFactory.getIdentityDatabase(getContext())
                        .setVerified(recipients.getPrimaryRecipient().getAddress(),
                                        result.get().getIdentityKey(),
                                        IdentityDatabase.VerifiedStatus.DEFAULT);

                                   }
                        verifiedStatusIndicator.setText("Action Needed");
                      verifiedStatusIndicator.setCompoundDrawablesWithIntrinsicBounds(
                              R.drawable.warning1, 0, 0, 0);
                        verifiedStatusIndicator.setTextColor(Color.RED);
                   //     verifiedIndicator.setImageResource(R.drawable.warning1);
                      contactPhotoImage.setAvatar(recipients, true);
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








        }else {

          DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                  //scan button clicked
                  IdentityUtil.getRemoteIdentityKey(getContext(), recipients.getPrimaryRecipient()).addListener(new ListenableFuture.Listener<Optional<IdentityDatabase.IdentityRecord>>() {
                    @Override
                    public void onSuccess(Optional<IdentityDatabase.IdentityRecord> result) {
                      if (result.isPresent()) {
                        Intent intent = new Intent(getContext(), VerifyIdentityActivity.class);
                        intent.putExtra(VerifyIdentityActivity.ADDRESS_EXTRA, recipients.getPrimaryRecipient().getAddress());
                        intent.putExtra(VerifyIdentityActivity.IDENTITY_EXTRA, new IdentityKeyParcelable(result.get().getIdentityKey()));
                        intent.putExtra(VerifyIdentityActivity.VERIFIED_EXTRA, result.get().getVerifiedStatus() == IdentityDatabase.VerifiedStatus.VERIFIED);

                        getContext().startActivity(intent);
                      } else{

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
                  IdentityUtil.getRemoteIdentityKey(getContext(), recipients.getPrimaryRecipient()).addListener(new ListenableFuture.Listener<Optional<IdentityDatabase.IdentityRecord>>() {
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
                                intent.putExtra(WebRtcCallService.EXTRA_REMOTE_ADDRESS, recipients.getPrimaryRecipient().getAddress());
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

                      } else{

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

// Elham edits sends

      }

    });
    this.contactPhotoImage.setAvatar(recipients, true);

  }

  private String[] getSegments(Fingerprint fingerprint, int segmentCount) {
    String[] segments = new String[segmentCount];
    String   digits   = fingerprint.getDisplayableFingerprint().getDisplayText();
    int      partSize = digits.length() / segmentCount;

    for (int i=0;i<segmentCount;i++) {
      segments[i] = digits.substring(i * partSize, (i * partSize) + partSize);
    }

    return segments;
  }

  private @NonNull String getFormattedSafetyNumbers(@NonNull Fingerprint fingerprint, int segmentCount) {
    String[]      segments = getSegments(fingerprint, segmentCount);
    StringBuilder result   = new StringBuilder();

    for (int i = 0; i < segments.length; i++) {
      result.append(segments[i]);

      if (i != segments.length - 1) {
        if (((i+1) % 4) == 0) result.append('\n');
        else                  result.append(' ');
      }
    }

    return result.toString();
  }
  public void bind(@NonNull MasterSecret masterSecret, @NonNull ThreadRecord thread,
                   @NonNull Locale locale, @NonNull Set<Long> selectedThreads, boolean batchMode)
  {
    this.selectedThreads  = selectedThreads;
    this.recipients       = thread.getRecipients();
    this.threadId         = thread.getThreadId();
    this.read             = thread.isRead();
    this.distributionType = thread.getDistributionType();
    this.lastSeen         = thread.getLastSeen();

    this.recipients.addListener(this);
    this.fromView.setText(recipients, read);

    this.subjectView.setText(thread.getDisplayBody());
    this.subjectView.setTypeface(read ? LIGHT_TYPEFACE : BOLD_TYPEFACE);

    if (thread.getDate() > 0) {
      CharSequence date = DateUtils.getBriefRelativeTimeSpanString(getContext(), locale, thread.getDate());
      dateView.setText(read ? date : SpanUtil.color(getResources().getColor(R.color.textsecure_primary), date));
      dateView.setTypeface(read ? LIGHT_TYPEFACE : BOLD_TYPEFACE);
    }

    if (thread.isArchived()) {
      this.archivedView.setVisibility(View.VISIBLE);
    } else {
      this.archivedView.setVisibility(View.GONE);
    }

    setStatusIcons(thread);
    setThumbnailSnippet(masterSecret, thread);
    setBatchState(batchMode);
    setBackground(thread);
    setRippleColor(recipients);
    this.contactPhotoImage.setAvatar(recipients, true);

    // Elham edits start

    IdentityUtil.getRemoteIdentityKey(getContext(), recipients.getPrimaryRecipient()).addListener(new ListenableFuture.Listener<Optional<IdentityDatabase.IdentityRecord>>() {

      @Override
      public void onSuccess(Optional<IdentityDatabase.IdentityRecord> result) {
        if (result.isPresent()) {
          if (result.get().getVerifiedStatus() == IdentityDatabase.VerifiedStatus.DEFAULT){

            // Needs modification for the color
            verifiedStatusIndicator.setText("Action Needed");
            verifiedStatusIndicator.setTextColor(Color.RED);
         //   verifiedIndicator.setImageResource(R.drawable.warning1);
            verifiedStatusIndicator.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.warning1, 0, 0, 0);

           // verifiedIndicator.setImageResource(R.drawable.exclamation_mark);
           // RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)verifiedIndicator.getLayoutParams();
           // params.setMargins(0, 0, 76, 0); //substitute parameters for left, top, right, bottom
            //verifiedIndicator.setLayoutParams(params);

             }
          else {
            verifiedStatusIndicator.setText("Verified");

            verifiedStatusIndicator.setTextColor( getResources().getColor(R.color.textsecure_primary));
          // verifiedIndicator.setImageResource(R.drawable.verified_icon);
            verifiedStatusIndicator.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.verified_icon, 0, 0, 0);
          }
        }
      }

      @Override
      public void onFailure(ExecutionException e) {
        Log.w(TAG, e);
      }
    });

    // Elham edits ends


  }

  @Override
  public void unbind() {
    if (this.recipients != null) this.recipients.removeListener(this);
  }

  private void setBatchState(boolean batch) {
    setSelected(batch && selectedThreads.contains(threadId));
  }

  public Recipients getRecipients() {
    return recipients;
  }

  public long getThreadId() {
    return threadId;
  }

  public boolean getRead() {
    return read;
  }

  public int getDistributionType() {
    return distributionType;
  }

  public long getLastSeen() {
    return lastSeen;
  }

  private void setThumbnailSnippet(MasterSecret masterSecret, ThreadRecord thread) {
    if (thread.getSnippetUri() != null) {
      this.thumbnailView.setVisibility(View.VISIBLE);
      this.thumbnailView.setImageResource(masterSecret, thread.getSnippetUri());

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
      subjectParams.addRule(RelativeLayout.LEFT_OF, R.id.delivery_status);
      if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
        subjectParams.addRule(RelativeLayout.START_OF, R.id.delivery_status);
      }
      this.subjectView.setLayoutParams(subjectParams);
    }
  }

  private void setStatusIcons(ThreadRecord thread) {
    if (!thread.isOutgoing() || thread.isOutgoingCall()) {
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

      if      (thread.isPending())   deliveryStatusIndicator.setPending();
      else if (thread.isDelivered()) deliveryStatusIndicator.setDelivered();
      else                           deliveryStatusIndicator.setSent();
    }
  }

  private void setBackground(ThreadRecord thread) {
    if (thread.isRead()) setBackgroundResource(readBackground);
    else                 setBackgroundResource(unreadBackround);
  }

  @TargetApi(VERSION_CODES.LOLLIPOP)
  private void setRippleColor(Recipients recipients) {
    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
      ((RippleDrawable)(getBackground()).mutate())
          .setColor(ColorStateList.valueOf(recipients.getColor().toConversationColor(getContext())));
    }
  }

  @Override
  public void onModified(final Recipients recipients) {
    handler.post(new Runnable() {
      @Override
      public void run() {
        fromView.setText(recipients, read);
        contactPhotoImage.setAvatar(recipients, true);
        setRippleColor(recipients);
      }
    });
  }

  private static class ThumbnailPositioner implements Runnable {

    private final View thumbnailView;
    private final View archivedView;
    private final View deliveryStatusView;
    private final View dateView;

    public ThumbnailPositioner(View thumbnailView, View archivedView, View deliveryStatusView, View dateView) {
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
        thumbnailParams.addRule(RelativeLayout.LEFT_OF, R.id.delivery_status);
        if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
          thumbnailParams.addRule(RelativeLayout.START_OF, R.id.delivery_status);
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
