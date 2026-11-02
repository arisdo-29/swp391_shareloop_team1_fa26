import type { ItemRequestStatus } from '../types/domain';

export type ConversationFilter = 'all' | 'unread' | 'open' | 'closed';

export const conversationFilterLabels: Record<ConversationFilter, string> = {
  all: 'Tất cả',
  unread: 'Chưa đọc',
  open: 'Đang giao dịch',
  closed: 'Đã đóng',
};

// Đã đóng là các trạng thái kết thúc của Request: hoàn tất, bị huỷ, không được chọn. Còn lại đều đang giao dịch.
const closedStatuses: ItemRequestStatus[] = ['COMPLETED', 'CANCELLED', 'REJECTED'];

export function isClosedRequestStatus(status: ItemRequestStatus) {
  return closedStatuses.includes(status);
}

/** Cuộc trò chuyện có thuộc tab lọc không. `unread` lấy từ isRequestUnread (mốc đã xem lưu ở trình duyệt). */
export function matchesConversationFilter(
  filter: ConversationFilter,
  status: ItemRequestStatus,
  unread: boolean,
) {
  if (filter === 'unread') return unread;
  if (filter === 'open') return !isClosedRequestStatus(status);
  if (filter === 'closed') return isClosedRequestStatus(status);
  return true;
}

/** Tìm không phân biệt hoa thường trong các đoạn chữ cho trước (tên món, tên người, nhãn trạng thái). */
export function matchesConversationQuery(query: string, searchableParts: string[]) {
  const normalizedQuery = query.trim().toLocaleLowerCase('vi-VN');
  if (!normalizedQuery) return true;
  return searchableParts.join(' ').toLocaleLowerCase('vi-VN').includes(normalizedQuery);
}
