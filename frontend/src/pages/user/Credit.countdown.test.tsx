// @vitest-environment jsdom

import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { TopUpOrderResponse } from '../../services/paymentApi';
import { ApiTopUpOrder } from './Credit';

const NOW = new Date('2026-11-17T08:00:00Z');

function pendingOrder(overrides: Partial<TopUpOrderResponse> = {}): TopUpOrderResponse {
  return {
    orderCode: 'SL-123',
    amountVnd: 20_000,
    credits: 20,
    status: 'PENDING',
    provider: 'VNPAY',
    redirectUrl: 'https://sandbox.vnpayment.vn/pay?vnp_TxnRef=SL-123',
    expiresAt: '2026-11-17T08:02:00Z',
    ...overrides,
  };
}

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(NOW);
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

describe('B29: đếm ngược đơn nạp đang chờ thanh toán', () => {
  it('còn hạn: đếm ngược tới expiresAt của backend và có nút Tiếp tục thanh toán', () => {
    // when
    render(<ApiTopUpOrder order={pendingOrder()} onNewTopup={() => undefined} />);

    // then
    expect(screen.getByRole('timer').textContent).toContain('Còn 02:00 để thanh toán');
    const continueLink = screen.getByRole('link', { name: 'Tiếp tục thanh toán' });
    expect(continueLink.getAttribute('href')).toBe(
      'https://sandbox.vnpayment.vn/pay?vnp_TxnRef=SL-123',
    );

    // when: 1 phút trôi qua
    act(() => {
      vi.advanceTimersByTime(60_000);
    });

    // then
    expect(screen.getByRole('timer').textContent).toContain('Còn 01:00 để thanh toán');
  });

  it('hết giờ: đổi sang Hết hạn, ẩn nút, báo không cộng Credit', () => {
    // given
    render(<ApiTopUpOrder order={pendingOrder()} onNewTopup={() => undefined} />);

    // when
    act(() => {
      vi.advanceTimersByTime(121_000);
    });

    // then: chỉ đổi hiển thị, trạng thái thật vẫn do job đổi
    expect(screen.getAllByText('Hết hạn').length).toBeGreaterThan(0);
    expect(screen.queryByText('Đang chờ thanh toán')).toBeNull();
    expect(screen.queryByRole('timer')).toBeNull();
    expect(screen.getByText(
      'Đơn đã quá hạn thanh toán. Credit sẽ không được cộng cho đơn này.',
    )).toBeTruthy();
    expect(screen.queryByRole('link', { name: 'Tiếp tục thanh toán' })).toBeNull();
  });

  it('đơn đã cộng Credit hoặc backend không trả expiresAt thì không đếm ngược', () => {
    // when
    render(
      <ApiTopUpOrder order={pendingOrder({ status: 'SUCCESS' })} onNewTopup={() => undefined} />,
    );
    // then
    expect(screen.queryByRole('timer')).toBeNull();
    cleanup();

    // when
    render(
      <ApiTopUpOrder order={pendingOrder({ expiresAt: null })} onNewTopup={() => undefined} />,
    );
    // then: vẫn tiếp tục thanh toán được, chỉ không có đồng hồ
    expect(screen.queryByRole('timer')).toBeNull();
    expect(screen.getByRole('link', { name: 'Tiếp tục thanh toán' })).toBeTruthy();
  });

  it('rút gọn mã đơn dài và sao chép đúng mã đầy đủ', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText },
    });
    const orderCode = '0A93D1F4E5B62C0B';

    render(<ApiTopUpOrder order={pendingOrder({ orderCode })} onNewTopup={() => undefined} />);

    expect(screen.getByText('#0A93...2C0B')).toBeTruthy();
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Sao chép mã đơn đầy đủ' }));
      await Promise.resolve();
    });
    expect(writeText).toHaveBeenCalledWith(orderCode);
    expect(screen.getByText('Đã sao chép')).toBeTruthy();
  });
});
