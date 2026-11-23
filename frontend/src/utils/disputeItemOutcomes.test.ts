import { describe, expect, it } from 'vitest';
import type { AdminRequestDetail } from '../services/adminRequestApi';
import { defaultItemChoices, disputeItemRows } from './disputeItemOutcomes';

function detail(overrides: Record<string, unknown>) {
  return {
    donor: { userId: 3, name: 'Bình' },
    receiver: { userId: 2, name: 'An' },
    item: { itemId: 5, title: 'Máy ảnh', imageUrl: null },
    offeredItem: { itemId: 6, title: 'Mũ', imageUrl: null },
    deliveryMethod: 'SHIPPING',
    shippingLegs: [],
    ...overrides,
  } as unknown as AdminRequestDetail;
}

const leg = (number: number, receivedAt: string | null) => ({ leg: number, receivedAt });

describe('disputeItemRows với giao hàng hai chiều', () => {
  it('chiều đã nhận thì món ở chỗ người nhận chiều đó và mặc định là Đã đổi chủ', () => {
    const rows = disputeItemRows(
      detail({ shippingLegs: [leg(1, '2026-11-26T08:00:00Z'), leg(2, null)] }),
    );

    expect(rows[0].holderName).toBe('An');
    expect(rows[1].holderName).toBe('An');
    expect(defaultItemChoices(rows, null)).toEqual({ 5: 'TRANSFERRED', 6: 'REOPEN' });
  });

  it('chiều chưa nhận thì món vẫn ở chỗ chủ món', () => {
    const rows = disputeItemRows(detail({ shippingLegs: [leg(1, null), leg(2, null)] }));

    expect(rows[0].holderName).toBe('Bình');
    expect(rows[1].holderName).toBe('An');
  });

  it('Gặp trực tiếp giữ quy ước cũ', () => {
    const rows = disputeItemRows(detail({ deliveryMethod: 'IN_PERSON' }));

    expect(rows[0].holderName).toBe('An');
    expect(rows[1].holderName).toBe('Bình');
    expect(defaultItemChoices(rows, 3)).toEqual({ 5: 'HIDE', 6: 'REOPEN' });
  });
});
