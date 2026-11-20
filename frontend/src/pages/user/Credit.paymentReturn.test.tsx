// @vitest-environment jsdom

import { cleanup, render, screen, waitFor } from '@testing-library/react';
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
  paymentApi: { getOrder: vi.fn(), createTopUpOrder: vi.fn() },
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
    <MemoryRouter initialEntries={['/credit?orderCode=SL-123&status=FAILED']}>
      <Credit />
    </MemoryRouter>,
  );

  await screen.findByRole('heading', { name: 'Đã hủy thanh toán' });
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
 fix/credit-vnpay-session
  expect(screen.getByRole('heading', { name: 'Đã hủy thanh toán' })).toBeTruthy();
  expect(screen.getAllByText('Đã hủy thanh toán').length).toBeGreaterThan(1);
  expect(screen.queryByRole('timer')).toBeNull();
  expect(screen.queryByRole('link', { name: 'Tiếp tục thanh toán' })).toBeNull();

  expect(screen.getByRole('heading', { name: 'Thanh toán đã hủy' })).toBeTruthy();
  expect(screen.getAllByText('Thanh toán đã hủy').length).toBeGreaterThan(1);
  expect(screen.queryByRole('timer')).toBeNull();
 develop
  expect(screen.getByText('20 Credit')).toBeTruthy();
  expect(screen.getByRole('button', { name: 'Tạo đơn nạp khác' })).toBeTruthy();
  expect(screen.queryByText('Đơn nạp Credit đã được tạo')).toBeNull();
  unsubscribe();
});

fix/credit-vnpay-session
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
  expect(
    received.some((message) => message.message === 'Bạn đã hủy yêu cầu thanh toán.'),
  ).toBe(false);
  unsubscribe();
});


 develop
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
 fix/credit-vnpay-session
  expect(screen.getAllByText('Đang đồng bộ trạng thái').length).toBeGreaterThan(0);
  expect(screen.getByRole('timer')).toBeTruthy();
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

  await waitFor(() => expect(paymentApi.getOrder).toHaveBeenCalledTimes(2), { timeout: 4_000 });
  await waitFor(() => expect(refreshWallet).toHaveBeenCalledTimes(1));
  expect(screen.getByText('Nạp thành công +50 Credit')).toBeTruthy();
});
=======
  expect(screen.getAllByText('Đang chờ thanh toán').length).toBeGreaterThan(0);
  expect(screen.getByRole('timer')).toBeTruthy();
  expect(screen.queryByText('Thanh toán đã hủy')).toBeNull();
  expect(sessionStorage.getItem('shareloop:pendingTopUp')).not.toBeNull();
  expect(refreshWallet).not.toHaveBeenCalled();
});
 develop
