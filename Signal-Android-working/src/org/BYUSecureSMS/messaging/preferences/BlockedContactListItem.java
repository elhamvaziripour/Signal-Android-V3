package org.BYUSecureSMS.messaging.preferences;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.BYUSecureSMS.messaging.components.AvatarImageView;
import org.BYUSecureSMS.messaging.R;
import org.BYUSecureSMS.messaging.recipients.Recipients;
import org.BYUSecureSMS.messaging.util.Util;

public class BlockedContactListItem extends RelativeLayout implements Recipients.RecipientsModifiedListener {

  private AvatarImageView contactPhotoImage;
  private TextView        nameView;
  private Recipients      recipients;

  public BlockedContactListItem(Context context) {
    super(context);
  }

  public BlockedContactListItem(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public BlockedContactListItem(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @Override
  public void onFinishInflate() {
    super.onFinishInflate();
    this.contactPhotoImage = (AvatarImageView)findViewById(R.id.contact_photo_image);
    this.nameView          = (TextView)       findViewById(R.id.name);
  }

  public void set(Recipients recipients) {
    this.recipients = recipients;

    onModified(recipients);
    recipients.addListener(this);
  }

  @Override
  public void onModified(final Recipients recipients) {
    final AvatarImageView contactPhotoImage = this.contactPhotoImage;
    final TextView        nameView          = this.nameView;

    Util.runOnMain(new Runnable() {
      @Override
      public void run() {
        contactPhotoImage.setAvatar(recipients, false);
        nameView.setText(recipients.toShortString());
      }
    });
  }

  public Recipients getRecipients() {
    return recipients;
  }
}