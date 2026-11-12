// @vitest-environment jsdom

import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { adminAuditApi, type ActivityLogPage, type ActivityLogResponse } from '../../services/adminAuditApi';
import { AdminAuditLogs } from './AdminPages';

vi.mock('../../services/adminAuditApi', () => ({
  adminAuditApi: { list: vi.fn(), types: vi.fn() },
}));

vi.mock('./AdminUserPicker', () => ({
  AdminUserPicker: () => <div data-testid="admin-user-picker" />,
}));

function activityLog(id: number): ActivityLogResponse {
  return {
    id,
    actorId: 1,
    actorName: 'Quản trị viên',
    type: 'CONFIG_UPDATE',
    typeLabel: 'Cập nhật cấu hình',
    targetType: 'CONFIG',
    targetTitle: `Cấu hình ${id}`,
    detail: `Chi tiết ${id}`,
    createdAt: '2026-10-27T01:00:00Z',
  };
}

function activityPage(number: number, totalElements: number): ActivityLogPage {
  const size = 20;
  const totalPages = Math.ceil(totalElements / size);
  const numberOfElements = Math.max(0, Math.min(size, totalElements - number * size));
  return {
    content: Array.from({ length: numberOfElements }, (_, index) => activityLog(number * size + index + 1)),
    number,
    size,
    totalElements,
    totalPages,
    numberOfElements,
    first: number === 0,
    last: totalPages === 0 || number + 1 >= totalPages,
    empty: numberOfElements === 0,
  };
}

beforeEach(() => {
  vi.mocked(adminAuditApi.types).mockResolvedValue([]);
  vi.mocked(adminAuditApi.list).mockImplementation(async ({ page = 0 } = {}) => activityPage(page, 55));
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('AdminAuditLogs pagination', () => {
  it('mở toàn bộ nội dung dài như văn bản, không thêm request hoặc render HTML', async () => {
    const user = userEvent.setup();
    const detail = '<img src=x onerror="alert(1)"> ' + 'Nội dung dài '.repeat(60);
    vi.mocked(adminAuditApi.list).mockResolvedValue({ ...activityPage(0, 1), content: [{ ...activityLog(1), detail }] });
    render(<AdminAuditLogs />);
    await screen.findByText('Cấu hình 1');
    const reads = vi.mocked(adminAuditApi.list).mock.calls.length;
    await user.click(screen.getByRole('button', { name: 'Xem Chi tiết nhật ký' }));
    const dialog = screen.getByRole('dialog', { name: 'Chi tiết nhật ký' });
    expect(within(dialog).getByText(detail.trim())).toBeTruthy();
    expect(dialog.querySelector('img')).toBeNull();
    expect(adminAuditApi.list).toHaveBeenCalledTimes(reads);
    await user.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('giữ raw payload ngoài giao diện, chỉ mở nội dung checklist đã nhận diện', async () => {
    const user = userEvent.setup();
    const data = { token: 'sensitive-test-marker', checks: [{ question: 'Ảnh đúng món đồ', passed: true }] };
    vi.mocked(adminAuditApi.list).mockResolvedValue({ ...activityPage(0, 1), content: [{ ...activityLog(1), data, detail: JSON.stringify(data) }] });
    render(<AdminAuditLogs />);
    await screen.findByText('Đạt đủ 1/1 mục kiểm duyệt');
    await user.click(screen.getByRole('button', { name: 'Xem chi tiết' }));
    const dialog = screen.getByRole('dialog', { name: 'Chi tiết nhật ký' });
    expect(within(dialog).getByText('Ảnh đúng món đồ')).toBeTruthy();
    expect(document.body.textContent).not.toContain('sensitive-test-marker');
    expect(document.body.textContent).not.toContain('token');
  });

  it('cố định 20 bản ghi và lấy đúng dữ liệu của trang được chọn', async () => {
    const user = userEvent.setup();
    render(<AdminAuditLogs />);

    await screen.findByText('55 bản ghi · Trang 1/3');
    expect(adminAuditApi.list).toHaveBeenLastCalledWith({ page: 0, size: 20 });
    expect(document.querySelectorAll('tbody tr')).toHaveLength(20);
    expect(screen.getByText('Cấu hình 1')).toBeTruthy();
    expect(screen.getByText('Cấu hình 20')).toBeTruthy();

    await user.click(screen.getByRole('button', { name: 'Trang 2' }));

    expect(await screen.findByText('55 bản ghi · Trang 2/3')).toBeTruthy();
    expect(adminAuditApi.list).toHaveBeenLastCalledWith({ page: 1, size: 20 });
    expect(document.querySelectorAll('tbody tr')).toHaveLength(20);
    expect(screen.getByText('Cấu hình 21')).toBeTruthy();
    expect(screen.getByText('Cấu hình 40')).toBeTruthy();
    expect(screen.queryByText('Cấu hình 20')).toBeNull();
    expect(screen.queryByText('Cấu hình 41')).toBeNull();
  });

  it('điều hướng bằng nút Trước và Sau trong giới hạn', async () => {
    const user = userEvent.setup();
    render(<AdminAuditLogs />);

    await screen.findByText('55 bản ghi · Trang 1/3');
    expect((screen.getByRole('button', { name: 'Trước' }) as HTMLButtonElement).disabled).toBe(true);

    await user.click(screen.getByRole('button', { name: 'Sau' }));
    expect(await screen.findByText('55 bản ghi · Trang 2/3')).toBeTruthy();
    expect(adminAuditApi.list).toHaveBeenLastCalledWith({ page: 1, size: 20 });

    await user.click(screen.getByRole('button', { name: 'Trước' }));
    expect(await screen.findByText('55 bản ghi · Trang 1/3')).toBeTruthy();
    expect(adminAuditApi.list).toHaveBeenLastCalledWith({ page: 0, size: 20 });
  });

  it('hiển thị dấu ba chấm, trang đầu/cuối và làm nổi bật trang đang chọn', async () => {
    const user = userEvent.setup();
    vi.mocked(adminAuditApi.list).mockImplementation(async ({ page = 0 } = {}) => activityPage(page, 205));
    render(<AdminAuditLogs />);

    await screen.findByText('205 bản ghi · Trang 1/11');
    expect(screen.getByRole('button', { name: 'Trang 1' }).getAttribute('aria-current')).toBe('page');
    for (const pageNumber of [1, 2, 3, 4, 11]) {
      expect(screen.getByRole('button', { name: `Trang ${pageNumber}` })).toBeTruthy();
    }
    expect(screen.getAllByText('…')).toHaveLength(1);

    await user.click(screen.getByRole('button', { name: 'Trang 4' }));

    expect(await screen.findByText('205 bản ghi · Trang 4/11')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Trang 4' }).getAttribute('aria-current')).toBe('page');
    expect(screen.getAllByText('…')).toHaveLength(2);
    expect(adminAuditApi.list).toHaveBeenLastCalledWith({ page: 3, size: 20 });
  });

  it('hiển thị trạng thái rỗng và khóa điều hướng khi không có dữ liệu', async () => {
    vi.mocked(adminAuditApi.list).mockResolvedValue(activityPage(0, 0));
    render(<AdminAuditLogs />);

    expect(await screen.findByText('Chưa có nhật ký')).toBeTruthy();
    expect(screen.getByText('0 bản ghi · Trang 1/1')).toBeTruthy();
    expect((screen.getByRole('button', { name: 'Trước' }) as HTMLButtonElement).disabled).toBe(true);
    expect((screen.getByRole('button', { name: 'Sau' }) as HTMLButtonElement).disabled).toBe(true);
    expect(screen.getByRole('button', { name: 'Trang 1' }).getAttribute('aria-current')).toBe('page');
  });

  it('tự quay về trang hợp lệ cuối cùng khi dữ liệu giảm làm trang hiện tại vượt giới hạn', async () => {
    const user = userEvent.setup();
    vi.mocked(adminAuditApi.list).mockImplementation(async ({ page = 0 } = {}) => {
      if (page === 1) {
        return {
          ...activityPage(1, 5),
          content: [],
          numberOfElements: 0,
          empty: true,
        };
      }
      return activityPage(0, vi.mocked(adminAuditApi.list).mock.calls.length === 1 ? 40 : 5);
    });
    render(<AdminAuditLogs />);

    await screen.findByText('40 bản ghi · Trang 1/2');
    await user.click(screen.getByRole('button', { name: 'Trang 2' }));

    await waitFor(() => {
      expect(adminAuditApi.list).toHaveBeenCalledWith({ page: 1, size: 20 });
      expect(adminAuditApi.list).toHaveBeenLastCalledWith({ page: 0, size: 20 });
    });
    expect(await screen.findByText('5 bản ghi · Trang 1/1')).toBeTruthy();
    expect(screen.getByText('Cấu hình 1')).toBeTruthy();
  });

  it('giữ dãy số trang và nút điều hướng trong khung responsive', async () => {
    render(<AdminAuditLogs />);
    await screen.findByText('55 bản ghi · Trang 1/3');

    const pagination = screen.getByRole('navigation', { name: 'Phân trang nhật ký' });
    expect(pagination.className).toContain('flex-wrap');
    expect(within(pagination).getByRole('button', { name: 'Trước' })).toBeTruthy();
    expect(within(pagination).getByRole('button', { name: 'Trang 1' })).toBeTruthy();
    expect(within(pagination).getByRole('button', { name: 'Trang 2' })).toBeTruthy();
    expect(within(pagination).getByRole('button', { name: 'Trang 3' })).toBeTruthy();
    expect(within(pagination).getByRole('button', { name: 'Sau' })).toBeTruthy();
  });
});
