// @vitest-environment jsdom

import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { adminFinanceApi } from '../../services/adminFinanceApi';
import { adminUserApi, type AdminUserSummary } from '../../services/adminUserApi';
import { AdminAccounts } from './AdminAccounts';

vi.mock('../../services/adminUserApi', () => ({ adminUserApi: { list: vi.fn() } }));
vi.mock('../../services/adminFinanceApi', () => ({ adminFinanceApi: { setLocked: vi.fn() } }));

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
  account({ id: 2, fullName: 'Bình Trần', email: 'binh@example.com', status: 'LOCKED', locked: true }),
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
  vi.mocked(adminUserApi.list).mockResolvedValue({
    items: accounts,
    page: 0,
    size: 20,
    totalElements: accounts.length,
  });
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
    expect(adminUserApi.list).toHaveBeenCalledWith(
      expect.objectContaining({ page: 0, size: 20 }),
    );
  });

  it('chỉ hiện nút khóa hoặc mở khóa đúng với trạng thái, quản trị viên và tài khoản bị cấm thì không', async () => {
    renderPage();
    await screen.findByText('Lan An');

    expect(within(rowOf('Lan An')).getByRole('button', { name: 'Khóa' })).toBeTruthy();
    expect(within(rowOf('Bình Trần')).getByRole('button', { name: 'Mở khóa' })).toBeTruthy();
    expect(within(rowOf('Quản trị Minh')).queryByRole('button')).toBeNull();
    expect(within(rowOf('Chi Bị Cấm')).queryByRole('button', { name: /khóa/i })).toBeNull();
  });

  it('khóa tài khoản phải nhập lý do rồi gọi API và tải lại danh sách', async () => {
    const user = userEvent.setup();
    vi.mocked(adminFinanceApi.setLocked).mockResolvedValue(undefined);
    renderPage();
    await screen.findByText('Lan An');

    await user.click(within(rowOf('Lan An')).getByRole('button', { name: 'Khóa' }));
    const dialog = screen.getByRole('dialog', { name: 'Khóa tài khoản' });
    const confirm = within(dialog).getByRole('button', { name: 'Xác nhận khóa' }) as HTMLButtonElement;
    expect(confirm.disabled).toBe(true);
    await user.type(within(dialog).getByRole('textbox', { name: /Lý do/ }), 'Đăng bài lừa đảo');
    await user.click(confirm);

    await waitFor(() =>
      expect(adminFinanceApi.setLocked).toHaveBeenCalledWith(1, true, 'Đăng bài lừa đảo'),
    );
    expect(await screen.findByText('Đã khóa tài khoản của Lan An.')).toBeTruthy();
    expect(adminUserApi.list).toHaveBeenCalledTimes(2);
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

    expect((await within(dialog).findByRole('alert')).textContent).toContain('không có quyền quản trị');
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
});
