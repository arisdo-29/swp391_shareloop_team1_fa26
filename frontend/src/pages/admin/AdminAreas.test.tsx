// @vitest-environment jsdom
import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { adminAreaApi } from '../../services/adminAreaApi';
import { AdminContentPage } from './AdminPages';

vi.mock('../../services/adminAreaApi', () => ({
  adminAreaApi: { list: vi.fn(), create: vi.fn(), update: vi.fn(), setActive: vi.fn() },
}));
const areas = [
  { id: 11, name: 'Thành phố kiểm thử', parentId: null, level: 1, active: true },
  { id: 12, name: 'Quận kiểm thử', parentId: 11, level: 2, active: true },
];
beforeEach(() => vi.mocked(adminAreaApi.list).mockResolvedValue(areas));
afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('Admin areas presentation', () => {
  it('giữ quan hệ cha từ response và không thực hiện mutation khi xem', async () => {
    render(<AdminContentPage kind="districts" />);
    const row = (await screen.findByText('Quận kiểm thử')).closest('tr')!;
    expect(within(row).getByText('Thành phố kiểm thử')).toBeTruthy();
    expect(adminAreaApi.create).not.toHaveBeenCalled();
    expect(adminAreaApi.update).not.toHaveBeenCalled();
    expect(adminAreaApi.setActive).not.toHaveBeenCalled();
    expect(screen.queryByRole('button', { name: 'Xóa' })).toBeNull();
  });

  it('giữ validation tên trống và payload thêm khu vực, chỉ gửi khi bấm lưu', async () => {
    const user = userEvent.setup();
    vi.mocked(adminAreaApi.create).mockResolvedValue({ ...areas[1], id: 13, name: 'Quận mới' });
    render(<AdminContentPage kind="districts" />);
    await screen.findByText('Quận kiểm thử');
    await user.click(screen.getByRole('button', { name: 'Thêm khu vực' }));
    const dialog = screen.getByRole('dialog', { name: 'Thêm khu vực' });
    const save = within(dialog).getByRole('button', { name: 'Lưu' }) as HTMLButtonElement;
    expect(save.disabled).toBe(true);
    await user.type(within(dialog).getByLabelText('Tên khu vực'), ' Quận mới ');
    await user.selectOptions(within(dialog).getByLabelText('Khu vực cha'), '11');
    expect(adminAreaApi.create).not.toHaveBeenCalled();
    await user.click(save);
    await waitFor(() =>
      expect(adminAreaApi.create).toHaveBeenCalledWith({ name: 'Quận mới', parentId: 11 }),
    );
    expect(await screen.findByText('Đã thêm khu vực.')).toBeTruthy();
  });

  it('giữ trạng thái đang lưu và lỗi backend khi không thể tạm ẩn', async () => {
    const user = userEvent.setup();
    let reject!: (error: unknown) => void;
    vi.mocked(adminAreaApi.setActive).mockReturnValue(
      new Promise((_, fail) => {
        reject = fail;
      }),
    );
    render(<AdminContentPage kind="districts" />);
    const row = (await screen.findByText('Quận kiểm thử')).closest('tr')!;
    const toggle = within(row).getByRole('button', { name: 'Tạm ẩn' }) as HTMLButtonElement;
    await user.click(toggle);
    expect(adminAreaApi.setActive).toHaveBeenCalledWith(12, false);
    expect(toggle.disabled).toBe(true);
    reject({ status: 409, message: 'Khu vực đang được sử dụng.' });
    expect(await screen.findByText('Khu vực đang được sử dụng.')).toBeTruthy();
    expect(within(row).getByText('Đang dùng')).toBeTruthy();
  });
});
