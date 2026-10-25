// @vitest-environment jsdom

import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { adminFinanceApi, type AdminFinanceUserSummary } from '../../services/adminFinanceApi';
import type { WalletTransactionResponse } from '../../services/walletApi';
import { AdminFinanceUserDetail, AdminFinanceUsers } from './AdminFinanceUsers';

vi.mock('../../services/adminFinanceApi', () => ({
  adminFinanceApi: { users: vi.fn(), userWallet: vi.fn(), userHistory: vi.fn() },
}));

const lanAn: AdminFinanceUserSummary = {
  userId: 7,
  fullName: 'Lan An',
  email: 'an@example.com',
  phone: '0900000007',
  availableCredit: 25,
  heldCredit: 5,
  totalCredit: 30,
  totalToppedUpCredit: 50,
};

function entry(
  id: number,
  type: WalletTransactionResponse['type'],
  amount: number,
): WalletTransactionResponse {
  return {
    id,
    type,
    amount,
    balanceAfter: 30,
    itemId: null,
    paymentOrderId: null,
    note: null,
    createdAt: '2026-10-05T03:00:00Z',
  };
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('AdminFinanceUsers', () => {
  beforeEach(() => {
    vi.mocked(adminFinanceApi.users).mockResolvedValue({
      items: [lanAn],
      page: 0,
      size: 20,
      totalElements: 1,
    });
  });

  it('mở trang là thấy ngay danh sách người dùng kèm số dư, không bắt nhập mã người dùng', async () => {
    render(
      <MemoryRouter>
        <AdminFinanceUsers />
      </MemoryRouter>,
    );

    const row = (await screen.findByText('Lan An')).closest('tr')!;
    for (const value of ['an@example.com', '0900000007', '25', '5', '30', '50']) {
      expect(within(row).getByText(value)).toBeTruthy();
    }
    expect(screen.queryByText(/Mã người dùng/)).toBeNull();
    expect(adminFinanceApi.users).toHaveBeenCalledWith({ keyword: undefined, page: 0, size: 20 });
    expect(within(row).getByRole('link', { name: 'Xem chi tiết' }).getAttribute('href')).toBe(
      '/admin/finance/users/7',
    );
  });

  it('tìm theo tên sau khi ngừng gõ', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <AdminFinanceUsers />
      </MemoryRouter>,
    );
    await screen.findByText('Lan An');

    await user.type(screen.getByRole('textbox', { name: 'Tìm người dùng' }), 'lan');

    await waitFor(() =>
      expect(adminFinanceApi.users).toHaveBeenLastCalledWith({ keyword: 'lan', page: 0, size: 20 }),
    );
  });

  it('báo không tìm thấy khi danh sách rỗng', async () => {
    vi.mocked(adminFinanceApi.users).mockResolvedValue({ items: [], page: 0, size: 20, totalElements: 0 });
    render(
      <MemoryRouter>
        <AdminFinanceUsers />
      </MemoryRouter>,
    );

    expect(await screen.findByText('Chưa có người dùng nào.')).toBeTruthy();
  });
});

describe('AdminFinanceUserDetail', () => {
  beforeEach(() => {
    vi.mocked(adminFinanceApi.userWallet).mockResolvedValue(lanAn);
    vi.mocked(adminFinanceApi.userHistory).mockResolvedValue({
      items: [entry(1, 'TOP_UP', 50), entry(2, 'POST_FEE', -5), entry(3, 'ADMIN_ADJUST', 3)],
      page: 0,
      size: 20,
      totalElements: 3,
    });
  });

  function renderDetail() {
    return render(
      <MemoryRouter initialEntries={['/admin/finance/users/7']}>
        <Routes>
          <Route path="/admin/finance/users/:userId" element={<AdminFinanceUserDetail />} />
        </Routes>
      </MemoryRouter>,
    );
  }

  it('hiện số dư và lịch sử chia theo nạp, cộng và trừ Credit như trang Credit của người dùng', async () => {
    const user = userEvent.setup();
    renderDetail();

    await screen.findByRole('heading', { name: 'Lan An' });
    expect(await screen.findByRole('button', { name: 'Tất cả (3)' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Nạp Credit (1)' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Cộng Credit (1)' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Trừ Credit (1)' })).toBeTruthy();
    expect(screen.getByText('Tổng đã nạp')).toBeTruthy();

    await user.click(screen.getByRole('button', { name: 'Trừ Credit (1)' }));
    expect(screen.getByText('Phí đăng bài')).toBeTruthy();
    expect(screen.queryByText('Cập nhật hệ thống')).toBeNull();
  });

  it('gửi khoảng ngày theo giờ Việt Nam khi lọc lịch sử', async () => {
    const user = userEvent.setup();
    renderDetail();
    await screen.findByRole('heading', { name: 'Lan An' });

    const fromInput = await screen.findByLabelText('Từ ngày');
    await user.type(fromInput, '2026-10-01');

    await waitFor(() =>
      expect(adminFinanceApi.userHistory).toHaveBeenLastCalledWith(
        7,
        expect.objectContaining({ from: '2026-09-30T17:00:00.000Z', page: 0, size: 20 }),
      ),
    );
  });

  it('hiện đúng nhãn phí sửa, gia hạn, đẩy và hoàn phí AI, không kèm tên món', async () => {
    // given
    const user = userEvent.setup();
    vi.mocked(adminFinanceApi.userHistory).mockResolvedValue({
      items: [
        { ...entry(1, 'EDIT_FEE', -2), itemId: 42 },
        { ...entry(2, 'RENEW_FEE', -3), itemId: 42 },
        { ...entry(3, 'BOOST_FEE', -4), itemId: 42 },
        { ...entry(4, 'AI_SEARCH_FEE', 2), note: 'Hoàn phí AI #9' },
      ],
      page: 0,
      size: 20,
      totalElements: 4,
    });

    // when
    renderDetail();
    await screen.findByRole('button', { name: 'Tất cả (4)' });

    // then
    expect(screen.getByText('Phí chỉnh sửa bài')).toBeTruthy();
    expect(screen.getByText('Phí gia hạn bài')).toBeTruthy();
    expect(screen.getByText('Phí đẩy tin')).toBeTruthy();
    expect(screen.getByText('Hoàn phí tìm kiếm AI')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Cộng Credit (1)' }));
    expect(screen.getByText('Hoàn phí tìm kiếm AI')).toBeTruthy();
    expect(screen.queryByText('Phí đẩy tin')).toBeNull();
  });

  it('báo lỗi rõ ràng khi không có người dùng', async () => {
    vi.mocked(adminFinanceApi.userWallet).mockRejectedValue({ status: 404 });
    renderDetail();

    expect(await screen.findByText('Không tìm thấy dữ liệu yêu cầu.')).toBeTruthy();
  });
});
