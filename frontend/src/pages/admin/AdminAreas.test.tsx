// @vitest-environment jsdom

import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { adminAreaApi, type AdminAreaResponse } from '../../services/adminAreaApi';
import { AdminContentPage } from './AdminPages';

vi.mock('../../services/adminAreaApi', () => ({
  adminAreaApi: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    visibleItemCount: vi.fn(),
    setActive: vi.fn(),
  },
}));

const areas: AdminAreaResponse[] = [
  { id: 1, name: 'Hồ Chí Minh', parentId: null, level: 1, active: true },
  { id: 2, name: 'Quận 1', parentId: 1, level: 2, active: true },
  { id: 3, name: 'Thủ Đức', parentId: 1, level: 2, active: false },
];

function renderPage() {
  return render(<AdminContentPage kind="districts" />);
}

function tableNames() {
  return within(screen.getByRole('table'))
    .getAllByRole('row')
    .slice(1)
    .map((row) => (row as HTMLTableRowElement).cells[0].textContent);
}

beforeEach(() => {
  vi.mocked(adminAreaApi.list).mockResolvedValue(areas);
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('Quản lý khu vực', () => {
  it('thẻ thống kê đếm từ API và tự cập nhật sau khi kích hoạt khu vực', async () => {
    const user = userEvent.setup();
    vi.mocked(adminAreaApi.setActive).mockResolvedValue({ ...areas[2], active: true });
    renderPage();
    await screen.findByText('Quận 1');

    const stats = screen.getByLabelText('Thống kê khu vực');
    const value = (label: string) => within(stats).getByText(label).parentElement?.querySelector('strong')?.textContent;
    expect(value('Tổng khu vực')).toBe('3');
    expect(value('Đang sử dụng')).toBe('2');
    expect(value('Đã tạm ẩn')).toBe('1');

    await user.click(screen.getByRole('button', { name: 'Kích hoạt' }));
    await waitFor(() => expect(value('Đã tạm ẩn')).toBe('0'));
    expect(value('Đang sử dụng')).toBe('3');
  });

  it('hiện badge trạng thái, chữ Không có cho khu vực cha trống và căn giữa các cột', async () => {
    renderPage();
    await screen.findByText('Quận 1');
    const table = screen.getByRole('table');

    expect(within(table).queryByText('Đang dùng')).toBeNull();
    expect(within(table).getAllByText('Đang sử dụng')[0].className).toContain('text-success');
    expect(within(table).getByText('Đã tạm ẩn').className).toContain('text-text-muted');
    // Tên thành phố còn hiện ở cột Khu vực cha của các quận, nên lấy theo dòng đầu tiên.
    const cityRow = within(table).getAllByRole('row')[1] as HTMLTableRowElement;
    expect(cityRow.cells[0].textContent).toBe('Hồ Chí Minh');
    expect(cityRow.cells[1].textContent).toBe('Không có');
    expect(within(table).queryByText('—')).toBeNull();
    for (const name of ['Cấp', 'Trạng thái', 'Thao tác']) {
      expect(screen.getByRole('columnheader', { name }).className).toContain('text-center');
    }
    expect(within(cityRow).getByRole('button', { name: 'Sửa' }).parentElement?.className).toContain('flex-nowrap');
  });

  it('tìm theo tên không phân biệt dấu và lọc theo cấp, trạng thái', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Quận 1');
    // Lấy ô chọn một lần: danh sách tùy biến mở ra sau khi chọn cũng mang cùng nhãn.
    const level = screen.getByLabelText<HTMLSelectElement>('Cấp khu vực');
    const status = screen.getByLabelText<HTMLSelectElement>('Trạng thái');
    const search = screen.getByRole('textbox', { name: 'Tìm khu vực' });

    await user.type(search, 'thu duc');
    expect(tableNames()).toEqual(['Thủ Đức']);
    await user.clear(search);

    await user.selectOptions(level, '1');
    expect(tableNames()).toEqual(['Hồ Chí Minh']);
    await user.selectOptions(level, '2');
    await user.selectOptions(status, 'active');
    expect(tableNames()).toEqual(['Quận 1']);

    await user.type(search, 'không có');
    expect(screen.getByText('Không tìm thấy khu vực phù hợp')).toBeTruthy();
    expect(adminAreaApi.list).toHaveBeenCalledTimes(1);
  });
});
