// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { tokenStorage } from '../../services/http';
import { paymentApi, type TopUpOrderDetailResponse } from '../../services/paymentApi';
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
    listOrders: vi.fn(),
  },
}));

const PAY_URL = 'https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_TxnRef=OLD1234567';

function order(overrides: Partial<TopUpOrderDetailResponse> = {}): TopUpOrderDetailResponse {
  const now = Date.now();
  return {
    orderCode: 'OLD1234567',
    userId: 7,
    amountVnd: 50_000,
    credits: 50,
    vndPerCredit: 1_000,
    status: 'PENDING',
    provider: 'VNPAY',
    createdAt: new Date(now - 60_000).toISOString(),
    expiresAt: new Date(now + 10 * 60_000).toISOString(),
    serverTime: new Date(now).toISOString(),
    submittedAt: null,
    reviewedBy: null,
    reviewedAt: null,
    reviewNote: null,
    failureReason: null,
    paymentUrl: PAY_URL,
    ...overrides,
  };
}

function page(items: TopUpOrderDetailResponse[], totalElements = items.length) {
  return { items, page: 0, size: items.length || 1, totalElements };
}

// listOrders được gọi cho cả mục đang chờ (status=PENDING) và lịch sử (không status).
function givenServerOrders(pending: TopUpOrderDetailResponse[], history: TopUpOrderDetailResponse[]) {
  // Như Backend: mục đang chờ chỉ trả đơn còn trong hạn thanh toán.
  vi.mocked(paymentApi.listOrders).mockImplementation(async (params = {}) =>
    params.status === 'PENDING'
      ? page(pending.filter((entry) => Date.parse(entry.expiresAt) > Date.now()))
      : page(history),
  );
}

function renderCredit() {
  render(
    <MemoryRouter initialEntries={['/credit']}>
      <Credit />
    </MemoryRouter>,
  );
}

async function submitAmount(amount: string) {
  fireEvent.change(screen.getByLabelText('Số tiền nạp (VND)'), { target: { value: amount } });
  fireEvent.click(screen.getByRole('button', { name: 'Tạo đơn nạp' }));
  fireEvent.click(screen.getByRole('button', { name: 'Tôi hiểu, tiếp tục nạp' }));
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
  for (const fn of Object.values(paymentApi)) vi.mocked(fn).mockReset();
});

describe('đơn đang chờ thanh toán lấy từ server', () => {
  it('(a) vào lại trang (không còn phiên) vẫn thấy đơn chờ, tiếp tục thanh toán bằng đúng URL cũ', async () => {
    // given: sessionStorage trống (rời trang / tab khác), server còn đơn PENDING
    givenServerOrders([order()], [order()]);

    // when
    renderCredit();

    // then: thẻ đơn đang chờ hiện trên form, có đồng hồ, nút tiếp tục mở đúng paymentUrl ở tab mới
    expect(await screen.findByRole('heading', { name: 'Đơn đang chờ thanh toán' })).toBeTruthy();
    expect(screen.getByRole('timer')).toBeTruthy();
    const continueLink = screen.getByRole('link', { name: 'Tiếp tục thanh toán' });
    expect(continueLink.getAttribute('href')).toBe(PAY_URL);
    expect(continueLink.getAttribute('target')).toBe('_blank');
    expect(screen.getByRole('button', { name: 'Hủy đơn nạp' })).toBeTruthy();
    // form tạo đơn vẫn ở dưới thẻ
    expect(screen.getByLabelText('Số tiền nạp (VND)')).toBeTruthy();
    expect(paymentApi.listOrders).toHaveBeenCalledWith({ status: 'PENDING', page: 0, size: 1 });
  });

  it('(b) tạo đơn khi còn đơn chờ: hỏi tiếp tục hay hủy, không gọi tạo đơn và không bỏ đơn cũ', async () => {
    givenServerOrders([order()], [order()]);
    renderCredit();
    await screen.findByRole('heading', { name: 'Đơn đang chờ thanh toán' });

    // when
    await submitAmount('20000');

    // then
    const dialog = await screen.findByRole('dialog', { name: 'Bạn còn đơn nạp đang chờ' });
    expect(within(dialog).getByText(/Bạn còn 1 đơn/)).toBeTruthy();
    expect(within(dialog).getByRole('button', { name: 'Tiếp tục thanh toán đơn cũ' })).toBeTruthy();
    expect(within(dialog).getByRole('button', { name: 'Hủy đơn cũ & tạo đơn mới' })).toBeTruthy();
    expect(paymentApi.createTopUpOrder).not.toHaveBeenCalled();
    expect(paymentApi.cancelOrder).not.toHaveBeenCalled();
  });

  it('(b) "Tạo đơn nạp mới" trên thẻ cũng hỏi trước khi bỏ đơn cũ; "Tiếp tục thanh toán đơn cũ" mở URL cũ', async () => {
    givenServerOrders([order()], [order()]);
    const open = vi.spyOn(window, 'open').mockReturnValue({} as Window);
    renderCredit();

    fireEvent.click(await screen.findByRole('button', { name: 'Tạo đơn nạp mới' }));
    const dialog = await screen.findByRole('dialog', { name: 'Bạn còn đơn nạp đang chờ' });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Tiếp tục thanh toán đơn cũ' }));

    expect(open).toHaveBeenCalledWith(PAY_URL, '_blank', 'noopener,noreferrer');
    expect(screen.queryByRole('dialog', { name: 'Bạn còn đơn nạp đang chờ' })).toBeNull();
    expect(screen.getByRole('heading', { name: 'Đơn đang chờ thanh toán' })).toBeTruthy();
  });

  it('(b) Backend trả 409 (đơn tạo ở tab khác): lấy đơn chờ về và hỏi người dùng', async () => {
    // given: lúc vào trang chưa có đơn chờ, đến khi tạo thì Backend báo còn đơn
    let pendingNow: TopUpOrderDetailResponse[] = [];
    vi.mocked(paymentApi.listOrders).mockImplementation(async (params = {}) =>
      params.status === 'PENDING' ? page(pendingNow) : page([]),
    );
    vi.mocked(paymentApi.createTopUpOrder).mockImplementation(async () => {
      pendingNow = [order()];
      throw { code: 'WALLET_TOPUP_PENDING_EXISTS', message: 'Bạn còn một đơn nạp đang chờ thanh toán.', status: 409 };
    });
    vi.spyOn(window, 'open').mockReturnValue(null);
    renderCredit();

    // when
    await submitAmount('20000');

    // then
    expect(await screen.findByRole('dialog', { name: 'Bạn còn đơn nạp đang chờ' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Đơn đang chờ thanh toán' })).toBeTruthy();
  });

  it('(c) "Hủy đơn cũ & tạo đơn mới" gửi replacePending trong một lần gọi tạo đơn', async () => {
    givenServerOrders([order()], [order()]);
    const paymentTab = { closed: false, opener: {} as unknown, location: { href: '' }, close: vi.fn() };
    vi.spyOn(window, 'open').mockReturnValue(paymentTab as unknown as Window);
    const now = Date.now();
    vi.mocked(paymentApi.createTopUpOrder).mockResolvedValue({
      orderCode: 'NEW7654321',
      amountVnd: 20_000,
      credits: 20,
      status: 'PENDING',
      provider: 'VNPAY',
      redirectUrl: 'https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_TxnRef=NEW7654321',
      expiresAt: new Date(now + 15 * 60_000).toISOString(),
      serverTime: new Date(now).toISOString(),
    });
    renderCredit();
    await screen.findByRole('heading', { name: 'Đơn đang chờ thanh toán' });

    // when
    await submitAmount('20000');
    const dialog = await screen.findByRole('dialog', { name: 'Bạn còn đơn nạp đang chờ' });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Hủy đơn cũ & tạo đơn mới' }));

    // then: một lần gọi, có replacePending; tab mới tới VNPay của đơn mới
    await waitFor(() =>
      expect(paymentApi.createTopUpOrder).toHaveBeenCalledWith({ amountVnd: 20_000, replacePending: true }),
    );
    await waitFor(() => expect(paymentTab.location.href).toContain('NEW7654321'));
    expect(paymentApi.cancelOrder).not.toHaveBeenCalled();
  });

  it('(d) đồng hồ về 0: đơn rời mục đang chờ, lịch sử tải lại', async () => {
    // given: đơn còn 2 giây
    const now = Date.now();
    givenServerOrders(
      [order({ expiresAt: new Date(now + 2_000).toISOString(), serverTime: new Date(now).toISOString() })],
      [],
    );
    renderCredit();
    await screen.findByRole('heading', { name: 'Đơn đang chờ thanh toán' });
    const callsBefore = vi.mocked(paymentApi.listOrders).mock.calls.length;

    // when: chờ hết giờ
    await waitFor(() => expect(screen.queryByRole('timer')).toBeNull(), { timeout: 5_000 });

    // then: thẻ đơn chờ biến mất, form còn, lịch sử được hỏi lại
    await waitFor(() =>
      expect(screen.queryByRole('heading', { name: 'Đơn đang chờ thanh toán' })).toBeNull(),
    );
    expect(screen.getByLabelText('Số tiền nạp (VND)')).toBeTruthy();
    expect(vi.mocked(paymentApi.listOrders).mock.calls.length).toBeGreaterThan(callsBefore);
  }, 10_000);
});

describe('Lịch sử nạp', () => {
  it('liệt kê mọi đơn với badge trạng thái, thời gian, số tiền và Credit', async () => {
    const serverTime = new Date().toISOString();
    givenServerOrders(
      [],
      [
        order({ orderCode: 'SUCC000001', status: 'SUCCESS', paymentUrl: null }),
        order({ orderCode: 'PEND000002' }),
        order({ orderCode: 'CANC000003', status: 'CANCELLED', failureReason: 'Bạn đã hủy đơn nạp.' }),
        order({ orderCode: 'FAIL000004', status: 'FAILED' }),
        order({ orderCode: 'EXPI000005', status: 'EXPIRED' }),
        order({
          orderCode: 'LATE000006',
          expiresAt: new Date(Date.now() - 60_000).toISOString(),
          serverTime,
        }),
      ],
    );

    renderCredit();

    const history = await screen.findByRole('region', { name: 'Lịch sử nạp' });
    await within(history).findByText('Thành công');
    for (const label of ['Thành công', 'Đang chờ', 'Đã hủy', 'Thất bại']) {
      expect(within(history).getAllByText(label).length).toBeGreaterThan(0);
    }
    // EXPIRED và PENDING đã qua hạn (đang chờ server chốt) đều hiện Hết hạn
    expect(within(history).getAllByText('Hết hạn')).toHaveLength(2);
    expect(within(history).getAllByText(/50\.000.*· 50 Credit/).length).toBe(6);
    expect(within(history).getByText('Bạn đã hủy đơn nạp.')).toBeTruthy();
  });

  it('có phân trang khi nhiều hơn 5 đơn', async () => {
    vi.mocked(paymentApi.listOrders).mockImplementation(async (params = {}) =>
      params.status === 'PENDING'
        ? page([])
        : { items: [order({ status: 'SUCCESS' })], page: params.page ?? 0, size: 5, totalElements: 12 },
    );

    renderCredit();

    const history = await screen.findByRole('region', { name: 'Lịch sử nạp' });
    await within(history).findByText('Thành công');
    fireEvent.click(within(history).getByRole('button', { name: 'Trang 2' }));
    await waitFor(() =>
      expect(paymentApi.listOrders).toHaveBeenCalledWith({ page: 1, size: 5 }),
    );
  });
});
