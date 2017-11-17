package org.BYUSecureSMS.messaging.mms;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;

import org.BYUSecureSMS.messaging.components.location.SignalPlace;
import org.whispersystems.libsignal.util.guava.Optional;

public class LocationSlide extends ImageSlide {

  @NonNull
  private final SignalPlace place;

  public LocationSlide(@NonNull  Context context, @NonNull  Uri uri, long size, @NonNull SignalPlace place)
  {
    super(context, uri, size);
    this.place = place;
  }

  @Override
  @NonNull
  public Optional<String> getBody() {
    return Optional.of(place.getDescription());
  }

  @NonNull
  public SignalPlace getPlace() {
    return place;
  }

  @Override
  public boolean hasLocation() {
    return true;
  }

}