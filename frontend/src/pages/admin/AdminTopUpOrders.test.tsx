// @vitest-environment jsdom

import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  adminFinanceApi,
  type AdminTopUpOrder,
  type AdminTopUpOrderDetail,
} from '../../services/adminFinanceApi';
import { AdminTopUpOrders as AdminTopUpOrdersPage } from './AdminTopUpOrders';

function AdminTopUpOrders({ path = '/admin/finance/topup-orders' }: { path?: string }) {
  return (
    <MemoryRouter initialEntries={[path]}>
      <AdminTopUpOrdersPage />
    </MemoryRouter>
  );
}

vi.mock('../../services/adminFinanceApi', () => ({
  adminFinanceApi: {
    topUpOrders: vi.fn(),
    topUpSummary: vi.fn(),
    topUpOrderDetail: vi.fn(),
    reconcileTopUp: vi.fn(),
    topUpOrdersExcel: vi.fn(),
  },
}));

const FLAGGED_CODE = 'ABCD1234EFGH5678IJKL9012MNOP3456';

function order(overrides: Partial<AdminTopUpOrder>): AdminTopUpOrder {
  return {
    orderCode: 'SL5F3A9C0D1E2B44A7B8C9D0E1F2A3B4C',
    userId: 11,
    userName: 'Lan An',
    userEmail: 'an@example.com',
    amountVnd: 50000,
    credits: 50,
    provider: 'VNPAY',
    status: 'PENDING',
    providerTxnId: null,
    createdAt: '2026-10-20T03:00:00Z',
    paidAt: null,
    needsReconcile: false,
    reviewedAt: null,
    reviewedByName: null,
    reviewNote: null,
    ...overrides,
  };
}

const flaggedOrder = order({
  orderCode: FLAGGED_CODE,
  userName: 'Chi Lê',
  userEmail: 'chi@example.com',
  status: 'EXPIRED',
  providerTxnId: '14999888',
  paidAt: '2026-10-20T03:20:00Z',
  needsReconcile: true,
});

const orders = [
  order({}),
  order({
    orderCode: 'SLDONE',
    status: 'SUCCESS',
    userName: 'Bình Trần',
    userEmail: 'binh@example.com',
    providerTxnId: '14000111',
    paidAt: '2026-10-19T03:00:00Z',
  }),
  flaggedOrder,
];

function detailOf(target: AdminTopUpOrder): AdminTopUpOrderDetail {
  return {
    order: target,
    callbackData: { vnp_ResponseCode: '00', vnp_BankCode: 'NCB' },
    ledgerLines: [],
  };
}

beforeEach(() => {
  vi.mocked(adminFinanceApi.topUpOrders).mockResolvedValue({
    items: orders,
    page: 0,
    size: 10,
    totalElements: orders.length,
  });
  vi.mocked(adminFinanceApi.topUpSummary).mockResolvedValue({
    byStatus: [
      { status: 'PENDING', orderCount: 1, amountVnd: 50000 },
      { status: 'SUCCESS', orderCount: 4, amountVnd: 200000 },
      { status: 'FAILED', orderCount: 0, amountVnd: 0 },
      { status: 'EXPIRED', orderCount: 1, amountVnd: 30000 },
      { status: 'CANCELLED', orderCount: 0, amountVnd: 0 },
    ],
    needsReconcileCount: 1,
  });
  vi.mocked(adminFinanceApi.topUpOrderDetail).mockResolvedValue(detailOf(flaggedOrder));
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('AdminTopUpOrders', () => {
  it('mở đúng chi tiết đơn nạp và có đường về lịch sử Credit khi được điều hướng từ sổ Credit', async () => {
    render(
      <MemoryRouter
        initialEntries={[
          {
            pathname: '/admin/finance/topup-orders',
            state: {
              openTopUpOrder: FLAGGED_CODE,
              ledgerReturn: {
                type: 'TOP_UP',
                user: null,
                keywordInput: FLAGGED_CODE,
                fromDate: '',
                toDate: '',
                page: 1,
              },
            },
          },
        ]}
      >
        <AdminTopUpOrdersPage />
      </MemoryRouter>,
    );

    const dialog = await screen.findByRole('dialog', { name: 'Chi tiết đơn nạp' });
    expect(adminFinanceApi.topUpOrderDetail).toHaveBeenCalledWith(FLAGGED_CODE);
    expect(within(dialog).getByRole('link', { name: 'Về lịch sử giao dịch Credit' })).toBeTruthy();
  });

  it('mở trang là tải tất cả đơn mà không ép trạng thái và hiện khối tổng hợp', async () => {
    render(<AdminTopUpOrders />);

    await screen.findByText('Lan An');
    expect(adminFinanceApi.topUpOrders).toHaveBeenCalledWith({ page: 0, size: 10 });
    expect((screen.getByLabelText('Trạng thái') as HTMLSelectElement).value).toBe('');
    expect(await screen.findByText('Cần đối soát', { selector: 'p' })).toBeTruthy();
  });

  it('đánh dấu đơn cần đối soát, rút gọn mã đơn và không lộ chữ kỹ thuật', async () => {
    render(<AdminTopUpOrders />);
    await screen.findByText('Chi Lê');

    const row = screen.getByText('Chi Lê').closest('tr') as HTMLElement;
    expect(within(row).getByText('Cần đối soát')).toBeTruthy();
    expect(within(row).getByText('#ABCD...3456')).toBeTruthy();
    expect(within(row).getByText('14999888')).toBeTruthy();
    const page = document.body.textContent ?? '';
    for (const hidden of [FLAGGED_CODE, 'EXPIRED', 'VNPAY', 'User #']) {
      expect(page).not.toContain(hidden);
    }
  });

  it('lọc theo trạng thái gửi đúng tham số', async () => {
    const user = userEvent.setup();
    render(<AdminTopUpOrders />);
    await screen.findByText('Lan An');

    await user.selectOptions(screen.getByLabelText('Trạng thái'), 'PENDING');

    await waitFor(() =>
      expect(adminFinanceApi.topUpOrders).toHaveBeenLastCalledWith({
        status: 'PENDING',
        page: 0,
        size: 10,
      }),
    );
  });

  it('bật công tắc chỉ đơn cần đối soát gửi needsReconcile', async () => {
    const user = userEvent.setup();
    render(<AdminTopUpOrders />);
    await screen.findByText('Lan An');

    await user.click(screen.getByLabelText('Chỉ đơn cần đối soát'));

    await waitFor(() =>
      expect(adminFinanceApi.topUpOrders).toHaveBeenLastCalledWith({
        needsReconcile: true,
        page: 0,
        size: 10,
      }),
    );
  });

  it('tìm kiếm và khoảng ngày được gửi lên máy chủ', async () => {
    const user = userEvent.setup();
    render(<AdminTopUpOrders />);
    await screen.findByText('Lan An');

    await user.type(screen.getByLabelText('Tìm đơn nạp'), 'chi@example.com');
    await user.type(screen.getByLabelText('Từ ngày'), '2026-10-01');

    await waitFor(() =>
      expect(adminFinanceApi.topUpOrders).toHaveBeenLastCalledWith({
        q: 'chi@example.com',
        from: '2026-10-01',
        page: 0,
        size: 10,
      }),
    );
  });

  it('mở từ trang Lịch sử giao dịch Credit kèm ?q thì lọc sẵn theo mã đơn đó', async () => {
    render(<AdminTopUpOrders path="/admin/finance/topup-orders?q=ABCD1234" />);
    await screen.findByText('Lan An');

    expect(adminFinanceApi.topUpOrders).toHaveBeenCalledWith({ q: 'ABCD1234', page: 0, size: 10 });
    expect((screen.getByLabelText('Tìm đơn nạp') as HTMLInputElement).value).toBe('ABCD1234');
  });

  it('mở từ cột tiền thu thì lọc đơn thành công theo ngày thanh toán', async () => {
    const day = '2026-10-06';
    render(
      <AdminTopUpOrders
        path={`/admin/finance/topup-orders?from=${day}&to=${day}&paidDate=true&status=SUCCESS`}
      />,
    );
    await screen.findByText('Lan An');

    expect(adminFinanceApi.topUpOrders).toHaveBeenCalledWith({
      from: day,
      to: day,
      paidDate: true,
      status: 'SUCCESS',
      page: 0,
      size: 10,
    });
    expect(adminFinanceApi.topUpSummary).toHaveBeenCalledWith({
      from: day,
      to: day,
      paidDate: true,
    });
    expect((screen.getByLabelText('Từ ngày thanh toán') as HTMLInputElement).value).toBe(day);
    expect((screen.getByLabelText('Đến ngày thanh toán') as HTMLInputElement).value).toBe(day);
    expect((screen.getByLabelText('Trạng thái') as HTMLSelectElement).value).toBe('SUCCESS');
  });

  it('khoảng ngày ngược thì báo lỗi và không gọi lại máy chủ', async () => {
    const user = userEvent.setup();
    render(<AdminTopUpOrders />);
    await screen.findByText('Lan An');
    vi.mocked(adminFinanceApi.topUpOrders).mockClear();

    await user.type(screen.getByLabelText('Từ ngày'), '2026-10-20');
    await user.type(screen.getByLabelText('Đến ngày'), '2026-10-10');

    expect(await screen.findByText('"Từ ngày" không được sau "Đến ngày".')).toBeTruthy();
    expect(adminFinanceApi.topUpOrders).not.toHaveBeenCalledWith(
      expect.objectContaining({ from: '2026-10-20', to: '2026-10-10' }),
    );
  });

  it('bấm vào ô cần đối soát ở khối tổng hợp lọc đúng các đơn đó', async () => {
    const user = userEvent.setup();
    render(<AdminTopUpOrders />);
    await screen.findByText('Lan An');

    await user.click(await screen.findByRole('button', { name: /Cần đối soát/ }));

    await waitFor(() =>
      expect(adminFinanceApi.topUpOrders).toHaveBeenLastCalledWith({
        needsReconcile: true,
        page: 0,
        size: 10,
      }),
    );
  });

  it('bấm một dòng mở hộp chi tiết, giữ thông tin thanh toán và không hiện callback kỹ thuật', async () => {
    const user = userEvent.setup();
    render(<AdminTopUpOrders />);
    await screen.findByText('Chi Lê');

    await user.click(screen.getByRole('button', { name: '#ABCD...3456' }));

    const dialog = await screen.findByRole('dialog');
    expect(adminFinanceApi.topUpOrderDetail).toHaveBeenCalledWith(FLAGGED_CODE);
    expect(await within(dialog).findByText('Mã giao dịch VNPay')).toBeTruthy();
    expect(within(dialog).getByText('14999888')).toBeTruthy();
    expect(within(dialog).queryByText('Phản hồi từ VNPay')).toBeNull();
    expect(within(dialog).queryByText('Mã phản hồi')).toBeNull();
    expect(within(dialog).queryByText('Ngân hàng')).toBeNull();
    expect(
      within(dialog).getByRole('button', { name: 'Xác nhận đã thu tiền và cộng Credit' }),
    ).toBeTruthy();
    expect(
      within(dialog).getByRole('button', { name: 'Đánh dấu đã hoàn tiền ngoài hệ thống' }),
    ).toBeTruthy();
  });

  it('đơn không cần đối soát thì hộp chi tiết không có nút xử lý', async () => {
    const user = userEvent.setup();
    const normal = orders[1];
    vi.mocked(adminFinanceApi.topUpOrderDetail).mockResolvedValue(detailOf(normal));
    render(<AdminTopUpOrders />);
    await screen.findByText('Bình Trần');

    await user.click(screen.getByRole('button', { name: '#SLDONE' }));

    const dialog = await screen.findByRole('dialog');
    await within(dialog).findByText('Mã giao dịch VNPay');
    expect(within(dialog).queryByRole('button', { name: /cộng Credit/ })).toBeNull();
    expect(within(dialog).queryByRole('button', { name: /hoàn tiền/ })).toBeNull();
  });

  it('cộng Credit bắt buộc có lý do rồi gọi đúng thao tác', async () => {
    const user = userEvent.setup();
    const granted = order({
      ...flaggedOrder,
      status: 'SUCCESS',
      needsReconcile: false,
      reviewedAt: '2026-10-21T03:00:00Z',
      reviewedByName: 'Quản trị',
      reviewNote: 'Đã đối chiếu sao kê',
    });
    vi.mocked(adminFinanceApi.reconcileTopUp).mockResolvedValue({
      ...detailOf(granted),
      ledgerLines: [{ id: 9, amount: 50, balanceAfter: 80, createdAt: '2026-10-21T03:00:00Z' }],
    });
    render(<AdminTopUpOrders />);
    await screen.findByText('Chi Lê');
    await user.click(screen.getByRole('button', { name: '#ABCD...3456' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(
      await within(dialog).findByRole('button', { name: 'Xác nhận đã thu tiền và cộng Credit' }),
    );

    // when: chưa nhập lý do thì nút xác nhận bị khoá
    const confirm = within(dialog).getByRole('button', { name: 'Xác nhận' }) as HTMLButtonElement;
    expect(confirm.disabled).toBe(true);
    await user.type(
      within(dialog).getByLabelText('Lý do hoặc ghi chú (bắt buộc)'),
      'Đã đối chiếu sao kê',
    );
    await user.click(confirm);

    // then
    await waitFor(() =>
      expect(adminFinanceApi.reconcileTopUp).toHaveBeenCalledWith(FLAGGED_CODE, {
        action: 'GRANT_CREDIT',
        note: 'Đã đối chiếu sao kê',
      }),
    );
    expect(await within(dialog).findByText('Số Credit thực tế đã cộng')).toBeTruthy();
    expect(within(dialog).queryByRole('button', { name: /cộng Credit/ })).toBeNull();
  });

  it('đánh dấu đã hoàn tiền ngoài hệ thống gửi hành động REFUNDED_OUTSIDE', async () => {
    const user = userEvent.setup();
    vi.mocked(adminFinanceApi.reconcileTopUp).mockResolvedValue(
      detailOf({ ...flaggedOrder, needsReconcile: false, reviewNote: 'Đã hoàn qua ngân hàng' }),
    );
    render(<AdminTopUpOrders />);
    await screen.findByText('Chi Lê');
    await user.click(screen.getByRole('button', { name: '#ABCD...3456' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(
      await within(dialog).findByRole('button', { name: 'Đánh dấu đã hoàn tiền ngoài hệ thống' }),
    );

    await user.type(
      within(dialog).getByLabelText('Lý do hoặc ghi chú (bắt buộc)'),
      'Đã hoàn qua ngân hàng',
    );
    await user.click(within(dialog).getByRole('button', { name: 'Xác nhận' }));

    await waitFor(() =>
      expect(adminFinanceApi.reconcileTopUp).toHaveBeenCalledWith(FLAGGED_CODE, {
        action: 'REFUNDED_OUTSIDE',
        note: 'Đã hoàn qua ngân hàng',
      }),
    );
  });

  it('báo lỗi trong hộp khi đơn đã được xử lý trước đó', async () => {
    const user = userEvent.setup();
    vi.mocked(adminFinanceApi.reconcileTopUp).mockRejectedValue({
      status: 409,
      message: 'Đơn nạp này không cần đối soát hoặc đã được xử lý.',
    });
    render(<AdminTopUpOrders />);
    await screen.findByText('Chi Lê');
    await user.click(screen.getByRole('button', { name: '#ABCD...3456' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(
      await within(dialog).findByRole('button', { name: 'Xác nhận đã thu tiền và cộng Credit' }),
    );
    await user.type(within(dialog).getByLabelText('Lý do hoặc ghi chú (bắt buộc)'), 'Thử lần hai');
    await user.click(within(dialog).getByRole('button', { name: 'Xác nhận' }));

    expect(
      await within(dialog).findByText('Đơn nạp này không cần đối soát hoặc đã được xử lý.'),
    ).toBeTruthy();
  });

  it('xuất Excel theo ngày thanh toán và bộ lọc đang chọn', async () => {
    const user = userEvent.setup();
    const createObjectURL = vi.fn(() => 'blob:xlsx');
    const revokeObjectURL = vi.fn();
    Object.assign(URL, { createObjectURL, revokeObjectURL });
    // jsdom không điều hướng khi bấm thẻ a tải file nên chặn lại cho khỏi báo lỗi.
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    vi.mocked(adminFinanceApi.topUpOrdersExcel).mockResolvedValue({
      blob: new Blob(['a']),
      fileName: 'giao-dich-nap-tien.xlsx',
    });
    render(
      <AdminTopUpOrders path="/admin/finance/topup-orders?from=2026-10-06&to=2026-10-06&paidDate=true" />,
    );
    await screen.findByText('Lan An');
    await user.click(screen.getByLabelText('Chỉ đơn cần đối soát'));

    await user.click(screen.getByRole('button', { name: 'Xuất Excel' }));

    await waitFor(() =>
      expect(adminFinanceApi.topUpOrdersExcel).toHaveBeenCalledWith({
        from: '2026-10-06',
        to: '2026-10-06',
        paidDate: true,
        needsReconcile: true,
      }),
    );
    expect(createObjectURL).toHaveBeenCalled();
    expect((click.mock.contexts[0] as HTMLAnchorElement).download).toBe('giao-dich-nap-tien.xlsx');
  });

  it('báo lỗi khi tải danh sách thất bại và cho thử lại ngay trong khung lỗi', async () => {
    // given
    const user = userEvent.setup();
    vi.mocked(adminFinanceApi.topUpOrders)
      .mockRejectedValueOnce({
        status: 0,
        message: 'Không kết nối được máy chủ, vui lòng thử lại.',
      })
      .mockResolvedValueOnce({ items: orders, page: 0, size: 10, totalElements: orders.length });
    render(<AdminTopUpOrders />);
    expect(await screen.findByText('Không kết nối được máy chủ, vui lòng thử lại.')).toBeTruthy();

    // when
    await user.click(screen.getByRole('button', { name: 'Thử lại' }));

    // then
    expect(await screen.findByText('Lan An')).toBeTruthy();
    expect(screen.queryByText('Không kết nối được máy chủ, vui lòng thử lại.')).toBeNull();
  });

  it('hiển thị tiêu đề bảng mới, nút xem chi tiết và thay mã VNPay bằng 0 thành dấu gạch', async () => {
    const zeroTransaction = order({ providerTxnId: '0' });
    vi.mocked(adminFinanceApi.topUpOrders).mockResolvedValue({
      items: [zeroTransaction],
      page: 0,
      size: 10,
      totalElements: 1,
    });
    render(<AdminTopUpOrders />);

    const row = (await screen.findByText('Lan An')).closest('tr') as HTMLElement;
    expect(screen.getByRole('columnheader', { name: 'Mã đơn nạp' })).toBeTruthy();
    expect(screen.getByRole('columnheader', { name: 'Số tiền / Credit' })).toBeTruthy();
    expect(screen.getByRole('columnheader', { name: 'Trạng thái thanh toán' })).toBeTruthy();
    expect(screen.getByRole('columnheader', { name: 'Thời gian giao dịch' })).toBeTruthy();
    expect(within(row).getByText('—', { selector: '.admin-topup-provider-code' })).toBeTruthy();
    expect(within(row).getByRole('button', { name: 'Xem chi tiết' })).toBeTruthy();
    expect(within(row).queryByText('Chép')).toBeNull();
    expect(within(row).getByRole('button', { name: 'Sao chép mã đơn' })).toBeTruthy();
  });

  it('giữ badge Đã hủy màu đỏ trong bảng và modal chi tiết', async () => {
    const user = userEvent.setup();
    const cancelledOrder = order({ status: 'CANCELLED' });
    vi.mocked(adminFinanceApi.topUpOrders).mockResolvedValue({
      items: [cancelledOrder],
      page: 0,
      size: 10,
      totalElements: 1,
    });
    vi.mocked(adminFinanceApi.topUpOrderDetail).mockResolvedValue(detailOf(cancelledOrder));
    render(<AdminTopUpOrders />);

    const row = (await screen.findByText('Lan An')).closest('tr') as HTMLElement;
    expect(within(row).getByText('Đã hủy').className).toContain('admin-topup-status--cancelled');
    await user.click(within(row).getByRole('button', { name: 'Xem chi tiết' }));

    const dialog = await screen.findByRole('dialog', { name: 'Chi tiết đơn nạp' });
    expect(within(dialog).getByText('Đã hủy').className).toContain('admin-topup-status--cancelled');
  });
});
