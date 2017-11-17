package org.BYUSecureSMS.messaging.sms;


import org.BYUSecureSMS.messaging.recipients.Recipients;

public class OutgoingIdentityDefaultMessage extends OutgoingTextMessage {

  public OutgoingIdentityDefaultMessage(Recipients recipients) {
    this(recipients, "");
  }

  private OutgoingIdentityDefaultMessage(Recipients recipients, String body) {
    super(recipients, body, -1);
  }

  @Override
  public boolean isIdentityDefault() {
    return true;
  }

  public OutgoingTextMessage withBody(String body) {
    return new OutgoingIdentityDefaultMessage(getRecipients());
  }
}
