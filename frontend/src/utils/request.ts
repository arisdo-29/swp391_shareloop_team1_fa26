import type { ItemRequestStatus } from '../types/domain';

export const requestStatusLabel: Record<ItemRequestStatus, string> = {
  PENDING: 'Chờ phản hồi',
  RESERVED: 'Đã được chọn',
  AWAITING_LOGISTICS: 'Chờ chốt lịch',
  LOGISTICS_CONFIRMED: 'Đã chốt lịch',
  AWAITING_HANDOVER: 'Chờ giao nhận',
  COMPLETED: 'Hoàn tất',
  DISPUTED: 'Đang khiếu nại',
  REJECTED: 'Không được chọn',
  CANCELLED: 'Đã hủy',
};

export function canChat(status: ItemRequestStatus) {
  return ['RESERVED', 'AWAITING_LOGISTICS', 'LOGISTICS_CONFIRMED', 'AWAITING_HANDOVER', 'COMPLETED', 'DISPUTED'].includes(status);
}

export function canViewChat(status: ItemRequestStatus) {
  return [
    'RESERVED',
    'AWAITING_LOGISTICS',
    'LOGISTICS_CONFIRMED',
    'AWAITING_HANDOVER',
    'COMPLETED',
    'DISPUTED',
    'CANCELLED',
  ].includes(status);
}

export function canSchedule(status: ItemRequestStatus) {
  return status === 'RESERVED' || status === 'AWAITING_LOGISTICS';
}

export function canConfirmHandover(status: ItemRequestStatus) {
  return status === 'LOGISTICS_CONFIRMED' || status === 'AWAITING_HANDOVER';
}

// Hai bên được huỷ giao dịch sau khi đã chọn người (docs/api/request.md mục 13). Backend vẫn kiểm lại.
export function canCancelTransaction(status: ItemRequestStatus) {
  return status === 'RESERVED' || status === 'AWAITING_LOGISTICS' || status === 'LOGISTICS_CONFIRMED' || status === 'AWAITING_HANDOVER';
}

// U08: còn khiếu nại được khi giao dịch hoàn tất và chưa qua complaintDeadline backend trả về.
// Không có hạn (dữ liệu cũ) thì vẫn cho mở form, backend là nơi kiểm cuối cùng.
export function canOpenComplaint(status: ItemRequestStatus, complaintDeadline?: string | null, now = Date.now()) {
  if (status !== 'COMPLETED') return false;
  if (!complaintDeadline) return true;
  return now < new Date(complaintDeadline).getTime();
}

// "14:30 31/10" theo giờ máy người dùng.
export function formatDeadline(value: string) {
  const date = new Date(value);
  const pad = (part: number) => String(part).padStart(2, '0');
  return `${pad(date.getHours())}:${pad(date.getMinutes())} ${pad(date.getDate())}/${pad(date.getMonth() + 1)}`;
}

// Chỉ hiện nút "chưa nhận được / chưa giao được" khi bên kia đã xác nhận bàn giao còn mình thì chưa (mục 14).
export function canReportHandoverIssue(status: ItemRequestStatus, otherConfirmedAt?: string, currentConfirmedAt?: string) {
  return status === 'AWAITING_HANDOVER' && Boolean(otherConfirmedAt) && !currentConfirmedAt;
}

const selectedRequestStatuses: ItemRequestStatus[] = [
  'RESERVED',
  'AWAITING_LOGISTICS',
  'LOGISTICS_CONFIRMED',
  'AWAITING_HANDOVER',
  'COMPLETED',
  'DISPUTED',
];

export function canSelectPendingRequest(
  status: ItemRequestStatus,
  itemOwnerId: number | undefined,
  currentUserId: number,
  siblingStatuses: ItemRequestStatus[],
) {
  if (itemOwnerId !== currentUserId || status !== 'PENDING') return false;
  return !siblingStatuses.some((siblingStatus) => selectedRequestStatuses.includes(siblingStatus));
}

export function isRequestUnread(lastMessageAt?: string | null, requestId?: number): boolean {
  if (!lastMessageAt || !requestId) return false;
  try {
    const seen = localStorage.getItem(`shareloop_last_seen_chat_${requestId}`);
    if (!seen) return true;
    return new Date(lastMessageAt).getTime() > new Date(seen).getTime();
  } catch {
    return false;
  }
}

export function markRequestChatSeen(requestId?: number): void {
  if (!requestId) return;
  try {
    localStorage.setItem(`shareloop_last_seen_chat_${requestId}`, new Date().toISOString());
    if (typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent('shareloop_chat_seen', { detail: { requestId } }));
    }
  } catch {
    /* Storage may be blocked or unavailable. */
  }
}

