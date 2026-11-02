import type { Item, ItemRequest, ItemRequestStatus } from '../types/domain';

export type ActivityStatusFilter =
  | 'all'
  | 'pending_review'
  | 'waiting_for_choice'
  | 'temporarily_hidden'
  | 'in_transaction'
  | 'completed'
  | 'cancelled'
  | 'rejected'
  | 'expired';

export interface ActivityStatusFilterOption {
  value: ActivityStatusFilter;
  label: string;
}

export const itemActivityStatusOptions: readonly ActivityStatusFilterOption[] = [
  { value: 'all', label: 'Tất cả' },
  { value: 'pending_review', label: 'Chờ duyệt' },
  { value: 'waiting_for_choice', label: 'Chờ chọn người' },
  { value: 'temporarily_hidden', label: 'Đang tạm ẩn' },
  { value: 'in_transaction', label: 'Đang giao dịch' },
  { value: 'completed', label: 'Đã hoàn thành' },
  { value: 'cancelled', label: 'Đã hủy' },
  { value: 'rejected', label: 'Bị từ chối' },
  { value: 'expired', label: 'Hết hạn' },
];

export const sentRequestActivityStatusOptions: readonly ActivityStatusFilterOption[] = [
  { value: 'all', label: 'Tất cả' },
  { value: 'waiting_for_choice', label: 'Chờ chọn người' },
  { value: 'in_transaction', label: 'Đang giao dịch' },
  { value: 'completed', label: 'Đã hoàn thành' },
  { value: 'cancelled', label: 'Đã hủy' },
];

const activeRequestStatuses: readonly ItemRequestStatus[] = [
  'RESERVED',
  'AWAITING_LOGISTICS',
  'LOGISTICS_CONFIRMED',
  'AWAITING_HANDOVER',
  'DISPUTED',
];

export function matchesItemActivityStatus(
  item: Item,
  filter: ActivityStatusFilter,
  requests: readonly ItemRequest[] = [],
) {
  if (filter === 'all') return true;
  if (filter === 'temporarily_hidden') return item.hidden === true;
  if (item.hidden) return false;
  if (filter === 'pending_review') return item.status === 'PENDING_REVIEW';
  if (filter === 'waiting_for_choice') return item.status === 'APPROVED';
  if (filter === 'in_transaction') {
    return (
      item.status === 'RESERVED' ||
      requests.some((request) => activeRequestStatuses.includes(request.status))
    );
  }
  if (filter === 'completed') {
    return item.status === 'TRADED' || requests.some((request) => request.status === 'COMPLETED');
  }
  if (filter === 'cancelled') {
    return requests.some((request) => request.status === 'CANCELLED');
  }
  if (filter === 'rejected') return item.status === 'REJECTED';
  return item.status === 'EXPIRED';
}

export function matchesSentRequestActivityStatus(
  request: ItemRequest,
  filter: ActivityStatusFilter,
) {
  if (filter === 'all') return true;
  if (
    filter === 'pending_review' ||
    filter === 'temporarily_hidden' ||
    filter === 'rejected' ||
    filter === 'expired'
  )
    return false;
  if (filter === 'waiting_for_choice') return request.status === 'PENDING';
  if (filter === 'in_transaction') return activeRequestStatuses.includes(request.status);
  if (filter === 'completed') return request.status === 'COMPLETED';
  return request.status === 'CANCELLED';
}
