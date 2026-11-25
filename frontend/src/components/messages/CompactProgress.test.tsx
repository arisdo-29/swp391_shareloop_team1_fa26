// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import type { RequestResponse } from '../../services/requestApi';
import { CompactProgress } from './CompactProgress';

afterEach(cleanup);

const shipping: RequestResponse = {
  id: 101,
  itemId: 45,
  receiverId: 12,
  donorId: 9,
  type: 'SWAP',
  status: 'AWAITING_HANDOVER',
  deliveryMethod: 'SHIPPING',
  itemTitle: 'Máy ảnh',
  offeredItemId: 77,
  offeredItemTitle: 'Mũ bảo hiểm',
  donorShippedAt: '2026-11-25T08:00:00Z',
  createdAt: '2026-11-20T08:00:00Z',
};

describe('CompactProgress', () => {
  it('Giao hàng hiện tiến độ hai chiều ở khung chat', () => {
    render(<CompactProgress status={shipping.status} request={shipping} />);
    expect(screen.getByText(/Máy ảnh: Đã gửi. Mũ bảo hiểm: Chờ gửi/)).toBeTruthy();
  });

  it('Gặp trực tiếp chỉ hiện trạng thái', () => {
    render(<CompactProgress status="AWAITING_HANDOVER" request={{ ...shipping, deliveryMethod: 'IN_PERSON' }} />);
    expect(screen.queryByText(/Máy ảnh: Đã gửi/)).toBeNull();
  });
});
