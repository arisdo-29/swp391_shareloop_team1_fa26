import { describe, expect, it } from 'vitest';
import type { ItemRequest, ItemRequestStatus } from '../types/domain';
import { findActiveSwapPair } from './swapRequest';

const swapRequest = (
  itemId: string,
  offeredItemId: string,
  status: ItemRequestStatus = 'PENDING',
) =>
  ({
    id: `api_req_${itemId}_${offeredItemId}_${status}`,
    type: 'trade',
    itemId,
    offeredItemId,
    status,
  }) as ItemRequest;

describe('findActiveSwapPair', () => {
  it('phát hiện cùng cặp sản phẩm ở cả hai chiều', () => {
    const direct = swapRequest('10', '20');
    const reverse = swapRequest('20', '10');

    expect(findActiveSwapPair([direct], '10', '20')).toBe(direct);
    expect(findActiveSwapPair([reverse], '10', '20')).toBe(reverse);
  });

  it.each([
    'PENDING',
    'RESERVED',
    'AWAITING_LOGISTICS',
    'LOGISTICS_CONFIRMED',
    'AWAITING_HANDOVER',
    'DISPUTED',
  ] as const)('coi trạng thái %s là yêu cầu còn hiệu lực', (status) => {
    expect(findActiveSwapPair([swapRequest('20', '10', status)], '10', '20')).toBeDefined();
  });

  it.each(['COMPLETED', 'REJECTED', 'CANCELLED'] as const)(
    'không chặn yêu cầu đã kết thúc với trạng thái %s',
    (status) => {
      expect(findActiveSwapPair([swapRequest('20', '10', status)], '10', '20')).toBeUndefined();
    },
  );

  it('không ghép nhầm yêu cầu của cặp sản phẩm khác', () => {
    expect(findActiveSwapPair([swapRequest('20', '30')], '10', '20')).toBeUndefined();
  });
});
