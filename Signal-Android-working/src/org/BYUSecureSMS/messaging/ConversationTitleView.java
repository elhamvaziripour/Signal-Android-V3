package org.BYUSecureSMS.messaging;

import android.content.Context;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.BYUSecureSMS.messaging.util.ViewUtil;
import org.BYUSecureSMS.messaging.recipients.Recipient;
import org.BYUSecureSMS.messaging.recipients.Recipients;

public class ConversationTitleView extends LinearLayout {

  private static final String TAG = ConversationTitleView.class.getSimpleName();

  private TextView  title;
  private TextView  subtitle;
  private ImageView verified;

  public ConversationTitleView(Context context) {
    this(context, null);
  }

  public ConversationTitleView(Context context, AttributeSet attrs) {
    super(context, attrs);

  }

  @Override
  public void onFinishInflate() {
    super.onFinishInflate();

    this.title    = (TextView)  findViewById(R.id.title);
    this.subtitle = (TextView)  findViewById(R.id.subtitle);
    this.verified = (ImageView) findViewById(R.id.verified_indicator);

    ViewUtil.setTextViewGravityStart(this.title, getContext());
    ViewUtil.setTextViewGravityStart(this.subtitle, getContext());
  }

  public void setTitle(@Nullable Recipients recipients) {
    if      (recipients == null)             setComposeTitle();
    else if (recipients.isSingleRecipient()) setRecipientTitle(recipients.getPrimaryRecipient());
    else                                     setRecipientsTitle(recipients);

    if (recipients != null && recipients.isBlocked()) {
      title.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_block_white_18dp, 0, 0, 0);
    } else if (recipients != null && recipients.isMuted()) {
      title.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_volume_off_white_18dp, 0, 0, 0);
    } else {
      title.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
    }
  }

  public void setVerified(boolean verified) {
    this.verified.setVisibility(verified ? View.VISIBLE : View.GONE);
  }

  private void setComposeTitle() {
    this.title.setText(R.string.ConversationActivity_compose_message);
    this.subtitle.setText(null);
    this.subtitle.setVisibility(View.GONE);
  }

  private void setRecipientTitle(Recipient recipient) {
    if (!recipient.isGroupRecipient()) {
      if (TextUtils.isEmpty(recipient.getName())) {
        this.title.setText(recipient.getAddress().serialize());
        this.subtitle.setText(null);
        this.subtitle.setVisibility(View.GONE);
      } else {
        this.title.setText(recipient.getName());

        if (recipient.getCustomLabel() != null) this.subtitle.setText(recipient.getCustomLabel());
        else                                    this.subtitle.setText(recipient.getAddress().serialize());

        this.subtitle.setVisibility(View.VISIBLE);
      }
    } else {
      this.title.setText(recipient.getName());
      this.subtitle.setText(null);
      this.subtitle.setVisibility(View.GONE);
    }
  }

  private void setRecipientsTitle(Recipients recipients) {
    int size = recipients.getRecipientsList().size();

    title.setText(getContext().getString(R.string.ConversationActivity_group_conversation));
    subtitle.setText(getContext().getResources().getQuantityString(R.plurals.ConversationActivity_d_recipients_in_group, size, size));
    subtitle.setVisibility(View.VISIBLE);
  }





}
