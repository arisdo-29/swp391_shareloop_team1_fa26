// @vitest-environment jsdom

import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { tokenStorage } from '../../services/http';
import { paymentApi, type TopUpOrderResponse } from '../../services/paymentApi';
import { ApiTopUpOrder, Credit } from './Credit';

const refreshWallet = vi.fn();

vi.mock('../../app/hooks', () => ({
  useAppSelector: () => ({ id: 'api_7', role: 'user' }),
}));
vi.mock('../../app/useWallet', () => ({
  useWallet: () => ({
    wallet: { available: 10, heldCredit: 0, creditBalance: 10, hasToppedUp: false },
    loading: false,
    error: '',
    refresh: refreshWallet,
    replaceWallet: vi.fn(),
  }),
}));
vi.mock('../../services/itemApi', () => ({ itemApi: { mine: vi.fn().mockResolvedValue([]) } }));
vi.mock('../../services/walletApi', () => ({
  walletApi: {
    listHeldFees: vi.fn().mockResolvedValue([]),
    listTransactions: vi.fn().mockResolvedValue({
      items: [],
      totalElements: 0,
      counts: { all: 0, topUp: 0, spend: 0 },
    }),
    listAllTransactions: vi.fn().mockResolvedValue([]),
  },
}));
vi.mock('../../services/paymentApi', () => ({
  paymentApi: { getOrder: vi.fn(), createTopUpOrder: vi.fn(), cancelOrder: vi.fn() },
}));

const VNPAY_URL = 'https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_TxnRef=SL-NEW';

function createdOrder(): TopUpOrderResponse {
  const now = Date.now();
  return {
    orderCode: 'SL-NEW',
    amountVnd: 20_000,
    credits: 20,
    status: 'PENDING',
    provider: 'VNPAY',
    redirectUrl: VNPAY_URL,
    expiresAt: new Date(now + 15 * 60_000).toISOString(),
    serverTime: new Date(now).toISOString(),
  };
}

async function submitTopUp() {
  fireEvent.change(screen.getByLabelText('Số tiền nạp (VND)'), { target: { value: '20000' } });
  fireEvent.click(screen.getByRole('button', { name: 'Tạo đơn nạp' }));
  fireEvent.click(screen.getByRole('button', { name: 'Tôi hiểu, tiếp tục nạp' }));
}

function renderCredit() {
  render(
    <MemoryRouter initialEntries={['/credit']}>
      <Credit />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  localStorage.clear();
  sessionStorage.clear();
  refreshWallet.mockClear();
  tokenStorage.set('valid-token', 'Bearer', 3600);
  tokenStorage.setUserId(7);
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
  vi.restoreAllMocks();
  vi.mocked(paymentApi.getOrder).mockReset();
  vi.mocked(paymentApi.createTopUpOrder).mockReset();
});

describe('mở VNPay ở tab mới', () => {
  it('tạo đơn rồi đưa tab mới tới VNPay, tab ShareLoop ở lại đếm ngược', async () => {
    // given: trình duyệt cho mở tab mới
    const paymentTab = { closed: false, opener: {} as unknown, location: { href: '' }, close: vi.fn() };
    const open = vi.spyOn(window, 'open').mockReturnValue(paymentTab as unknown as Window);
    vi.mocked(paymentApi.createTopUpOrder).mockResolvedValue(createdOrder());
    vi.mocked(paymentApi.getOrder).mockResolvedValue({
      ...createdOrder(),
      userId: 7,
      vndPerCredit: 1_000,
      createdAt: new Date().toISOString(),
      expiresAt: createdOrder().expiresAt!,
      submittedAt: null,
      reviewedBy: null,
      reviewedAt: null,
      reviewNote: null,
    });
    renderCredit();

    // when
    await submitTopUp();

    // then: tab trống được mở ngay trong cú bấm, sau đó điền URL VNPay; không còn liên kết ngược
    expect(open).toHaveBeenCalledWith('', '_blank');
    await waitFor(() => expect(paymentTab.location.href).toBe(VNPAY_URL));
    expect(paymentTab.opener).toBeNull();
    expect(await screen.findByRole('heading', { name: 'Đơn đang chờ thanh toán' })).toBeTruthy();
    expect(screen.getByRole('timer').textContent).toMatch(/Còn (15:00|14:5\d) để thanh toán/);
    expect(screen.queryByRole('link', { name: 'Mở trang thanh toán VNPay' })).toBeNull();
  });

  it('trình duyệt chặn popup thì hiện nút "Mở trang thanh toán VNPay" mở ở tab mới', async () => {
    // given
    vi.spyOn(window, 'open').mockReturnValue(null);
    vi.mocked(paymentApi.createTopUpOrder).mockResolvedValue(createdOrder());
    vi.mocked(paymentApi.getOrder).mockReturnValue(new Promise(() => {}));
    renderCredit();

    // when
    await submitTopUp();

    // then
    const link = await screen.findByRole('link', { name: 'Mở trang thanh toán VNPay' });
    expect(link.getAttribute('href')).toBe(VNPAY_URL);
    expect(link.getAttribute('target')).toBe('_blank');
    expect(link.getAttribute('rel')).toContain('noopener');
  });

  it('tạo đơn lỗi thì đóng tab trống vừa mở', async () => {
    // given
    const paymentTab = { closed: false, opener: null, location: { href: '' }, close: vi.fn() };
    vi.spyOn(window, 'open').mockReturnValue(paymentTab as unknown as Window);
    vi.mocked(paymentApi.createTopUpOrder).mockRejectedValue({ message: 'Lỗi tạo đơn', status: 500 });
    renderCredit();

    // when
    await submitTopUp();

    // then
    expect(await screen.findByText('Lỗi tạo đơn')).toBeTruthy();
    expect(paymentTab.close).toHaveBeenCalled();
  });
});

describe('đồng hồ theo giờ server', () => {
  const NOW = new Date('2026-11-21T08:00:00Z');

  function pendingOrder(overrides: Partial<TopUpOrderResponse> = {}): TopUpOrderResponse {
    return {
      orderCode: 'SL-1',
      amountVnd: 20_000,
      credits: 20,
      status: 'PENDING',
      provider: 'VNPAY',
      redirectUrl: VNPAY_URL,
      expiresAt: '2026-11-21T08:02:00Z',
      ...overrides,
    };
  }

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(NOW);
  });

  it('bù độ lệch: server chạy trước máy 30 giây thì còn 01:30 chứ không phải 02:00', () => {
    render(
      <ApiTopUpOrder order={pendingOrder()} clockOffsetMs={30_000} onNewTopup={() => undefined} />,
    );

    expect(screen.getByRole('timer').textContent).toContain('Còn 01:30 để thanh toán');
  });

  it('về 0 thì dừng đếm, hiện "Đơn đã hết hạn" và hỏi lại server đúng một lần', () => {
    const onDeadlineReached = vi.fn();
    render(
      <ApiTopUpOrder
        order={pendingOrder()}
        onNewTopup={() => undefined}
        onDeadlineReached={onDeadlineReached}
      />,
    );

    act(() => {
      vi.advanceTimersByTime(121_000);
    });

    expect(screen.queryByRole('timer')).toBeNull();
    expect(screen.getAllByText('Đơn đã hết hạn').length).toBeGreaterThan(0);
    expect(screen.queryByRole('link', { name: 'Tiếp tục thanh toán' })).toBeNull();
    expect(onDeadlineReached).toHaveBeenCalledTimes(1);

    // Đồng hồ đã dừng: thêm thời gian cũng không gọi lại.
    act(() => {
      vi.advanceTimersByTime(10_000);
    });
    expect(onDeadlineReached).toHaveBeenCalledTimes(1);
  });

  it('đơn không còn PENDING thì không đếm ngược', () => {
    for (const status of ['SUCCESS', 'FAILED', 'EXPIRED'] as const) {
      render(<ApiTopUpOrder order={pendingOrder({ status })} onNewTopup={() => undefined} />);
      expect(screen.queryByRole('timer')).toBeNull();
      cleanup();
    }
  });
});
