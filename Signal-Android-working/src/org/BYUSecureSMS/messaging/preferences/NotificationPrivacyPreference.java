package org.BYUSecureSMS.messaging.preferences;

public class NotificationPrivacyPreference {

  private final String preference;

  public NotificationPrivacyPreference(String preference) {
    this.preference = preference;
  }

  public boolean isDisplayContact() {
    return "all".equals(preference) || "contact".equals(preference);
  }

  public boolean isDisplayMessage() {
    return "all".equals(preference);
  }

}
