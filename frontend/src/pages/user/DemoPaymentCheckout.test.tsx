// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { paymentApi } from '../../services/paymentApi';
import { DemoPaymentCheckout } from './DemoPaymentCheckout';

const replaceWallet = vi.hoisted(() => vi.fn());

vi.mock('../../app/useWallet', () => ({
  useWallet: () => ({ replaceWallet }),
}));

vi.mock('../../services/paymentApi', () => ({
  paymentApi: { getOrder: vi.fn() },
}));

vi.mock('../../services/paymentFlow', () => ({
  demoPaymentEnabled: true,
  demoPaymentUnavailableMessage: 'Thanh toán thử nghiệm chưa được bật trong môi trường này.',
  demoPaymentErrorMessage: (error: { message?: string }) => error.message ?? 'Lỗi',
  confirmDemoPayment: vi.fn(),
}));

function renderCheckout() {
  return render(
    <MemoryRouter initialEntries={['/wallet/topup/mock-checkout?orderCode=TOPUP-009']}>
      <DemoPaymentCheckout />
    </MemoryRouter>,
  );
}

const mockOrder = {
  orderCode: 'TOPUP-009',
  amountVnd: 20000,
  credits: 20,
  status: 'PENDING' as const,
  provider: 'MOCK' as const,
  userId: 7,
  vndPerCredit: 1000,
  createdAt: '2026-10-05T03:00:00Z',
  expiresAt: '2026-10-06T03:00:00Z',
  submittedAt: null,
  reviewedBy: null,
  reviewedAt: null,
  reviewNote: null,
};

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('DemoPaymentCheckout', () => {
  it('hiện trạng thái đang tải, không báo không tìm thấy đơn trong lúc chờ', async () => {
    // given
    vi.mocked(paymentApi.getOrder).mockReturnValue(new Promise(() => undefined));

    // when
    renderCheckout();

    // then
    expect(screen.getByText('Đang tải đơn thanh toán...')).toBeTruthy();
    expect(screen.queryByText(/Không tìm thấy đơn/)).toBeNull();
  });

  it('hiện lỗi thật và cho thử tải lại khi tải đơn thất bại', async () => {
    // given
    vi.mocked(paymentApi.getOrder)
      .mockRejectedValueOnce({ message: 'Không kết nối được máy chủ, vui lòng thử lại.' })
      .mockResolvedValueOnce(mockOrder);
    renderCheckout();
    expect(await screen.findByText('Không kết nối được máy chủ, vui lòng thử lại.')).toBeTruthy();

    // when
    await userEvent.click(screen.getByRole('button', { name: 'Thử tải lại' }));

    // then
    expect(await screen.findByRole('button', { name: 'Xác nhận thanh toán thử nghiệm' })).toBeTruthy();
  });
});
