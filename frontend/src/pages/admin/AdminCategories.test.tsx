// @vitest-environment jsdom

import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { adminCategoryApi, type AdminCategoryResponse } from '../../services/adminCategoryApi';
import { ApiAdminCategories } from './AdminPages';

vi.mock('../../services/adminCategoryApi', () => ({
  adminCategoryApi: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    setActive: vi.fn(),
    reorder: vi.fn(),
  },
}));

function category(id: number, name: string, sortOrder: number): AdminCategoryResponse {
  return { id, name, parentId: null, restricted: false, boostable: false, sortOrder, active: true };
}

// Dữ liệu cố ý lộn xộn: danh mục tên "10" có sortOrder 26 nhưng Backend trả về đầu danh sách.
const unsorted = [category(5, '10', 26), category(1, 'Đồ điện tử', 1), category(2, 'Quần áo', 2)];

beforeEach(() => {
  vi.mocked(adminCategoryApi.list).mockResolvedValue(unsorted);
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function names() {
  return screen
    .getAllByRole('row')
    .slice(1)
    .map((row) => within(row).getAllByRole('cell')[1].textContent);
}

describe('ApiAdminCategories', () => {
  it('luôn sắp theo sortOrder tăng dần và hiện vị trí 1, 2, 3 thay cho giá trị sortOrder', async () => {
    render(<ApiAdminCategories />);

    await screen.findByText('Đồ điện tử');
    expect(names()).toEqual(['Đồ điện tử', 'Quần áo', '10']);
    const positions = screen.getAllByRole('row').slice(1).map((row) => within(row).getAllByRole('cell')[0].textContent);
    expect(positions).toEqual(['1', '2', '3']);
    expect(screen.queryByText('26')).toBeNull();
  });

  it('khóa nút chuyển lên ở dòng đầu và nút chuyển xuống ở dòng cuối', async () => {
    render(<ApiAdminCategories />);
    await screen.findByText('Đồ điện tử');

    const rows = screen.getAllByRole('row').slice(1);
    const up = within(rows[1]).getByRole('button', { name: 'Chuyển lên' });
    const down = within(rows[1]).getByRole('button', { name: 'Chuyển xuống' });
    expect(up.textContent).toBe('');
    expect(down.textContent).toBe('');
    expect(up.querySelector('svg')?.classList.contains('-rotate-90')).toBe(true);
    expect(down.querySelector('svg')?.classList.contains('rotate-90')).toBe(true);
    expect(up.getAttribute('title')).toBe('Đưa danh mục này lên trên một vị trí');
    expect(down.getAttribute('title')).toBe('Đưa danh mục này xuống dưới một vị trí');
    expect((within(rows[0]).getByRole('button', { name: 'Chuyển lên' }) as HTMLButtonElement).disabled).toBe(true);
    expect((within(rows[2]).getByRole('button', { name: 'Chuyển xuống' }) as HTMLButtonElement).disabled).toBe(true);
  });

  it('chuyển xuống gửi toàn bộ id theo thứ tự mới rồi hiện danh sách Backend trả về', async () => {
    const user = userEvent.setup();
    vi.mocked(adminCategoryApi.reorder).mockResolvedValue([
      category(2, 'Quần áo', 1),
      category(1, 'Đồ điện tử', 2),
      category(5, '10', 3),
    ]);
    render(<ApiAdminCategories />);
    await screen.findByText('Đồ điện tử');

    const firstRow = screen.getAllByRole('row')[1];
    await user.click(within(firstRow).getByRole('button', { name: 'Chuyển xuống' }));

    await waitFor(() => expect(adminCategoryApi.reorder).toHaveBeenCalledWith([2, 1, 5]));
    await waitFor(() => expect(names()).toEqual(['Quần áo', 'Đồ điện tử', '10']));
    expect(screen.getByText('Đã cập nhật thứ tự danh mục.')).toBeTruthy();
  });

  it('giữ thứ tự cũ và báo lỗi khi đổi thứ tự thất bại', async () => {
    const user = userEvent.setup();
    vi.mocked(adminCategoryApi.reorder).mockRejectedValue({ message: 'Danh sách danh mục đã thay đổi.' });
    render(<ApiAdminCategories />);
    await screen.findByText('Đồ điện tử');

    await user.click(within(screen.getAllByRole('row')[1]).getByRole('button', { name: 'Chuyển xuống' }));

    expect(await screen.findByText('Danh sách danh mục đã thay đổi.')).toBeTruthy();
    expect(names()).toEqual(['Đồ điện tử', 'Quần áo', '10']);
  });

  it('thêm danh mục mới không gửi thứ tự và đặt ở cuối bảng', async () => {
    const user = userEvent.setup();
    vi.mocked(adminCategoryApi.create).mockResolvedValue(category(9, 'Nội thất', 27));
    render(<ApiAdminCategories />);
    await screen.findByText('Đồ điện tử');

    await user.click(screen.getByRole('button', { name: 'Thêm danh mục' }));
    const dialog = screen.getByRole('dialog', { name: 'Thêm danh mục' });
    expect(within(dialog).queryByText('Danh mục hạn chế')).toBeNull();
    expect(dialog.querySelectorAll('input[type="checkbox"]')).toHaveLength(1);
    expect(within(dialog).getByRole('checkbox', { name: 'Cho phép đẩy tin' })).toBeTruthy();
    await user.type(screen.getByRole('textbox', { name: 'Tên danh mục' }), 'Nội thất');
    await user.click(screen.getByRole('button', { name: 'Lưu' }));

    await waitFor(() =>
      expect(adminCategoryApi.create).toHaveBeenCalledWith({
        name: 'Nội thất',
        parentId: null,
        restricted: false,
        boostable: false,
      }),
    );
    await waitFor(() => expect(names()).toEqual(['Đồ điện tử', 'Quần áo', '10', 'Nội thất']));
  });
});
