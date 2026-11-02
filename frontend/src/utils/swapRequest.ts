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
  return requests.find(
    (request) =>
      request.type === 'trade' &&
      isActiveRequestStatus(request.status) &&
      ((request.itemId === firstItemId && request.offeredItemId === secondItemId) ||
        (request.itemId === secondItemId && request.offeredItemId === firstItemId)),
  );
}
