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

export function canSchedule(status: ItemRequestStatus) {
  return status === 'RESERVED' || status === 'AWAITING_LOGISTICS';
}

export function canConfirmHandover(status: ItemRequestStatus) {
  return status === 'LOGISTICS_CONFIRMED' || status === 'AWAITING_HANDOVER';
}
