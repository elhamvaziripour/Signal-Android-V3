package org.BYUSecureSMS.messaging.database.model;


import android.content.Context;
import android.support.annotation.NonNull;

import org.BYUSecureSMS.messaging.mms.SlideDeck;
import org.BYUSecureSMS.messaging.database.documents.IdentityKeyMismatch;
import org.BYUSecureSMS.messaging.database.documents.NetworkFailure;
import org.BYUSecureSMS.messaging.mms.Slide;
import org.BYUSecureSMS.messaging.recipients.Recipient;
import org.BYUSecureSMS.messaging.recipients.Recipients;

import java.util.List;

public abstract class MmsMessageRecord extends MessageRecord {

  private final @NonNull
  SlideDeck slideDeck;

  MmsMessageRecord(Context context, long id, Body body, Recipients recipients,
                   Recipient individualRecipient, int recipientDeviceId, long dateSent,
                   long dateReceived, long threadId, int deliveryStatus, int receiptCount,
                   long type, List<IdentityKeyMismatch> mismatches,
                   List<NetworkFailure> networkFailures, int subscriptionId, long expiresIn,
                   long expireStarted, @NonNull SlideDeck slideDeck)
  {
    super(context, id, body, recipients, individualRecipient, recipientDeviceId, dateSent, dateReceived, threadId, deliveryStatus, receiptCount, type, mismatches, networkFailures, subscriptionId, expiresIn, expireStarted);
    this.slideDeck = slideDeck;
  }

  @Override
  public boolean isMms() {
    return true;
  }

  @NonNull
  public SlideDeck getSlideDeck() {
    return slideDeck;
  }

  @Override
  public boolean isMediaPending() {
    for (Slide slide : getSlideDeck().getSlides()) {
      if (slide.isInProgress() || slide.isPendingDownload()) {
        return true;
      }
    }

    return false;
  }

  public boolean containsMediaSlide() {
    return slideDeck.containsMediaSlide();
  }


}