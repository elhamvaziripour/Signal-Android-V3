package org.BYUSecureSMS.messaging.attachments;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.BYUSecureSMS.messaging.crypto.MasterSecretUnion;
import org.BYUSecureSMS.messaging.crypto.MediaKey;
import org.BYUSecureSMS.messaging.database.AttachmentDatabase;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;

import java.util.LinkedList;
import java.util.List;

public class PointerAttachment extends Attachment {

  public PointerAttachment(@NonNull String contentType, int transferState, long size,
                           @Nullable String fileName,  @NonNull String location,
                           @NonNull String key, @NonNull String relay,
                           @Nullable byte[] digest, boolean voiceNote)
  {
    super(contentType, transferState, size, fileName, location, key, relay, digest, null, voiceNote);
  }

  @Nullable
  @Override
  public Uri getDataUri() {
    return null;
  }

  @Nullable
  @Override
  public Uri getThumbnailUri() {
    return null;
  }


  public static List<Attachment> forPointers(@NonNull MasterSecretUnion masterSecret, Optional<List<SignalServiceAttachment>> pointers) {
    List<Attachment> results = new LinkedList<>();

    if (pointers.isPresent()) {
      for (SignalServiceAttachment pointer : pointers.get()) {
        if (pointer.isPointer()) {
          String encryptedKey = MediaKey.getEncrypted(masterSecret, pointer.asPointer().getKey());
          results.add(new PointerAttachment(pointer.getContentType(),
                                            AttachmentDatabase.TRANSFER_PROGRESS_PENDING,
                                            pointer.asPointer().getSize().or(0),
                                            pointer.asPointer().getFileName().orNull(),
                                            String.valueOf(pointer.asPointer().getId()),
                                            encryptedKey, pointer.asPointer().getRelay().orNull(),
                                            pointer.asPointer().getDigest().orNull(),
                                            pointer.asPointer().getVoiceNote()));
        }
      }
    }

    return results;
  }
}
