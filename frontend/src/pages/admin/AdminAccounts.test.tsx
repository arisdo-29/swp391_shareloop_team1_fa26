// @vitest-environment jsdom

import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { adminFinanceApi } from '../../services/adminFinanceApi';
import { adminItemApi } from '../../services/adminItemApi';
import { adminUserApi, type AdminUserSummary } from '../../services/adminUserApi';
import { userApi } from '../../services/userApi';
import { AdminAccounts } from './AdminAccounts';

vi.mock('../../services/adminUserApi', () => ({ adminUserApi: { list: vi.fn() } }));
vi.mock('../../services/adminFinanceApi', () => ({
  adminFinanceApi: { setLocked: vi.fn(), userWallet: vi.fn() },
}));
vi.mock('../../services/adminItemApi', () => ({ adminItemApi: { list: vi.fn() } }));
vi.mock('../../services/userApi', () => ({ userApi: { getPublicProfile: vi.fn() } }));

function account(overrides: Partial<AdminUserSummary>): AdminUserSummary {
  return {
    id: 1,
    fullName: 'Lan An',
    email: 'an@example.com',
    phone: '0900000001',
    role: 'USER',
    status: 'ACTIVE',
    locked: false,
    createdAt: '2026-10-01T03:00:00Z',
    ...overrides,
  };
}

const accounts = [
  account({}),
  account({
    id: 2,
    fullName: 'Bình Trần',
    email: 'binh@example.com',
    status: 'LOCKED',
    locked: true,
  }),
  account({ id: 3, fullName: 'Quản trị Minh', email: 'admin@example.com', role: 'ADMIN' }),
  account({ id: 4, fullName: 'Chi Bị Cấm', email: 'chi@example.com', status: 'BANNED' }),
];

function renderPage() {
  return render(
    <MemoryRouter>
      <AdminAccounts />
    </MemoryRouter>,
  );
}

function rowOf(name: string) {
  return screen.getByText(name).closest('tr')!;
}

beforeEach(() => {
  vi.mocked(adminUserApi.list).mockImplementation(async (params) => {
    if (params.size === 1) {
      const totalElements = params.status === 'ACTIVE' ? 2 : params.status === 'LOCKED' ? 1 : 4;
      return { items: [], page: 0, size: 1, totalElements };
    }
    return { items: accounts, page: 0, size: 20, totalElements: accounts.length };
  });
  vi.mocked(userApi.getPublicProfile).mockImplementation(async (id) => ({
    id,
    fullName: accounts.find((entry) => entry.id === id)?.fullName ?? 'Người dùng',
    avatarUrl: id === 1 ? '/media/avatar-1.jpg' : null,
    avatarInitials: id === 1 ? 'LA' : 'ND',
    district: null,
    rank: 'BRONZE',
    trustStars: 4,
    completedTransactions: 7,
    joinedAt: '2026-10-01T03:00:00Z',
  }));
  vi.mocked(adminFinanceApi.userWallet).mockImplementation(async (userId) => ({
    userId,
    fullName: 'Lan An',
    email: 'an@example.com',
    phone: '0900000001',
    availableCredit: 20,
    heldCredit: 5,
    totalCredit: 25,
    totalToppedUpCredit: 50,
  }));
  vi.mocked(adminItemApi.list).mockResolvedValue({ items: [], page: 0, size: 1, totalElements: 6 });
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('AdminAccounts', () => {
  it('hiện danh sách với vai trò và trạng thái bằng tiếng Việt, không có ô nhập mã người dùng', async () => {
    renderPage();

    await screen.findByText('Lan An');
    expect(within(rowOf('Lan An')).getByText('Đang hoạt động')).toBeTruthy();
    expect(within(rowOf('Bình Trần')).getByText('Đã khóa')).toBeTruthy();
    expect(within(rowOf('Chi Bị Cấm')).getByText('Bị cấm')).toBeTruthy();
    expect(within(rowOf('Quản trị Minh')).getByText('Quản trị viên')).toBeTruthy();
    expect(screen.queryByText(/Mã người dùng/)).toBeNull();
    expect(adminUserApi.list).toHaveBeenCalledWith(expect.objectContaining({ page: 0, size: 20 }));
  });

  it('chỉ hiện nút khóa hoặc mở khóa đúng với trạng thái, quản trị viên và tài khoản bị cấm thì không', async () => {
    renderPage();
    await screen.findByText('Lan An');

    expect(within(rowOf('Lan An')).getByRole('button', { name: 'Khóa' })).toBeTruthy();
    expect(within(rowOf('Bình Trần')).getByRole('button', { name: 'Mở khóa' })).toBeTruthy();
    expect(within(rowOf('Quản trị Minh')).queryByRole('button', { name: /khóa/i })).toBeNull();
    expect(within(rowOf('Chi Bị Cấm')).queryByRole('button', { name: /khóa/i })).toBeNull();
  });

  it('khóa tài khoản phải nhập lý do rồi gọi API và tải lại danh sách', async () => {
    const user = userEvent.setup();
    vi.mocked(adminFinanceApi.setLocked).mockResolvedValue(undefined);
    renderPage();
    await screen.findByText('Lan An');

    await user.click(within(rowOf('Lan An')).getByRole('button', { name: 'Khóa' }));
    const dialog = screen.getByRole('dialog', { name: 'Khóa tài khoản' });
    const confirm = within(dialog).getByRole('button', {
      name: 'Xác nhận khóa',
    }) as HTMLButtonElement;
    expect(confirm.disabled).toBe(true);
    await user.type(within(dialog).getByRole('textbox', { name: /Lý do/ }), 'Đăng bài lừa đảo');
    await user.click(confirm);

    await waitFor(() =>
      expect(adminFinanceApi.setLocked).toHaveBeenCalledWith(1, true, 'Đăng bài lừa đảo'),
    );
    expect(await screen.findByText('Đã khóa tài khoản của Lan An.')).toBeTruthy();
    expect(
      vi.mocked(adminUserApi.list).mock.calls.filter(([params]) => params.size === 20),
    ).toHaveLength(2);
  });

  it('mở khóa gọi API với cờ mở khóa', async () => {
    const user = userEvent.setup();
    vi.mocked(adminFinanceApi.setLocked).mockResolvedValue(undefined);
    renderPage();
    await screen.findByText('Bình Trần');

    await user.click(within(rowOf('Bình Trần')).getByRole('button', { name: 'Mở khóa' }));
    const dialog = screen.getByRole('dialog', { name: 'Mở khóa tài khoản' });
    await user.type(within(dialog).getByRole('textbox', { name: /Lý do/ }), 'Đã xác minh lại');
    await user.click(within(dialog).getByRole('button', { name: 'Xác nhận mở khóa' }));

    await waitFor(() =>
      expect(adminFinanceApi.setLocked).toHaveBeenCalledWith(2, false, 'Đã xác minh lại'),
    );
  });

  it('giữ hộp thoại và hiện lỗi khi Backend từ chối', async () => {
    const user = userEvent.setup();
    vi.mocked(adminFinanceApi.setLocked).mockRejectedValue({ status: 403 });
    renderPage();
    await screen.findByText('Lan An');

    await user.click(within(rowOf('Lan An')).getByRole('button', { name: 'Khóa' }));
    const dialog = screen.getByRole('dialog', { name: 'Khóa tài khoản' });
    await user.type(within(dialog).getByRole('textbox', { name: /Lý do/ }), 'Vi phạm');
    await user.click(within(dialog).getByRole('button', { name: 'Xác nhận khóa' }));

    expect((await within(dialog).findByRole('alert')).textContent).toContain(
      'không có quyền quản trị',
    );
  });

  it('lọc theo trạng thái và vai trò gửi đúng tham số lên Backend', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Lan An');

    await user.selectOptions(screen.getByLabelText('Trạng thái'), 'LOCKED');
    await waitFor(() =>
      expect(adminUserApi.list).toHaveBeenLastCalledWith(
        expect.objectContaining({ status: 'LOCKED', role: undefined, page: 0 }),
      ),
    );
    await user.selectOptions(screen.getByLabelText('Vai trò'), 'USER');
    await waitFor(() =>
      expect(adminUserApi.list).toHaveBeenLastCalledWith(
        expect.objectContaining({ status: 'LOCKED', role: 'USER' }),
      ),
    );
  });

  it('tìm theo từ khóa sau khi người dùng ngừng gõ', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Lan An');

    await user.type(screen.getByRole('textbox', { name: 'Tìm tài khoản' }), 'binh');

    await waitFor(() =>
      expect(adminUserApi.list).toHaveBeenLastCalledWith(
        expect.objectContaining({ keyword: 'binh', page: 0 }),
      ),
    );
  });

  it('cột thao tác giữ các nút cùng hàng và có đủ chức năng', async () => {
    renderPage();
    await screen.findByText('Bình Trần');

    const row = rowOf('Bình Trần');
    const actionCell = within(row).getByRole('button', { name: 'Mở khóa' }).closest('td')!;
    const flexWrap = actionCell.querySelector('.admin-accounts-actions');
    expect(flexWrap).toBeTruthy();
    expect(within(actionCell).getByRole('button', { name: 'Xem chi tiết' })).toBeTruthy();
    expect(within(actionCell).getByText('Xem Credit')).toBeTruthy();
  });

  it('hiển thị thống kê toàn hệ thống từ các truy vấn riêng, không cộng dữ liệu trang hiện tại', async () => {
    renderPage();

    await screen.findByText('Lan An');
    expect(screen.getByText('Tổng tài khoản').parentElement?.textContent).toContain('4');
    expect(
      screen.getByText('Đang hoạt động', { selector: '.admin-accounts-stat p' }).parentElement
        ?.textContent,
    ).toContain('2');
    expect(
      screen.getByText('Đã khóa', { selector: '.admin-accounts-stat p' }).parentElement
        ?.textContent,
    ).toContain('1');
    expect(adminUserApi.list).toHaveBeenCalledWith({ page: 0, size: 1 });
    expect(adminUserApi.list).toHaveBeenCalledWith({ status: 'ACTIVE', page: 0, size: 1 });
    expect(adminUserApi.list).toHaveBeenCalledWith({ status: 'LOCKED', page: 0, size: 1 });
  });

  it('hiển thị avatar, Credit, uy tín và modal chi tiết bằng dữ liệu API hiện có', async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByText('Lan An');
    const row = rowOf('Lan An');
    expect(await within(row).findByText('25 Credit')).toBeTruthy();
    expect(row.querySelector('img')?.getAttribute('src')).toContain('/media/avatar-1.jpg');
    expect(within(row).getByText('4 sao')).toBeTruthy();
    await user.click(within(row).getByRole('button', { name: 'Xem chi tiết' }));

    const dialog = screen.getByRole('dialog', { name: 'Chi tiết tài khoản' });
    expect(await within(dialog).findByText('Tổng bài đăng')).toBeTruthy();
    expect(within(dialog).getByText('6')).toBeTruthy();
    expect(within(dialog).getByText('Giao dịch hoàn tất')).toBeTruthy();
    expect(within(dialog).getByText('7')).toBeTruthy();
    expect(
      within(dialog).getByRole('link', { name: 'Xem lịch sử Credit' }).getAttribute('href'),
    ).toBe('/admin/finance/users/1');
  });
});
