package org.BYUSecureSMS.messaging.components;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.provider.ContactsContract;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import org.BYUSecureSMS.messaging.color.MaterialColor;
import org.BYUSecureSMS.messaging.contacts.avatars.ContactColors;
import org.BYUSecureSMS.messaging.contacts.avatars.ContactPhotoFactory;
import org.BYUSecureSMS.messaging.database.IdentityDatabase;
import org.BYUSecureSMS.messaging.util.IdentityUtil;
import org.BYUSecureSMS.messaging.util.concurrent.ListenableFuture;
import org.BYUSecureSMS.messaging.R;
import org.BYUSecureSMS.messaging.recipients.Recipient;
import org.BYUSecureSMS.messaging.recipients.RecipientFactory;
import org.BYUSecureSMS.messaging.recipients.Recipients;

import org.BYUSecureSMS.messaging.database.identity.IdentityRecordList;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.concurrent.ExecutionException;

import static com.android.mms.LogTag.TAG;

public class AvatarImageView extends ImageView {

  private boolean inverted;
  // Elham edits starts
  private final IdentityRecordList identityRecords = new IdentityRecordList();
  //Elham edits ends
  public AvatarImageView(Context context) {
    super(context);
    setScaleType(ScaleType.CENTER_CROP);

  }

  public AvatarImageView(Context context, AttributeSet attrs) {
    super(context, attrs);
    setScaleType(ScaleType.CENTER_CROP);


    if (attrs != null) {
      TypedArray typedArray = context.getTheme().obtainStyledAttributes(attrs, R.styleable.AvatarImageView, 0, 0);
      inverted = typedArray.getBoolean(0, false);
      typedArray.recycle();
    }
  }

  public void setAvatar(final @Nullable Recipients recipients, boolean quickContactEnabled) {


    if (recipients != null) {

      final MaterialColor backgroundColor = recipients.getColor();
     //Toast.makeText(getContext(), "Your toast message"+identityRecords.isVerified(), Toast.LENGTH_LONG).show();

      // Elham edits start
      IdentityUtil.getRemoteIdentityKey(getContext(), recipients.getPrimaryRecipient()).addListener(new ListenableFuture.Listener<Optional<IdentityDatabase.IdentityRecord>>() {
        @Override
        public void onSuccess(Optional<IdentityDatabase.IdentityRecord> result) {
          if (result.isPresent()) {
            if (result.get().getVerifiedStatus() == IdentityDatabase.VerifiedStatus.DEFAULT){
              //setImageDrawable(recipients.getContactPhoto().asCallCard(getContext()));
              setImageResource(R.drawable.warningicon);

            }
            else {
              setImageDrawable(recipients.getContactPhoto().asDrawable(getContext(), backgroundColor.toConversationColor(getContext()), inverted));
              //    setImageDrawable(recipients.getContactPhoto().asCallCard(getContext()));

            }
          }
        }

        @Override
        public void onFailure(ExecutionException e) {
          Log.w(TAG, e);
        }
      });

          // Elham edits ends

      setAvatarClickHandler(recipients, quickContactEnabled);
    } else {

      setImageDrawable(ContactPhotoFactory.getDefaultContactPhoto(null).asDrawable(getContext(), ContactColors.UNKNOWN_COLOR.toConversationColor(getContext()), inverted));
      setOnClickListener(null);
    }
  }

  public void setAvatar(@Nullable Recipient recipient, boolean quickContactEnabled) {
    setAvatar(RecipientFactory.getRecipientsFor(getContext(), recipient, true), quickContactEnabled);
  }

  private void setAvatarClickHandler(final Recipients recipients, boolean quickContactEnabled) {
    if (!recipients.isGroupRecipient() && quickContactEnabled) {
      setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          Recipient recipient = recipients.getPrimaryRecipient();

          if (recipient != null && recipient.getContactUri() != null) {
            ContactsContract.QuickContact.showQuickContact(getContext(), AvatarImageView.this, recipient.getContactUri(), ContactsContract.QuickContact.MODE_LARGE, null);
          } else if (recipient != null) {
            final Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
            if (recipient.getAddress().isEmail()) {
              intent.putExtra(ContactsContract.Intents.Insert.EMAIL, recipient.getAddress().toEmailString());
            } else {
              intent.putExtra(ContactsContract.Intents.Insert.PHONE, recipient.getAddress().toPhoneString());
            }
            intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
            getContext().startActivity(intent);
          }
        }
      });
    } else {
      setOnClickListener(null);
    }
  }
}
