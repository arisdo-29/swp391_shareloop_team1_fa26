import type { ItemRequestStatus } from '../types/domain';

export const transactionSteps = ['Được chọn', 'Chốt lịch', 'Giao nhận', 'Hoàn tất'] as const;

// Số bước đã xong theo trạng thái yêu cầu; -1 là chưa vào giao dịch (đang chờ, bị từ chối, đã hủy).
// Dùng chung cho bảng tiến trình cũ, thanh thu gọn ở khung chat và trang Tiến trình giao dịch.
export function completedStepCount(status: ItemRequestStatus) {
  if (status === 'RESERVED' || status === 'AWAITING_LOGISTICS') return 1;
  if (status === 'LOGISTICS_CONFIRMED' || status === 'AWAITING_HANDOVER') return 2;
  if (status === 'COMPLETED' || status === 'DISPUTED') return 4;
  return -1;
}
