// @vitest-environment jsdom

import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { adminFinanceApi } from '../../services/adminFinanceApi';
import type { TopUpOrderDetailResponse } from '../../services/paymentApi';
import { AdminTopUpOrders } from './AdminTopUpOrders';

vi.mock('../../services/adminFinanceApi', () => ({
  adminFinanceApi: { topUpOrders: vi.fn(), approveTopUp: vi.fn(), rejectTopUp: vi.fn() },
}));

function order(overrides: Partial<TopUpOrderDetailResponse>): TopUpOrderDetailResponse {
  return {
    orderCode: 'SL5F3A9C0D1E2B44A7B8C9D0E1F2A3B4C',
    amountVnd: 50000,
    credits: 50,
    status: 'PENDING',
    provider: 'MANUAL',
    userId: 11,
    vndPerCredit: 1000,
    createdAt: '2026-10-20T03:00:00Z',
    expiresAt: '2026-10-20T04:00:00Z',
    submittedAt: '2026-10-20T03:10:00Z',
    reviewedBy: null,
    reviewedAt: null,
    reviewNote: null,
    userName: 'Lan An',
    userEmail: 'an@example.com',
    reviewedByName: null,
    ...overrides,
  };
}

const orders = [
  order({}),
  order({
    orderCode: 'SLDONE',
    status: 'SUCCESS',
    userName: 'Bình Trần',
    userEmail: 'binh@example.com',
    reviewedBy: 9,
    reviewedByName: 'Quản trị Minh',
    reviewedAt: '2026-10-19T03:00:00Z',
    reviewNote: 'Đã đối chiếu sao kê',
  }),
  order({
    orderCode: 'SLREJECT',
    status: 'FAILED',
    userName: 'Chi Lê',
    userEmail: 'chi@example.com',
    reviewedBy: 9,
    reviewedByName: 'Quản trị Minh',
    reviewedAt: '2026-10-18T03:00:00Z',
  }),
];

beforeEach(() => {
  vi.mocked(adminFinanceApi.topUpOrders).mockResolvedValue({
    items: orders,
    page: 0,
    size: 10,
    totalElements: orders.length,
  });
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function rowOf(name: string) {
  return screen.getByText(name).closest('tr')!;
}

describe('AdminTopUpOrders', () => {
  it('mở trang là tải tất cả đơn: không ép trạng thái và không chỉ lấy đơn đã xác nhận', async () => {
    render(<AdminTopUpOrders />);

    await screen.findByText('Lan An');
    expect(adminFinanceApi.topUpOrders).toHaveBeenCalledWith({ page: 0, size: 10 });
    expect((screen.getByLabelText('Trạng thái') as HTMLSelectElement).value).toBe('');
    expect((screen.getByLabelText('Chỉ đơn đã xác nhận thanh toán') as HTMLInputElement).checked).toBe(false);
  });

  it('hiện tên người dùng và tên người duyệt, không hiện mã đơn, ID, cổng thanh toán hay tỷ giá', async () => {
    render(<AdminTopUpOrders />);
    await screen.findByText('Lan An');

    expect(screen.getByText('an@example.com')).toBeTruthy();
    expect(within(rowOf('Bình Trần')).getByText(/Người duyệt: Quản trị Minh/)).toBeTruthy();
    expect(within(rowOf('Bình Trần')).getByText('Thành công')).toBeTruthy();
    expect(within(rowOf('Chi Lê')).getByText('Từ chối')).toBeTruthy();
    const page = document.body.textContent ?? '';
    for (const hidden of ['SL5F3A9C0D1E2B44A', 'User #', 'Admin:', 'MANUAL', '/Credit', 'Nguồn', 'Không thể duyệt thủ công']) {
      expect(page).not.toContain(hidden);
    }
  });

  it('chỉ đơn chờ duyệt đã xác nhận có nút xử lý, đơn đã xử lý để dấu gạch', async () => {
    render(<AdminTopUpOrders />);
    await screen.findByText('Lan An');

    expect(within(rowOf('Lan An')).getByRole('button', { name: 'Phê duyệt' })).toBeTruthy();
    expect(within(rowOf('Bình Trần')).queryByRole('button')).toBeNull();
    expect(within(rowOf('Bình Trần')).getAllByText('—').length).toBeGreaterThan(0);
  });

  it('lọc theo trạng thái và checkbox gửi đúng tham số', async () => {
    const user = userEvent.setup();
    render(<AdminTopUpOrders />);
    await screen.findByText('Lan An');

    await user.selectOptions(screen.getByLabelText('Trạng thái'), 'PENDING');
    await user.click(screen.getByLabelText('Chỉ đơn đã xác nhận thanh toán'));

    await waitFor(() =>
      expect(adminFinanceApi.topUpOrders).toHaveBeenLastCalledWith({
        status: 'PENDING',
        submittedOnly: true,
        page: 0,
        size: 10,
      }),
    );
  });

  it('phê duyệt đơn xác nhận bằng tên người dùng rồi tải lại danh sách', async () => {
    const user = userEvent.setup();
    vi.mocked(adminFinanceApi.approveTopUp).mockResolvedValue(order({ status: 'SUCCESS' }));
    render(<AdminTopUpOrders />);
    await screen.findByText('Lan An');

    await user.click(within(rowOf('Lan An')).getByRole('button', { name: 'Phê duyệt' }));
    const dialog = screen.getByRole('dialog', { name: 'Xác nhận xử lý đơn nạp' });
    expect(within(dialog).getByText('Lan An')).toBeTruthy();
    expect(dialog.textContent).not.toContain('#11');
    await user.click(within(dialog).getByRole('button', { name: 'Xác nhận' }));

    await waitFor(() =>
      expect(adminFinanceApi.approveTopUp).toHaveBeenCalledWith('SL5F3A9C0D1E2B44A7B8C9D0E1F2A3B4C', ''),
    );
    expect(await screen.findByText(/Đã phê duyệt đơn nạp/)).toBeTruthy();
    expect(adminFinanceApi.topUpOrders).toHaveBeenCalledTimes(2);
  });

  it('từ chối đơn bắt buộc có lý do', async () => {
    const user = userEvent.setup();
    vi.mocked(adminFinanceApi.rejectTopUp).mockResolvedValue(order({ status: 'FAILED' }));
    render(<AdminTopUpOrders />);
    await screen.findByText('Lan An');

    await user.click(within(rowOf('Lan An')).getByRole('button', { name: 'Từ chối' }));
    const dialog = screen.getByRole('dialog', { name: 'Xác nhận xử lý đơn nạp' });
    const confirm = within(dialog).getByRole('button', { name: 'Xác nhận' }) as HTMLButtonElement;
    expect(confirm.disabled).toBe(true);
    await user.type(within(dialog).getByRole('textbox'), 'Chưa nhận được tiền');
    await user.click(confirm);

    await waitFor(() =>
      expect(adminFinanceApi.rejectTopUp).toHaveBeenCalledWith(
        'SL5F3A9C0D1E2B44A7B8C9D0E1F2A3B4C',
        'Chưa nhận được tiền',
      ),
    );
  });
});
