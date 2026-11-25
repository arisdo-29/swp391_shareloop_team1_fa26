// @vitest-environment jsdom

import { act, cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { adminAuditApi, type ActivityLogPage } from '../../services/adminAuditApi';
import { adminFinanceApi, type FinanceSummary } from '../../services/adminFinanceApi';
import { AdminOperationsDashboard } from './AdminFinancePages';

vi.mock('../../services/adminAuditApi', () => ({
  adminAuditApi: { list: vi.fn() },
}));

vi.mock('./AdminTodoCards', () => ({
  AdminTodoCards: () => null,
}));

vi.mock('./AdminSystemStats', () => ({
  AdminSystemStats: () => null,
}));

vi.mock('../../services/adminFinanceApi', () => ({
  adminFinanceApi: { report: vi.fn() },
}));

const report: FinanceSummary = {
  from: null,
  to: null,
  cashOrderCount: 2,
  cashAmountVnd: 120000,
  creditInTotal: 120,
  creditInByType: [],
  creditOutTotal: 10,
  creditOutByType: [],
  reconciliation: { cashCredits: 120, topUpCredits: 120, difference: 0, matched: true },
  cashByDay: [],
};

const activityPage: ActivityLogPage = {
  content: [{
    id: 1,
    actorId: 7,
    actorName: 'Quản trị viên',
    actorEmail: 'admin@shareloop.vn',
    type: 'CATEGORY_UPDATE',
    typeLabel: 'Sửa danh mục',
    targetType: 'CATEGORY',
    targetId: 3,
    targetTitle: null,
    detail: 'Đã sửa danh mục Sách',
    createdAt: '2026-10-24T01:00:00Z',
  }],
  number: 0,
  size: 5,
  totalElements: 1,
  totalPages: 1,
  numberOfElements: 1,
  first: true,
  last: true,
  empty: false,
};

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

function renderDashboard() {
  return render(
    <MemoryRouter>
      <AdminOperationsDashboard />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.mocked(adminFinanceApi.report).mockResolvedValue(report);
  vi.mocked(adminAuditApi.list).mockResolvedValue(activityPage);
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('loading độc lập trên Admin Dashboard', () => {
  it('hiển thị số liệu tài chính khi API nhật ký phụ vẫn đang chờ', async () => {
    const auditRequest = deferred<ActivityLogPage>();
    vi.mocked(adminAuditApi.list).mockReturnValue(auditRequest.promise);

    renderDashboard();

    expect(await screen.findByText('120.000đ')).toBeTruthy();
    expect(screen.queryByText('Đang tải tổng quan')).toBeNull();
    expect(screen.getByText('Đang tải hoạt động gần đây...')).toBeTruthy();

    await act(async () => auditRequest.resolve(activityPage));
    expect(await screen.findByText('Đã sửa danh mục Sách')).toBeTruthy();
  });

  it('vẫn hiển thị nhật ký khi API báo cáo lỗi', async () => {
    vi.mocked(adminFinanceApi.report).mockRejectedValue({ message: 'Không tải được báo cáo.', status: 500 });

    renderDashboard();

    expect(await screen.findByText(/Không tải được báo cáo/)).toBeTruthy();
    expect(await screen.findByText('Đã sửa danh mục Sách')).toBeTruthy();
  });

  it('vẫn hiển thị số liệu khi API nhật ký lỗi', async () => {
    vi.mocked(adminAuditApi.list).mockRejectedValue({ message: 'Không tải được nhật ký.', status: 500 });

    renderDashboard();

    expect(await screen.findByText('120.000đ')).toBeTruthy();
    expect(await screen.findByText(/Không tải được nhật ký/)).toBeTruthy();
  });

  it('hiện ba thẻ tài chính dẫn tới đúng trang quản lý', async () => {
    renderDashboard();

    const cashCard = (await screen.findByText('120.000đ')).closest('a')!;
    expect(cashCard.getAttribute('href')).toBe('/admin/finance/topup-orders?status=SUCCESS');
    expect(cashCard.textContent).toContain('Từ 2 đơn nạp thành công.');
    expect(screen.getByText('Tổng Credit đã cộng').closest('a')!.getAttribute('href')).toBe('/admin/finance/ledger');
    expect(screen.getByText('Tổng Credit đã trừ').closest('a')!.textContent).toContain('10');
  });
});

describe('Hoạt động gần đây', () => {
  it('xin đúng 5 dòng mới nhất và hiện cột Đối tượng', async () => {
    renderDashboard();

    expect(await screen.findByText('Đã sửa danh mục Sách')).toBeTruthy();
    expect(adminAuditApi.list).toHaveBeenCalledWith({ page: 0, size: 5 });
    expect(screen.getByRole('columnheader', { name: 'Đối tượng' })).toBeTruthy();
    expect(screen.getByText('Danh mục')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Xem chi tiết' }).getAttribute('href')).toBe('/admin/categories');
  });

  it('dẫn tới trang chi tiết theo id và bỏ nút khi không có trang hỗ trợ', async () => {
    vi.mocked(adminAuditApi.list).mockResolvedValue({
      ...activityPage,
      content: [
        { id: 2, actorId: 7, actorName: 'Quản trị viên', type: 'ADMIN_ITEM_HIDDEN', typeLabel: 'Ẩn bài đăng', targetType: 'ITEM', targetId: 42, targetTitle: 'Xe đạp cũ', detail: 'Đã ẩn bài', createdAt: '2026-10-24T02:00:00Z' },
        { id: 3, actorId: null, type: 'SYSTEM_JOB', typeLabel: 'Tác vụ hệ thống', targetType: null, targetId: null, detail: 'Chạy tác vụ', createdAt: '2026-10-24T03:00:00Z' },
      ],
    });

    renderDashboard();

    expect(await screen.findByText('Xe đạp cũ')).toBeTruthy();
    const links = screen.getAllByRole('link', { name: 'Xem chi tiết' });
    expect(links).toHaveLength(1);
    expect(links[0].getAttribute('href')).toBe('/admin/items/42');
    expect(screen.getByText('Hệ thống')).toBeTruthy();
  });
});
