package org.BYUSecureSMS.messaging.mms;

import org.BYUSecureSMS.messaging.attachments.Attachment;
import org.BYUSecureSMS.messaging.database.ThreadDatabase;
import org.BYUSecureSMS.messaging.recipients.Recipients;

import java.util.LinkedList;

public class OutgoingExpirationUpdateMessage extends OutgoingSecureMediaMessage {

  public OutgoingExpirationUpdateMessage(Recipients recipients, long sentTimeMillis, long expiresIn) {
    super(recipients, "", new LinkedList<Attachment>(), sentTimeMillis,
          ThreadDatabase.DistributionTypes.CONVERSATION, expiresIn);
  }

  @Override
  public boolean isExpirationUpdate() {
    return true;
  }

}
