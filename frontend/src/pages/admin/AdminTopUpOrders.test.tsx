// @vitest-environment jsdom

import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { adminFinanceApi } from '../../services/adminFinanceApi';
import type { TopUpOrderDetailResponse } from '../../services/paymentApi';
import { AdminTopUpOrders } from './AdminTopUpOrders';

vi.mock('../../services/adminFinanceApi', () => ({
  adminFinanceApi: { topUpOrders: vi.fn() },
}));

function order(overrides: Partial<TopUpOrderDetailResponse>): TopUpOrderDetailResponse {
  return {
    orderCode: 'SL5F3A9C0D1E2B44A7B8C9D0E1F2A3B4C',
    amountVnd: 50000,
    credits: 50,
    status: 'PENDING',
    provider: 'VNPAY',
    userId: 11,
    vndPerCredit: 1000,
    createdAt: '2026-10-20T03:00:00Z',
    expiresAt: '2026-10-20T04:00:00Z',
    submittedAt: null,
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
    reviewedAt: '2026-10-19T03:00:00Z',
  }),
  order({
    orderCode: 'SLREJECT',
    status: 'FAILED',
    userName: 'Chi Lê',
    userEmail: 'chi@example.com',
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

describe('AdminTopUpOrders', () => {
  it('mở trang là tải tất cả đơn mà không ép trạng thái', async () => {
    render(<AdminTopUpOrders />);

    await screen.findByText('Lan An');
    expect(adminFinanceApi.topUpOrders).toHaveBeenCalledWith({ page: 0, size: 10 });
    expect((screen.getByLabelText('Trạng thái') as HTMLSelectElement).value).toBe('');
  });

  it('hiện mã đơn công khai, người dùng và trạng thái, không hiện định danh kỹ thuật', async () => {
    render(<AdminTopUpOrders />);
    await screen.findByText('Lan An');

    expect(screen.getByText('an@example.com')).toBeTruthy();
    expect(screen.getAllByText('Thành công').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Thất bại').length).toBeGreaterThan(0);
    const page = document.body.textContent ?? '';
    expect(screen.getByText(orders[0].orderCode)).toBeTruthy();
    expect(screen.getAllByText('VNPay').length).toBeGreaterThan(0);
    for (const hidden of ['User #', 'Admin:', 'VNPAY', '/Credit', 'Nguồn']) {
      expect(page).not.toContain(hidden);
    }
  });

  it('mở đúng đơn từ dữ liệu đã tải, không gọi API thêm và không có thao tác tài chính', async () => {
    const user = userEvent.setup();
    render(<AdminTopUpOrders />);
    await screen.findByText('Lan An');
    const callsBefore = vi.mocked(adminFinanceApi.topUpOrders).mock.calls.length;
    await user.click(screen.getByRole('button', { name: `Xem Đơn nạp ${orders[0].orderCode}` }));
    const dialog = screen.getByRole('dialog', { name: `Đơn nạp ${orders[0].orderCode}` });
    expect(within(dialog).getByText('an@example.com')).toBeTruthy();
    expect(within(dialog).queryByRole('button', { name: /xác nhận|cộng|đối soát/i })).toBeNull();
    expect(adminFinanceApi.topUpOrders).toHaveBeenCalledTimes(callsBefore);
  });

  it('chỉ hiển thị danh sách theo dõi, không có thao tác duyệt thủ công', async () => {
    render(<AdminTopUpOrders />);
    await screen.findByText('Lan An');

    expect(screen.queryByRole('button', { name: 'Phê duyệt' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Từ chối' })).toBeNull();
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

  it('báo lỗi khi tải danh sách thất bại và cho thử lại ngay trong khung lỗi', async () => {
    // given
    const user = userEvent.setup();
    vi.mocked(adminFinanceApi.topUpOrders)
      .mockRejectedValueOnce({ status: 0, message: 'Không kết nối được máy chủ, vui lòng thử lại.' })
      .mockResolvedValueOnce({ items: orders, page: 0, size: 10, totalElements: orders.length });
    render(<AdminTopUpOrders />);
    expect(await screen.findByText('Không kết nối được máy chủ, vui lòng thử lại.')).toBeTruthy();

    // when
    await user.click(screen.getByRole('button', { name: 'Thử lại' }));

    // then
    expect(await screen.findByText('Lan An')).toBeTruthy();
    expect(screen.queryByText('Không kết nối được máy chủ, vui lòng thử lại.')).toBeNull();
  });
});
