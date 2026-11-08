import type { MessageResponse } from '../services/chatApi';
import type { ApiError } from '../services/http';
import type { ItemResponse } from '../services/itemApi';
import { resolveMediaUrl } from '../services/mediaUrl';
import type { RequestResponse } from '../services/requestApi';
import type { Item } from '../types/domain';

// Đề nghị đổi món chỉ mở khi giao dịch Cho tặng đang thương lượng (docs/api/chat.md mục 4–6).
// Chỉ để ẩn/hiện nút; backend vẫn kiểm lại điều kiện khi gọi API.
export function isOfferChangeOpen(request: Pick<RequestResponse, 'type' | 'status'>): boolean {
  return request.type === 'GIVE' && (request.status === 'RESERVED' || request.status === 'AWAITING_LOGISTICS');
}

// Mỗi giao dịch có tối đa một đề nghị PENDING. Lấy tin mới nhất vì tin cũ có thể chưa kịp cập nhật DECLINED.
export function latestPendingOfferChange(messages: MessageResponse[]): MessageResponse | undefined {
  let latest: MessageResponse | undefined;
  for (const entry of messages) {
    if (entry.kind === 'OFFER_CHANGE' && entry.offerStatus === 'PENDING') latest = entry;
  }
  return latest;
}

// Bài của người nhận có thể đem đề nghị đổi: đã duyệt và còn hạn. Backend kiểm các điều kiện còn lại.
export function offerChangeOptions(
  items: ItemResponse[],
  now = Date.now(),
): Array<Pick<Item, 'id' | 'title' | 'images' | 'district'>> {
  const options: Array<Pick<Item, 'id' | 'title' | 'images' | 'district'>> = [];
  for (const item of items) {
    if (item.status !== 'APPROVED') continue;
    if (item.expireAt && Date.parse(item.expireAt) < now) continue;
    options.push({
      id: String(item.id),
      title: item.title,
      images: item.images.map(resolveMediaUrl).filter((url) => url !== ''),
      district: '',
    });
  }
  return options;
}

const offerChangeErrorMessages: Record<string, string> = {
  CHAT_OFFER_CHANGE_NOT_PENDING: 'Đề nghị này đã được trả lời hoặc đã tự huỷ. Tin nhắn đã được tải lại.',
  CHAT_OFFER_CHANGE_NOT_FOUND: 'Không tìm thấy đề nghị này trong cuộc trò chuyện.',
  REQUEST_OFFER_CHANGE_NOT_ALLOWED: 'Giao dịch không còn đổi món được (đã là Trao đổi hoặc đã chốt lịch).',
  REQUEST_ITEM_NOT_AVAILABLE: 'Món đề nghị không còn sẵn sàng để trao đổi.',
  REQUEST_ITEM_NOT_FOUND: 'Món đề nghị không còn tồn tại.',
  REQUEST_SELF_OFFER_NOT_ALLOWED: 'Không thể đề nghị chính món của giao dịch này.',
};

// Lỗi của các API đổi món; mã lạ thì dùng thông điệp backend gửi về.
export function offerChangeErrorMessage(apiError: ApiError): string {
  return (
    offerChangeErrorMessages[apiError.code] ||
    apiError.message ||
    'Không thực hiện được. Vui lòng thử lại.'
  );
}
