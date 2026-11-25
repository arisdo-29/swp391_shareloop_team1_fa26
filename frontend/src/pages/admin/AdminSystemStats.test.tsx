// @vitest-environment jsdom

import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { adminUserApi } from '../../services/adminUserApi';
import { adminItemApi } from '../../services/adminItemApi';
import { adminRequestApi, type AdminRequestListParams } from '../../services/adminRequestApi';
import { AdminSystemStats } from './AdminSystemStats';

vi.mock('../../services/adminUserApi', () => ({ adminUserApi: { list: vi.fn() } }));
vi.mock('../../services/adminItemApi', () => ({ adminItemApi: { list: vi.fn() } }));
vi.mock('../../services/adminRequestApi', () => ({ adminRequestApi: { list: vi.fn() } }));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

const requestTotals: Record<string, number> = {
  RESERVED: 1,
  AWAITING_LOGISTICS: 2,
  LOGISTICS_CONFIRMED: 3,
  AWAITING_HANDOVER: 4,
  COMPLETED: 25,
};

describe('AdminSystemStats', () => {
  it('đếm tài khoản, bài đang hiển thị và giao dịch theo trạng thái', async () => {
    vi.mocked(adminUserApi.list).mockResolvedValue({ items: [], page: 0, size: 1, totalElements: 1200 });
    vi.mocked(adminItemApi.list).mockResolvedValue({ items: [], page: 0, size: 1, totalElements: 7, totalPages: 7 });
    vi.mocked(adminRequestApi.list).mockImplementation(async (params: AdminRequestListParams) => ({
      items: [],
      page: 0,
      size: 1,
      totalElements: requestTotals[params.status ?? ''] ?? 0,
      totalPages: 1,
    }));

    render(
      <MemoryRouter>
        <AdminSystemStats />
      </MemoryRouter>,
    );

    const userCard = screen.getByText('Tổng tài khoản').closest('a')!;
    const runningCard = screen.getByText('Giao dịch đang diễn ra').closest('a')!;
    const completedCard = screen.getByText('Giao dịch hoàn tất').closest('a')!;
    const itemCard = screen.getByText('Bài đang hiển thị').closest('a')!;

    await waitFor(() => expect(userCard.textContent).toContain('1.200'));
    await waitFor(() => expect(runningCard.textContent).toContain('10'));
    await waitFor(() => expect(completedCard.textContent).toContain('25'));
    await waitFor(() => expect(itemCard.textContent).toContain('7'));

    expect(userCard.getAttribute('href')).toBe('/admin/users');
    expect(itemCard.getAttribute('href')).toBe('/admin/items');
    expect(completedCard.getAttribute('href')).toBe('/admin/transactions?status=COMPLETED');
    expect(adminItemApi.list).toHaveBeenCalledWith({ status: 'APPROVED', active: true, page: 0, size: 1 });
    expect(adminRequestApi.list).toHaveBeenCalledWith({ status: 'COMPLETED', page: 0, size: 1 });
  });

  it('thẻ lỗi hiện hai gạch mà không ảnh hưởng thẻ khác', async () => {
    vi.mocked(adminUserApi.list).mockRejectedValue({ status: 500 });
    vi.mocked(adminItemApi.list).mockResolvedValue({ items: [], page: 0, size: 1, totalElements: 7, totalPages: 7 });
    vi.mocked(adminRequestApi.list).mockResolvedValue({ items: [], page: 0, size: 1, totalElements: 0, totalPages: 0 });

    render(
      <MemoryRouter>
        <AdminSystemStats />
      </MemoryRouter>,
    );

    const userCard = screen.getByText('Tổng tài khoản').closest('a')!;
    await waitFor(() => expect(userCard.textContent).toContain('--'));
    await waitFor(() => expect(screen.getByText('Bài đang hiển thị').closest('a')!.textContent).toContain('7'));
  });
});
