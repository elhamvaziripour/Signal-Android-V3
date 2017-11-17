package org.BYUSecureSMS.messaging.database.documents;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.BYUSecureSMS.messaging.database.Address;

public class NetworkFailure {

  @JsonProperty(value = "a")
  private String address;

  public NetworkFailure(Address address) {
    this.address = address.serialize();
  }

  public NetworkFailure() {}

  public Address getAddress() {
    return Address.fromSerialized(address);
  }

  @Override
  public boolean equals(Object other) {
    if (other == null || !(other instanceof NetworkFailure)) return false;

    NetworkFailure that = (NetworkFailure)other;
    return this.address.equals(that.address);
  }

  @Override
  public int hashCode() {
    return address.hashCode();
  }
}