import type { ItemRequest, ItemRequestStatus } from '../types/domain';

const activeRequestStatuses: readonly ItemRequestStatus[] = [
  'PENDING',
  'RESERVED',
  'AWAITING_LOGISTICS',
  'LOGISTICS_CONFIRMED',
  'AWAITING_HANDOVER',
  'DISPUTED',
];

export function isActiveRequestStatus(status: ItemRequestStatus) {
  return activeRequestStatuses.includes(status);
}

export function findActiveSwapPair(
  requests: readonly ItemRequest[],
  firstItemId: string,
  secondItemId: string,
) {
  // Không lọc theo request.type: yêu cầu kèm món gửi vào bài Cho vẫn mang type gift cho tới khi được chọn.
  // So khớp offeredItemId đã đủ để biết đây là một cặp trao đổi.
  return requests.find(
    (request) =>
      isActiveRequestStatus(request.status) &&
      ((request.itemId === firstItemId && request.offeredItemId === secondItemId) ||
        (request.itemId === secondItemId && request.offeredItemId === firstItemId)),
  );
}
