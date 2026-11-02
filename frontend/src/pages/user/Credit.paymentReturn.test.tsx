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

  expect(await screen.findByText('Bạn đã hủy thanh toán. Tài khoản chưa bị trừ tiền.'))
    .toBeTruthy();
  await waitFor(() => {
    expect(received).toContainEqual(expect.objectContaining({
      tone: 'warning',
      message: 'Bạn đã hủy thanh toán. Tài khoản chưa bị trừ tiền.',
    }));
  });
  expect(tokenStorage.get()).toBe('valid-token');
  expect(tokenStorage.getUserId()).toBe(7);
  expect(refreshWallet).not.toHaveBeenCalled();
  unsubscribe();
});
