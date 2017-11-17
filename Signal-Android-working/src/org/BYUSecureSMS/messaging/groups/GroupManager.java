package org.BYUSecureSMS.messaging.groups;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.protobuf.ByteString;

import org.BYUSecureSMS.messaging.crypto.MasterSecret;
import org.BYUSecureSMS.messaging.sms.MessageSender;
import org.BYUSecureSMS.messaging.util.MediaUtil;
import org.BYUSecureSMS.messaging.attachments.Attachment;
import org.BYUSecureSMS.messaging.attachments.UriAttachment;
import org.BYUSecureSMS.messaging.database.Address;
import org.BYUSecureSMS.messaging.database.AttachmentDatabase;
import org.BYUSecureSMS.messaging.database.DatabaseFactory;
import org.BYUSecureSMS.messaging.database.GroupDatabase;
import org.BYUSecureSMS.messaging.mms.OutgoingGroupMediaMessage;
import org.BYUSecureSMS.messaging.providers.SingleUseBlobProvider;
import org.BYUSecureSMS.messaging.recipients.Recipient;
import org.BYUSecureSMS.messaging.recipients.RecipientFactory;
import org.BYUSecureSMS.messaging.recipients.Recipients;
import org.BYUSecureSMS.messaging.util.BitmapUtil;
import org.BYUSecureSMS.messaging.util.GroupUtil;
import org.BYUSecureSMS.messaging.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContext;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class GroupManager {

  public static @NonNull GroupActionResult createGroup(@NonNull  Context        context,
                                                       @NonNull MasterSecret masterSecret,
                                                       @NonNull  Set<Recipient> members,
                                                       @Nullable Bitmap         avatar,
                                                       @Nullable String         name)
      throws InvalidNumberException
  {
    final byte[]        avatarBytes      = BitmapUtil.toByteArray(avatar);
    final GroupDatabase groupDatabase    = DatabaseFactory.getGroupDatabase(context);
    final byte[]        groupId          = groupDatabase.allocateGroupId();
    final Set<Address>  memberAddresses  = getMemberAddresses(context, members);

    memberAddresses.add(Address.fromSerialized(TextSecurePreferences.getLocalNumber(context)));
    groupDatabase.create(groupId, name, new LinkedList<>(memberAddresses), null, null);
    groupDatabase.updateAvatar(groupId, avatarBytes);
    return sendGroupUpdate(context, masterSecret, groupId, memberAddresses, name, avatarBytes);
  }

  private static Set<Address> getMemberAddresses(Context context, Collection<Recipient> recipients)
      throws InvalidNumberException
  {
    final Set<Address> results = new HashSet<>();
    for (Recipient recipient : recipients) {
      results.add(recipient.getAddress());
    }

    return results;
  }

  public static GroupActionResult updateGroup(@NonNull  Context        context,
                                              @NonNull  MasterSecret   masterSecret,
                                              @NonNull  byte[]         groupId,
                                              @NonNull  Set<Recipient> members,
                                              @Nullable Bitmap         avatar,
                                              @Nullable String         name)
      throws InvalidNumberException
  {
    final GroupDatabase groupDatabase   = DatabaseFactory.getGroupDatabase(context);
    final Set<Address>  memberAddresses = getMemberAddresses(context, members);
    final byte[]        avatarBytes     = BitmapUtil.toByteArray(avatar);

    memberAddresses.add(Address.fromSerialized(TextSecurePreferences.getLocalNumber(context)));
    groupDatabase.updateMembers(groupId, new LinkedList<>(memberAddresses));
    groupDatabase.updateTitle(groupId, name);
    groupDatabase.updateAvatar(groupId, avatarBytes);

    return sendGroupUpdate(context, masterSecret, groupId, memberAddresses, name, avatarBytes);
  }

  private static GroupActionResult sendGroupUpdate(@NonNull  Context      context,
                                                   @NonNull  MasterSecret masterSecret,
                                                   @NonNull  byte[]       groupId,
                                                   @NonNull  Set<Address> members,
                                                   @Nullable String       groupName,
                                                   @Nullable byte[]       avatar)
  {
    Attachment avatarAttachment = null;
    Address    groupAddress     = Address.fromSerialized(GroupUtil.getEncodedId(groupId));
    Recipients groupRecipient   = RecipientFactory.getRecipientsFor(context, new Address[]{groupAddress}, false);

    List<String> numbers = new LinkedList<>();

    for (Address member : members) {
      numbers.add(member.serialize());
    }

    GroupContext.Builder groupContextBuilder = GroupContext.newBuilder()
                                                           .setId(ByteString.copyFrom(groupId))
                                                           .setType(GroupContext.Type.UPDATE)
                                                           .addAllMembers(numbers);
    if (groupName != null) groupContextBuilder.setName(groupName);
    GroupContext groupContext = groupContextBuilder.build();

    if (avatar != null) {
      Uri avatarUri = SingleUseBlobProvider.getInstance().createUri(avatar);
      avatarAttachment = new UriAttachment(avatarUri, MediaUtil.IMAGE_PNG, AttachmentDatabase.TRANSFER_PROGRESS_DONE, avatar.length, null, false);
    }

    OutgoingGroupMediaMessage outgoingMessage = new OutgoingGroupMediaMessage(groupRecipient, groupContext, avatarAttachment, System.currentTimeMillis(), 0);
    long                      threadId        = MessageSender.send(context, masterSecret, outgoingMessage, -1, false, null);

    return new GroupActionResult(groupRecipient, threadId);
  }

  public static class GroupActionResult {
    private Recipients groupRecipient;
    private long       threadId;

    public GroupActionResult(Recipients groupRecipient, long threadId) {
      this.groupRecipient = groupRecipient;
      this.threadId       = threadId;
    }

    public Recipients getGroupRecipient() {
      return groupRecipient;
    }

    public long getThreadId() {
      return threadId;
    }
  }
}
