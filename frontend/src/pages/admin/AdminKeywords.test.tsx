// @vitest-environment jsdom

import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { adminLexiconApi, type AdminLexiconEntry } from '../../services/adminLexiconApi';
import { AdminContentPage } from './AdminPages';

vi.mock('../../services/adminLexiconApi', () => ({
  adminLexiconApi: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    setActive: vi.fn(),
    test: vi.fn(),
  },
}));

function lexiconEntry(id: number): AdminLexiconEntry {
  return {
    id,
    keyword: `Từ khóa ${id}`,
    group: 'CONTACT',
    level: 'BLOCK',
    matchMode: null,
    exceptions: [],
    scope: 'POST',
    sortOrder: id,
    active: true,
  };
}

const entries = Array.from({ length: 21 }, (_, index) => lexiconEntry(index + 1));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('Từ khóa kiểm duyệt', () => {
  beforeEach(() => {
    vi.mocked(adminLexiconApi.list).mockResolvedValue(entries);
    vi.mocked(adminLexiconApi.setActive).mockImplementation(async (id, active) => ({
      ...entries.find((entry) => entry.id === id)!,
      active,
    }));
    vi.mocked(adminLexiconApi.update).mockImplementation(async (id, input) => ({
      ...entries.find((entry) => entry.id === id)!,
      ...input,
    }));
  });

  it('hiện 20 dòng mỗi trang và giữ trang hiện tại sau khi tạm ngưng hoặc sửa', async () => {
    const user = userEvent.setup();
    render(<AdminContentPage kind="keywords" />);

    const firstPageTable = (await screen.findByText('Từ khóa 1')).closest('table')!;
    expect(within(firstPageTable).getAllByRole('row')).toHaveLength(21);
    expect(screen.queryByText('Từ khóa 21')).toBeNull();

    await user.click(screen.getByRole('button', { name: 'Trang sau' }));
    expect(await screen.findByText('Từ khóa 21')).toBeTruthy();
    expect(screen.getByText('Hiển thị 21–21 trên 21 kết quả')).toBeTruthy();

    await user.click(screen.getByRole('button', { name: 'Tạm ngưng' }));
    expect(await within(screen.getByRole('table')).findByText('Đã tạm ngưng')).toBeTruthy();
    expect(screen.getByText('Hiển thị 21–21 trên 21 kết quả')).toBeTruthy();

    await user.click(screen.getByRole('button', { name: 'Sửa' }));
    const keywordInput = screen.getByLabelText('Từ khóa');
    await user.clear(keywordInput);
    await user.type(keywordInput, 'Từ khóa đã sửa');
    await user.click(screen.getByRole('button', { name: 'Lưu' }));

    await waitFor(() => expect(adminLexiconApi.update).toHaveBeenCalled());
    expect(await screen.findByText('Từ khóa đã sửa')).toBeTruthy();
    expect(screen.getByText('Hiển thị 21–21 trên 21 kết quả')).toBeTruthy();
  });

  it('thẻ thống kê đếm từ API, badge đúng màu và nhãn Đang hoạt động thay cho Đang hiển thị', async () => {
    vi.mocked(adminLexiconApi.list).mockResolvedValue([
      { ...lexiconEntry(1), keyword: 'zalo', level: 'REVIEW' },
      { ...lexiconEntry(2), keyword: 'dao găm', group: 'WEAPONS' },
      { ...lexiconEntry(3), keyword: 'ma túy', group: 'DRUGS', active: false },
    ]);
    render(<AdminContentPage kind="keywords" />);
    await screen.findByText('zalo');

    const stats = screen.getByLabelText('Thống kê từ khóa');
    const value = (label: string) => within(stats).getByText(label).parentElement?.querySelector('strong')?.textContent;
    expect(value('Tổng từ khóa')).toBe('3');
    expect(value('Đang hoạt động')).toBe('2');
    expect(value('Đã tạm ngưng')).toBe('1');

    const table = screen.getByRole('table');
    expect(within(table).queryByText('Đang hiển thị')).toBeNull();
    expect(within(table).getByText('Cần duyệt thủ công').className).toContain('text-warning');
    expect(within(table).getAllByText('Chặn đăng')[0].className).toContain('text-error');
    expect(within(table).getAllByText('Đang hoạt động')[0].className).toContain('text-success');
    expect(within(table).getByText('Đã tạm ngưng').className).toContain('text-text-muted');
    expect(screen.getByRole('columnheader', { name: 'Thao tác' }).className).toContain('text-center');
  });

  it('tìm theo từ khóa không phân biệt dấu và lọc theo nhóm, mức độ, trạng thái', async () => {
    const user = userEvent.setup();
    vi.mocked(adminLexiconApi.list).mockResolvedValue([
      { ...lexiconEntry(1), keyword: 'zalo', level: 'REVIEW' },
      { ...lexiconEntry(2), keyword: 'dao găm', group: 'WEAPONS' },
      { ...lexiconEntry(3), keyword: 'ma túy', group: 'DRUGS', active: false },
    ]);
    render(<AdminContentPage kind="keywords" />);
    await screen.findByText('zalo');
    const rows = () => within(screen.getByRole('table')).getAllByRole('row').slice(1).map((row) => (row as HTMLTableRowElement).cells[0].textContent);
    // Lấy ô chọn một lần: danh sách tùy biến mở ra sau khi chọn cũng mang cùng nhãn.
    const group = screen.getByLabelText<HTMLSelectElement>('Nhóm');
    const level = screen.getByLabelText<HTMLSelectElement>('Mức độ');
    const status = screen.getByLabelText<HTMLSelectElement>('Trạng thái');

    await user.type(screen.getByRole('textbox', { name: 'Tìm từ khóa' }), 'ma tuy');
    expect(rows()).toEqual(['ma túy']);
    await user.clear(screen.getByRole('textbox', { name: 'Tìm từ khóa' }));

    await user.selectOptions(group, 'WEAPONS');
    expect(rows()).toEqual(['dao găm']);
    await user.selectOptions(group, '');

    await user.selectOptions(level, 'REVIEW');
    expect(rows()).toEqual(['zalo']);
    await user.selectOptions(level, '');

    await user.selectOptions(status, 'inactive');
    expect(rows()).toEqual(['ma túy']);

    await user.type(screen.getByRole('textbox', { name: 'Tìm từ khóa' }), 'zalo');
    expect(screen.getByText('Không tìm thấy từ khóa phù hợp')).toBeTruthy();
    expect(adminLexiconApi.list).toHaveBeenCalledTimes(1);
  });

  it('khung kiểm tra nội dung cao khoảng 100px và nút Kiểm tra nằm bên phải', async () => {
    render(<AdminContentPage kind="keywords" />);
    await screen.findByText('Từ khóa 1');

    const textarea = screen.getByRole('textbox', { name: 'Kiểm tra nội dung' });
    expect(textarea.className).toContain('h-[100px]');
    expect(screen.getByRole('button', { name: 'Kiểm tra' }).parentElement?.className).toContain('justify-end');
  });
});
