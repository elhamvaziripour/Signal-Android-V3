package org.BYUSecureSMS.messaging.transport;

public class RetryLaterException extends Exception {
  public RetryLaterException(Exception e) {
    super(e);
  }
}
