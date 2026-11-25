// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  adminChecklistApi,
  type AdminChecklistEntry,
} from '../../services/adminChecklistApi';
import { AdminContentPage } from './AdminPages';

vi.mock('../../services/adminChecklistApi', () => ({
  adminChecklistApi: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    reorder: vi.fn(),
    setActive: vi.fn(),
  },
  ADMIN_CHECKLIST_QUESTION_MAX_LENGTH: 255,
  ADMIN_CHECKLIST_DESCRIPTION_MAX_LENGTH: 1000,
}));

const first: AdminChecklistEntry = {
  id: 11,
  key: 'real_photos',
  question: 'Ảnh là ảnh thật của đúng món đồ?',
  description: 'Đạt khi ảnh chụp rõ món đồ. Từ chối nếu dùng ảnh mạng.',
  sortOrder: 1,
  active: true,
};

const second: AdminChecklistEntry = {
  id: 12,
  key: 'category_match',
  question: 'Danh mục có đúng với món đồ?',
  description: 'Đối chiếu danh mục đã chọn với nội dung bài.',
  sortOrder: 2,
  active: false,
};

function renderPage() {
  return render(
    <MemoryRouter>
      <AdminContentPage kind="checklist" />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  // Cố ý trả sai thứ tự để kiểm tra UI luôn dùng sortOrder từ API.
  vi.mocked(adminChecklistApi.list).mockResolvedValue([second, first]);
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('Admin checklist management', () => {
  it('tải đủ câu hỏi, mô tả, trạng thái và sắp xếp theo sortOrder', async () => {
    renderPage();

    await screen.findByText(first.question);
    expect(screen.getByText(first.description!)).toBeTruthy();
    expect(screen.getByText(second.description!)).toBeTruthy();
    // Thẻ thống kê cũng có chữ "Đang hoạt động" nên kiểm nhãn trạng thái trong bảng.
    const table = screen.getByRole('table');
    expect(within(table).getByText('Đang hoạt động')).toBeTruthy();
    expect(within(table).getByText('Không hoạt động')).toBeTruthy();

    const rows = screen.getAllByRole('row').slice(1);
    expect(rows[0].textContent).toContain(first.question);
    expect(rows[1].textContent).toContain(second.question);
  });

  it('thêm và sửa cả câu hỏi lẫn description bằng dữ liệu API trả về', async () => {
    const user = userEvent.setup();
    const created: AdminChecklistEntry = {
      id: 13,
      key: 'complete_description',
      question: 'Mô tả có đầy đủ?',
      description: 'Đạt khi mô tả nêu rõ tình trạng món đồ.',
      sortOrder: 3,
      active: true,
    };
    vi.mocked(adminChecklistApi.create).mockResolvedValue(created);
    vi.mocked(adminChecklistApi.update).mockResolvedValue({
      ...first,
      question: 'Ảnh có đúng món đồ đang đăng?',
      description: 'Đạt khi tất cả ảnh đều là ảnh thật.',
    });
    renderPage();
    await screen.findByText(first.question);

    await user.click(screen.getByRole('button', { name: 'Thêm tiêu chí' }));
    let dialog = screen.getByRole('dialog', { name: 'Thêm tiêu chí' });
    fireEvent.change(within(dialog).getByRole('textbox', { name: 'Nội dung tiêu chí' }), {
      target: { value: created.question },
    });
    fireEvent.change(within(dialog).getByRole('textbox', { name: 'Mô tả điều kiện đạt' }), {
      target: { value: created.description },
    });
    await user.click(within(dialog).getByRole('button', { name: 'Lưu' }));

    await waitFor(() => expect(adminChecklistApi.create).toHaveBeenCalledWith({
      question: created.question,
      description: created.description,
    }));
    expect(await screen.findByText(created.description!)).toBeTruthy();

    const firstRow = screen.getByText(first.question).closest('tr')!;
    await user.click(within(firstRow).getByRole('button', { name: 'Sửa nội dung' }));
    dialog = screen.getByRole('dialog', { name: 'Sửa tiêu chí' });
    const question = within(dialog).getByRole('textbox', { name: 'Nội dung tiêu chí' });
    const description = within(dialog).getByRole('textbox', { name: 'Mô tả điều kiện đạt' });
    fireEvent.change(question, { target: { value: 'Ảnh có đúng món đồ đang đăng?' } });
    fireEvent.change(description, { target: { value: 'Đạt khi tất cả ảnh đều là ảnh thật.' } });
    await user.click(within(dialog).getByRole('button', { name: 'Lưu' }));

    await waitFor(() => expect(adminChecklistApi.update).toHaveBeenCalledWith(11, {
      question: 'Ảnh có đúng món đồ đang đăng?',
      description: 'Đạt khi tất cả ảnh đều là ảnh thật.',
    }));
    expect(await screen.findByText('Đạt khi tất cả ảnh đều là ảnh thật.')).toBeTruthy();
  });

  it('bật/tắt và đổi thứ tự bằng toàn bộ danh sách ID mà không tự xoá dữ liệu', async () => {
    const user = userEvent.setup();
    vi.mocked(adminChecklistApi.setActive).mockResolvedValue({ ...first, active: false });
    vi.mocked(adminChecklistApi.reorder).mockResolvedValue([
      { ...second, sortOrder: 1 },
      { ...first, active: false, sortOrder: 2 },
    ]);
    renderPage();
    await screen.findByText(first.question);

    const firstRow = screen.getByText(first.question).closest('tr')!;
    await user.click(within(firstRow).getByRole('button', { name: 'Tạm ẩn' }));
    await waitFor(() => expect(adminChecklistApi.setActive).toHaveBeenCalledWith(11, false));
    expect(screen.getAllByText('Không hoạt động')).toHaveLength(2);

    await user.click(screen.getByRole('button', { name: `Đưa ${second.question} lên trên` }));
    await waitFor(() => expect(adminChecklistApi.reorder).toHaveBeenCalledWith([12, 11]));
    expect(screen.getByText(first.description!).closest('tr')).toBeTruthy();
    expect(screen.getByText(second.description!).closest('tr')).toBeTruthy();
  });

  it('giữ nguyên danh sách và hiển thị lỗi khi lưu thứ tự thất bại', async () => {
    const user = userEvent.setup();
    vi.mocked(adminChecklistApi.reorder).mockRejectedValue({ status: 0 });
    renderPage();
    await screen.findByText(first.question);

    await user.click(screen.getByRole('button', { name: `Đưa ${second.question} lên trên` }));

    expect(await screen.findByText('Không kết nối được máy chủ. Vui lòng thử lại.')).toBeTruthy();
    const rows = screen.getAllByRole('row').slice(1);
    expect(rows[0].textContent).toContain(first.question);
    expect(rows[1].textContent).toContain(second.question);
  });

  it('ba thẻ thống kê đếm từ API và tự cập nhật sau khi kích hoạt hoặc tạm ẩn tiêu chí', async () => {
    const user = userEvent.setup();
    vi.mocked(adminChecklistApi.setActive)
      .mockResolvedValueOnce({ ...second, active: true })
      .mockResolvedValueOnce({ ...first, active: false });
    renderPage();
    await screen.findByText(first.question);

    const stats = screen.getByLabelText('Thống kê tiêu chí');
    const value = (label: string) => within(stats).getByText(label).parentElement?.querySelector('strong')?.textContent;
    expect(value('Tổng tiêu chí')).toBe('2');
    expect(value('Đang hoạt động')).toBe('1');
    expect(value('Đã tạm ẩn')).toBe('1');

    const secondRow = screen.getByText(second.question).closest('tr')!;
    await user.click(within(secondRow).getByRole('button', { name: 'Kích hoạt' }));
    await waitFor(() => expect(value('Đang hoạt động')).toBe('2'));
    expect(value('Đã tạm ẩn')).toBe('0');

    const firstRow = screen.getByText(first.question).closest('tr')!;
    await user.click(within(firstRow).getByRole('button', { name: 'Tạm ẩn' }));
    await waitFor(() => expect(value('Đã tạm ẩn')).toBe('1'));
  });

  it('nút đổi thứ tự chỉ còn icon mũi tên, có tooltip và các nút nằm trên một hàng', async () => {
    renderPage();
    await screen.findByText(first.question);

    const up = screen.getByRole('button', { name: `Đưa ${second.question} lên trên` });
    const down = screen.getByRole('button', { name: `Đưa ${first.question} xuống dưới` });
    expect(up.textContent).toBe('');
    expect(up.querySelector('svg')).toBeTruthy();
    expect(up.getAttribute('title')).toBe('Đưa mục này lên trên một vị trí');
    expect(down.getAttribute('title')).toBe('Đưa mục này xuống dưới một vị trí');
    expect(screen.queryByText(/Lên|Xuống/)).toBeNull();
    expect(up.parentElement?.className).toContain('flex-nowrap');
    expect(screen.getByRole('columnheader', { name: 'Thao tác' }).className).toContain('text-center');
  });
});
