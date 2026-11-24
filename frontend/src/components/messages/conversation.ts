import type { ItemResponse } from '../../services/itemApi';
import type { RequestResponse } from '../../services/requestApi';

/** Một cuộc trò chuyện là một Request, kèm bài đăng và món đề nghị nếu tải được. */
export interface ApiConversation {
  request: RequestResponse;
  item?: ItemResponse;
  offeredItem?: ItemResponse;
}

// Chủ bài lấy từ yêu cầu trước, vì bài đã giữ chỗ hoặc đã trao đổi không còn tải được qua API công khai.
export function donorIdOf(conversation: ApiConversation) {
  return conversation.request.donorId ?? conversation.item?.donorId;
}

export type ChatMessageSide = 'left' | 'right';

/**
 * Vị trí tin nhắn bám theo hai chủ sở hữu trong khung trao đổi, không bám theo người đang đăng nhập:
 * món chính/chủ bài ở trái, món đề xuất/người trao đổi ở phải.
 */
export function messageSideForSender(conversation: ApiConversation, senderId: number): ChatMessageSide {
  const leftOwnerId = conversation.item?.donorId ?? donorIdOf(conversation);
  const rightOwnerId = conversation.offeredItem?.donorId ?? conversation.request.receiverId;
  if (senderId === rightOwnerId) return 'right';
  if (senderId === leftOwnerId) return 'left';
  return 'left';
}

export function isDonorView(conversation: ApiConversation, currentUserId: number) {
  return donorIdOf(conversation) === currentUserId;
}

export function requestParticipantName(conversation: ApiConversation, currentUserId: number) {
  if (isDonorView(conversation, currentUserId)) {
    return conversation.request.receiverName?.trim() || 'Người gửi yêu cầu';
  }
  return conversation.request.donorName?.trim() || 'Chủ bài đăng';
}

/** Id của người bên kia; undefined khi chưa biết chủ bài (dữ liệu cũ thiếu donorId). */
export function partnerIdOf(conversation: ApiConversation, currentUserId: number) {
  const donorId = donorIdOf(conversation);
  if (donorId === undefined || donorId === null) return undefined;
  return donorId === currentUserId ? conversation.request.receiverId : donorId;
}

export function partnerAvatarUrl(conversation: ApiConversation, currentUserId: number) {
  const url = isDonorView(conversation, currentUserId)
    ? conversation.request.receiverAvatarUrl
    : conversation.request.donorAvatarUrl;
  return url ?? undefined;
}

// Vai của người bên kia. Giao dịch Trao đổi không có "người cho tặng" nên gọi theo vai trong bài đăng.
export function partnerRoleLabel(conversation: ApiConversation, currentUserId: number) {
  const partnerIsDonor = !isDonorView(conversation, currentUserId);
  if (conversation.request.type === 'SWAP') {
    return partnerIsDonor ? 'Chủ bài đăng' : 'Người đề nghị trao đổi';
  }
  return partnerIsDonor ? 'Người cho tặng' : 'Người nhận';
}

export function avatarInitials(name: string) {
  if (name === 'Chủ bài đăng') return '';
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(-2)
    .map((part) => part[0])
    .join('')
    .toUpperCase();
}

// Khi chưa có tiêu đề món đồ thì gọi theo loại yêu cầu, không in id kỹ thuật ra màn hình.
// Dựa vào việc có kèm món (như requestOfferLabel), vì type của yêu cầu PENDING gửi chéo loại chưa đổi.
export function requestFallbackTitle(request: RequestResponse) {
  return request.offeredItemId ? 'Yêu cầu trao đổi' : 'Yêu cầu nhận đồ';
}

export function conversationTitle(conversation: ApiConversation) {
  return (
    conversation.item?.title ??
    (conversation.request.itemTitle?.trim() || requestFallbackTitle(conversation.request))
  );
}

export function itemPrimaryImage(
  item?: Pick<ItemResponse, 'images'>,
  ...fallbacks: Array<string | null | undefined>
) {
  const candidates = [...(item?.images ?? []), ...fallbacks];
  const image = candidates.find(
    (candidate): candidate is string => typeof candidate === 'string' && candidate.trim().length > 0,
  );
  return image?.trim();
}

export function conversationImage(conversation: ApiConversation) {
  return itemPrimaryImage(conversation.item, conversation.request.itemImage);
}
