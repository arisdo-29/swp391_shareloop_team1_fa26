import type { ItemCondition, TransactionStatus } from '../types/domain';

export const conditionLabel: Record<ItemCondition, string> = {
  NEW: 'Mới',
  LIKE_NEW: 'Ít sử dụng',
  GOOD: 'Đã dùng nhiều',
  DEFECTIVE: 'Có lỗi',
  new: 'Mới',
  good: 'Đã dùng nhiều',
  used: 'Có lỗi',
};

export const conditionDescription: Record<'NEW' | 'LIKE_NEW' | 'GOOD' | 'DEFECTIVE', string> = {
  NEW: 'Chưa sử dụng',
  LIKE_NEW: 'Mới dùng vài lần, còn đẹp',
  GOOD: 'Có trầy xước hoặc hao mòn, vẫn dùng tốt',
  DEFECTIVE: 'Có hư hỏng, cần nêu rõ',
};

export const txLabel: Record<TransactionStatus, string> = {
  NEGOTIATING: 'Đang thương lượng',
  SCHEDULE_PROPOSED: 'Chờ chốt lịch',
  SCHEDULE_CONFIRMED: 'Đã chốt lịch',
  CREDIT_HELD: 'Đã chốt lịch',
  WAITING_HANDOVER: 'Chờ giao nhận',
  SENDER_CONFIRMED: 'Bên gửi đã xác nhận',
  RECEIVER_CONFIRMED: 'Bên nhận đã xác nhận',
  COMPLETED: 'Hoàn tất',
  CANCELLED: 'Đã hủy',
  DISPUTED: 'Khiếu nại',
};

export function formatCredit(value: number) {
  return `${value.toLocaleString('vi-VN')} Credit`;
}

export function formatVnd(value: number) {
  return `${value.toLocaleString('vi-VN')}đ`;
}
