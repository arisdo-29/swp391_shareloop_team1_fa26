// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { subscribeToasts, type ToastMessage } from '../../components/toast';
import { tokenStorage } from '../../services/http';
import { paymentApi } from '../../services/paymentApi';
import { Credit } from './Credit';

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
  TOPUP_PENDING_EXISTS: 'WALLET_TOPUP_PENDING_EXISTS',
  paymentApi: {
    getOrder: vi.fn(),
    createTopUpOrder: vi.fn(),
    cancelOrder: vi.fn(),
    listOrders: vi.fn().mockResolvedValue({ items: [], page: 0, size: 1, totalElements: 0 }),
  },
}));

beforeEach(() => {
  localStorage.clear();
  sessionStorage.clear();
  refreshWallet.mockClear();
  tokenStorage.set('valid-token', 'Bearer', 3600);
  tokenStorage.setUserId(7);
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

it('kiểm tra Backend và cho phép tiếp tục đơn còn PENDING trong phiên hiện tại', async () => {
  const redirectUrl = 'https://sandbox.vnpayment.vn/pay?vnp_TxnRef=SL-OLD';
  sessionStorage.setItem(
    'shareloop:pendingTopUp',
    JSON.stringify({ orderCode: 'SL-OLD', redirectUrl }),
  );
  vi.mocked(paymentApi.getOrder).mockResolvedValue({
    orderCode: 'SL-OLD',
    userId: 7,
    amountVnd: 20_000,
    credits: 20,
    vndPerCredit: 1_000,
    status: 'PENDING',
    provider: 'VNPAY',
    createdAt: '2026-11-03T00:00:00Z',
    expiresAt: '2099-11-03T00:30:00Z',
    submittedAt: null,
    reviewedBy: null,
    reviewedAt: null,
    reviewNote: null,
  });

  render(
    <MemoryRouter initialEntries={['/credit']}>
      <Credit />
    </MemoryRouter>,
  );

  expect(await screen.findByRole('heading', { name: 'Đơn đang chờ thanh toán' })).toBeTruthy();
  expect(paymentApi.getOrder).toHaveBeenCalledWith('SL-OLD');
  expect(screen.getByRole('link', { name: 'Tiếp tục thanh toán' }).getAttribute('href')).toBe(
    redirectUrl,
  );
});

it('không render lỗi không tìm thấy của mã đơn cũ trên form nạp Credit', async () => {
  sessionStorage.setItem('shareloop:pendingTopUp', JSON.stringify({ orderCode: 'SL-OLD' }));
  vi.mocked(paymentApi.getOrder).mockRejectedValue({
    code: 'NOT_FOUND',
    message: 'Không tìm thấy dữ liệu.',
    status: 404,
  });

  render(
    <MemoryRouter initialEntries={['/credit?orderCode=SL-OLD']}>
      <Credit />
    </MemoryRouter>,
  );

  await waitFor(() => expect(paymentApi.getOrder).toHaveBeenCalledWith('SL-OLD'));
  expect(screen.getByRole('heading', { name: 'Nạp Credit' })).toBeTruthy();
  expect(screen.queryByText('Mở lại đơn nạp gần nhất')).toBeNull();
  expect(screen.queryByText('Không tìm thấy dữ liệu.')).toBeNull();
  expect(sessionStorage.getItem('shareloop:pendingTopUp')).toBeNull();
});

it('giữ phiên và báo Toast khi người dùng quay lại sau khi hủy VNPay', async () => {
  vi.mocked(paymentApi.getOrder).mockResolvedValue({
    orderCode: 'SL-123',
    userId: 7,
    amountVnd: 20_000,
    credits: 20,
    vndPerCredit: 1_000,
    status: 'CANCELLED',
    provider: 'VNPAY',
    createdAt: '2026-11-03T00:00:00Z',
    expiresAt: '2026-11-03T00:15:00Z',
    submittedAt: null,
    reviewedBy: null,
    reviewedAt: null,
    reviewNote: null,
    failureReason: 'Bạn đã hủy giao dịch tại VNPay.',
  });
  const received: ToastMessage[] = [];
  const unsubscribe = subscribeToasts((message) => received.push(message));

  render(
    <MemoryRouter initialEntries={['/credit?orderCode=SL-123&status=FAILED']}>
      <Credit />
    </MemoryRouter>,
  );

  await screen.findByRole('heading', { name: 'Đơn nạp đã hủy' });
  await waitFor(() => {
    expect(
      received.filter(
        (message) => message.message === 'Bạn đã hủy yêu cầu thanh toán.',
      ),
    ).toEqual([expect.objectContaining({ tone: 'warning' })]);
  });
  expect(tokenStorage.get()).toBe('valid-token');
  expect(tokenStorage.getUserId()).toBe(7);
  expect(refreshWallet).not.toHaveBeenCalled();
  expect(screen.getByRole('heading', { name: 'Đơn nạp đã hủy' })).toBeTruthy();
  expect(screen.getByText(/Bạn đã hủy giao dịch tại VNPay./)).toBeTruthy();
  expect(screen.queryByText('Đang đồng bộ trạng thái')).toBeNull();
  expect(screen.getAllByText('Đã hủy thanh toán').length).toBeGreaterThan(1);
  expect(screen.queryByRole('timer')).toBeNull();
  expect(screen.queryByRole('link', { name: 'Tiếp tục thanh toán' })).toBeNull();
  expect(screen.getByText('20 Credit')).toBeTruthy();
  expect(screen.getByRole('button', { name: 'Tạo đơn nạp mới' })).toBeTruthy();
  expect(screen.queryByText('Đơn nạp Credit đã được tạo')).toBeNull();
  unsubscribe();
});

it('hiển thị thất bại riêng và không phát thông báo hủy khi Backend chỉ trả FAILED', async () => {
  vi.mocked(paymentApi.getOrder).mockResolvedValue({
    orderCode: 'SL-FAILED',
    userId: 7,
    amountVnd: 50_000,
    credits: 50,
    vndPerCredit: 1_000,
    status: 'FAILED',
    provider: 'VNPAY',
    createdAt: '2026-11-03T00:00:00Z',
    expiresAt: '2026-11-03T00:15:00Z',
    submittedAt: null,
    reviewedBy: null,
    reviewedAt: null,
    reviewNote: null,
  });
  const received: ToastMessage[] = [];
  const unsubscribe = subscribeToasts((message) => received.push(message));

  render(
    <MemoryRouter initialEntries={['/credit?orderCode=SL-FAILED']}>
      <Credit />
    </MemoryRouter>,
  );

  expect(await screen.findByText('Thanh toán không thành công. Credit chưa được cộng vào ví.'))
    .toBeTruthy();
  expect(screen.getByRole('heading', { name: 'Thanh toán không thành công' })).toBeTruthy();
  expect(screen.queryByRole('timer')).toBeNull();
  expect(
    received.some((message) => message.message === 'Bạn đã hủy yêu cầu thanh toán.'),
  ).toBe(false);
  unsubscribe();
});

it('không tự đánh dấu đã hủy khi callback thất bại nhưng Backend vẫn trả PENDING', async () => {
  sessionStorage.setItem('shareloop:pendingTopUp', JSON.stringify({ orderCode: 'SL-123' }));
  vi.mocked(paymentApi.getOrder).mockResolvedValue({
    orderCode: 'SL-123',
    userId: 7,
    amountVnd: 20_000,
    credits: 20,
    vndPerCredit: 1_000,
    status: 'PENDING',
    provider: 'VNPAY',
    createdAt: '2026-11-03T00:00:00Z',
    expiresAt: '2099-11-03T00:15:00Z',
    submittedAt: null,
    reviewedBy: null,
    reviewedAt: null,
    reviewNote: null,
  });

  render(
    <MemoryRouter initialEntries={['/credit?orderCode=SL-123&status=FAILED']}>
      <Credit />
    </MemoryRouter>,
  );

  expect(await screen.findByText(
    'Thanh toán vừa thất bại tại VNPay, nhưng đơn vẫn đang chờ hệ thống xác nhận hủy. Credit chưa được cộng vào ví.',
  )).toBeTruthy();
  expect(screen.getByRole('heading', { name: 'Đơn nạp đang chờ xác nhận' })).toBeTruthy();
  expect(screen.getAllByText('Đang đồng bộ trạng thái').length).toBeGreaterThan(0);
  // Thanh toán đã thất bại: không đếm ngược nữa dù Backend chưa kịp chốt đơn.
  expect(screen.queryByRole('timer')).toBeNull();
  expect(screen.queryByRole('link', { name: 'Tiếp tục thanh toán' })).toBeNull();
  expect(screen.queryByText('Đã hủy thanh toán')).toBeNull();
  expect(sessionStorage.getItem('shareloop:pendingTopUp')).not.toBeNull();
  expect(refreshWallet).not.toHaveBeenCalled();
});

it('tự kiểm tra lại đơn PENDING và tải lại ví khi Backend xác nhận SUCCESS', async () => {
  const pendingOrder = {
    orderCode: 'SL-123',
    userId: 7,
    amountVnd: 50_000,
    credits: 50,
    vndPerCredit: 1_000,
    status: 'PENDING' as const,
    provider: 'VNPAY' as const,
    createdAt: '2026-11-03T00:00:00Z',
    expiresAt: '2099-11-03T00:15:00Z',
    submittedAt: null,
    reviewedBy: null,
    reviewedAt: null,
    reviewNote: null,
  };
  vi.mocked(paymentApi.getOrder)
    .mockResolvedValueOnce(pendingOrder)
    .mockResolvedValueOnce({ ...pendingOrder, status: 'SUCCESS' });

  render(
    <MemoryRouter initialEntries={['/credit?orderCode=SL-123&status=SUCCESS']}>
      <Credit />
    </MemoryRouter>,
  );

  // Vòng hỏi trạng thái chạy mỗi 4 giây.
  await waitFor(() => expect(paymentApi.getOrder).toHaveBeenCalledTimes(2), { timeout: 6_000 });
  await waitFor(() => expect(refreshWallet).toHaveBeenCalledTimes(1));
  expect(screen.getByText('Nạp thành công +50 Credit')).toBeTruthy();
}, 10_000);

function pendingVnpayOrder(orderCode: string) {
  return {
    orderCode,
    userId: 7,
    amountVnd: 20_000,
    credits: 20,
    vndPerCredit: 1_000,
    status: 'PENDING' as const,
    provider: 'VNPAY' as const,
    createdAt: '2026-11-03T00:00:00Z',
    expiresAt: '2099-11-03T00:15:00Z',
    submittedAt: null,
    reviewedBy: null,
    reviewedAt: null,
    reviewNote: null,
    failureReason: null,
  };
}

it('Xác nhận hủy: khoá nút khi đang gọi, cập nhật ngay theo response, ẩn đồng hồ, không đổi ví', async () => {
  // given: đơn VNPay đang chờ thanh toán trong phiên hiện tại
  sessionStorage.setItem(
    'shareloop:pendingTopUp',
    JSON.stringify({ orderCode: 'SL-CANCEL', redirectUrl: 'https://sandbox.vnpayment.vn/pay' }),
  );
  vi.mocked(paymentApi.getOrder).mockResolvedValue(pendingVnpayOrder('SL-CANCEL'));
  let resolveCancel: (value: Awaited<ReturnType<typeof paymentApi.cancelOrder>>) => void = () => {};
  vi.mocked(paymentApi.cancelOrder).mockReturnValue(
    new Promise((resolve) => {
      resolveCancel = resolve;
    }),
  );
  render(
    <MemoryRouter initialEntries={['/credit']}>
      <Credit />
    </MemoryRouter>,
  );
  expect(await screen.findByRole('timer')).toBeTruthy();

  // when: bấm Hủy đơn nạp rồi Xác nhận hủy
  fireEvent.click(screen.getByRole('button', { name: 'Hủy đơn nạp' }));
  fireEvent.click(screen.getByRole('button', { name: 'Xác nhận hủy' }));

  // then: đang gọi API thì nút bị khoá
  expect(await screen.findByRole('button', { name: 'Đang hủy...' })).toHaveProperty('disabled', true);
  expect(paymentApi.cancelOrder).toHaveBeenCalledWith('SL-CANCEL');

  // when: Backend trả đơn đã hủy
  resolveCancel({
    ...pendingVnpayOrder('SL-CANCEL'),
    status: 'CANCELLED',
    reviewNote: 'Bạn đã hủy đơn nạp.',
    failureReason: 'Bạn đã hủy đơn nạp.',
  });

  // then: giao diện đổi ngay, không cần Kiểm tra lại; không đếm ngược, không tải lại ví
  expect(await screen.findByRole('heading', { name: 'Đơn nạp đã hủy' })).toBeTruthy();
  expect(screen.getByText(/Bạn đã hủy đơn nạp\./)).toBeTruthy();
  expect(screen.queryByRole('timer')).toBeNull();
  expect(screen.queryByText('Đang đồng bộ trạng thái')).toBeNull();
  expect(screen.queryByRole('link', { name: 'Tiếp tục thanh toán' })).toBeNull();
  expect(screen.getByRole('button', { name: 'Tạo đơn nạp mới' })).toBeTruthy();
  expect(paymentApi.cancelOrder).toHaveBeenCalledTimes(1);
  expect(paymentApi.getOrder).toHaveBeenCalledTimes(1);
  expect(refreshWallet).not.toHaveBeenCalled();
  expect(screen.getByText('10')).toBeTruthy();
  expect(sessionStorage.getItem('shareloop:pendingTopUp')).toBeNull();
});

it('Không hủy: đóng hộp xác nhận, đơn vẫn chờ thanh toán', async () => {
  sessionStorage.setItem('shareloop:pendingTopUp', JSON.stringify({ orderCode: 'SL-KEEP' }));
  vi.mocked(paymentApi.getOrder).mockResolvedValue(pendingVnpayOrder('SL-KEEP'));
  render(
    <MemoryRouter initialEntries={['/credit']}>
      <Credit />
    </MemoryRouter>,
  );

  fireEvent.click(await screen.findByRole('button', { name: 'Hủy đơn nạp' }));
  fireEvent.click(screen.getByRole('button', { name: 'Không hủy' }));

  expect(screen.queryByRole('button', { name: 'Xác nhận hủy' })).toBeNull();
  expect(screen.getByRole('timer')).toBeTruthy();
  expect(paymentApi.cancelOrder).not.toHaveBeenCalled();
});
