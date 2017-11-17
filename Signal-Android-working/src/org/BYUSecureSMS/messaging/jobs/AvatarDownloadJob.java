package org.BYUSecureSMS.messaging.jobs;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.util.Log;

import org.BYUSecureSMS.messaging.crypto.MasterSecret;
import org.BYUSecureSMS.messaging.database.DatabaseFactory;
import org.BYUSecureSMS.messaging.database.GroupDatabase;
import org.BYUSecureSMS.messaging.dependencies.InjectableType;
import org.BYUSecureSMS.messaging.jobs.requirements.MasterSecretRequirement;
import org.BYUSecureSMS.messaging.mms.AttachmentStreamUriLoader.AttachmentModel;
import org.BYUSecureSMS.messaging.util.BitmapDecodingException;
import org.BYUSecureSMS.messaging.util.BitmapUtil;
import org.BYUSecureSMS.messaging.util.Hex;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import javax.inject.Inject;

public class AvatarDownloadJob extends MasterSecretJob implements InjectableType {

  private static final int MAX_AVATAR_SIZE = 20 * 1024 * 1024;
  private static final long serialVersionUID = 1L;

  private static final String TAG = AvatarDownloadJob.class.getSimpleName();

  @Inject transient SignalServiceMessageReceiver receiver;

  private final byte[] groupId;

  public AvatarDownloadJob(Context context, @NonNull byte[] groupId) {
    super(context, JobParameters.newBuilder()
                                .withRequirement(new MasterSecretRequirement(context))
                                .withRequirement(new NetworkRequirement(context))
                                .withPersistence()
                                .create());

    this.groupId = groupId;
  }

  @Override
  public void onAdded() {}

  @Override
  public void onRun(MasterSecret masterSecret) throws IOException {
    GroupDatabase             database   = DatabaseFactory.getGroupDatabase(context);
    GroupDatabase.GroupRecord record     = database.getGroup(groupId);
    File                      attachment = null;

    try {
      if (record != null) {
        long             avatarId    = record.getAvatarId();
        String           contentType = record.getAvatarContentType();
        byte[]           key         = record.getAvatarKey();
        String           relay       = record.getRelay();
        Optional<byte[]> digest      = Optional.fromNullable(record.getAvatarDigest());
        Optional<String> fileName    = Optional.absent();

        if (avatarId == -1 || key == null) {
          return;
        }

        if (digest.isPresent()) {
          Log.w(TAG, "Downloading group avatar with digest: " + Hex.toString(digest.get()));
        }

        attachment = File.createTempFile("avatar", "tmp", context.getCacheDir());
        attachment.deleteOnExit();

        SignalServiceAttachmentPointer pointer     = new SignalServiceAttachmentPointer(avatarId, contentType, key, relay, digest, fileName, false);
        InputStream                    inputStream = receiver.retrieveAttachment(pointer, attachment, MAX_AVATAR_SIZE);
        Bitmap                         avatar      = BitmapUtil.createScaledBitmap(context, new AttachmentModel(attachment, key), 500, 500);

        database.updateAvatar(groupId, avatar);
        inputStream.close();
      }
    } catch (BitmapDecodingException | NonSuccessfulResponseCodeException | InvalidMessageException e) {
      Log.w(TAG, e);
    } finally {
      if (attachment != null)
        attachment.delete();
    }
  }

  @Override
  public void onCanceled() {}

  @Override
  public boolean onShouldRetryThrowable(Exception exception) {
    if (exception instanceof IOException) return true;
    return false;
  }

}
