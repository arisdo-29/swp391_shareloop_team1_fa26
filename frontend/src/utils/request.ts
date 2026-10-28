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
  } catch {}
}

