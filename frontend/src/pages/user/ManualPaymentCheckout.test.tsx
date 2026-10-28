// @vitest-environment jsdom

import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { paymentApi, type TopUpOrderDetailResponse } from '../../services/paymentApi';
import { ManualPaymentCheckout } from './ManualPaymentCheckout';

// refresh phải là cùng một hàm giữa các lần render, nếu không effect tải đơn sẽ chạy lại liên tục.
const refreshWallet = vi.hoisted(() => vi.fn());

vi.mock('../../app/useWallet', () => ({
  useWallet: () => ({ refresh: refreshWallet }),
}));

vi.mock('../../services/paymentApi', () => ({
  paymentApi: { getOrder: vi.fn(), markPaid: vi.fn() },
}));

function buildOrder(overrides: Partial<TopUpOrderDetailResponse> = {}): TopUpOrderDetailResponse {
  return {
    orderCode: 'TOPUP-001',
    amountVnd: 50000,
    credits: 50,
    status: 'PENDING',
    provider: 'MANUAL',
    userId: 7,
    vndPerCredit: 1000,
    createdAt: '2026-10-05T03:00:00Z',
    expiresAt: '2026-10-06T03:00:00Z',
    submittedAt: null,
    reviewedBy: null,
    reviewedAt: null,
    reviewNote: null,
    ...overrides,
  };
}

function renderCheckout() {
  return render(
    <MemoryRouter initialEntries={['/wallet/topup/manual-checkout?orderCode=TOPUP-001']}>
      <ManualPaymentCheckout />
    </MemoryRouter>,
  );
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('ManualPaymentCheckout', () => {
  it('hiện chữ tiếng Việt cho đơn đã gửi yêu cầu, không còn chữ admin', async () => {
    // given
    vi.mocked(paymentApi.getOrder).mockResolvedValue(
      buildOrder({ submittedAt: '2026-10-05T04:00:00Z', reviewNote: 'Đã đối chiếu' }),
    );

    // when
    const { container } = renderCheckout();

    // then
    expect(await screen.findByText('Chờ quản trị viên duyệt')).toBeTruthy();
    expect(screen.getByText(/Ghi chú của quản trị viên: Đã đối chiếu/)).toBeTruthy();
    expect(container.textContent).not.toMatch(/admin/i);
  });

  it('hiện lỗi tiếng Việt và cho thử tải lại khi không tải được đơn', async () => {
    // given
    vi.mocked(paymentApi.getOrder)
      .mockRejectedValueOnce({ status: 0, code: 'NETWORK_ERROR', message: 'Không kết nối được máy chủ, vui lòng thử lại.' })
      .mockResolvedValueOnce(buildOrder());
    renderCheckout();
    expect(await screen.findByText('Không kết nối được máy chủ, vui lòng thử lại.')).toBeTruthy();

    // when
    await userEvent.click(screen.getByRole('button', { name: 'Thử tải lại' }));

    // then
    expect(await screen.findByText('Chờ thanh toán')).toBeTruthy();
    expect(screen.queryByText('Không kết nối được máy chủ, vui lòng thử lại.')).toBeNull();
  });

  it('chỉ gửi xác nhận đã thanh toán một lần dù bấm liên tiếp', async () => {
    // given
    vi.mocked(paymentApi.getOrder).mockResolvedValue(buildOrder());
    vi.mocked(paymentApi.markPaid).mockReturnValue(new Promise(() => undefined));
    renderCheckout();
    const button = await screen.findByRole('button', { name: 'Đã thanh toán' });

    // when
    await userEvent.dblClick(button);

    // then
    await waitFor(() => expect(paymentApi.markPaid).toHaveBeenCalledTimes(1));
  });
});
