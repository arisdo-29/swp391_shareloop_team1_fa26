// @vitest-environment jsdom

import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { adminModerationApi, type ReviewQueueResponse } from '../../services/adminModerationApi';
import { reportApi, type AdminReportPage } from '../../services/reportApi';
import { adminAnomalyApi } from '../../services/adminAnomalyApi';
import { adminFinanceApi, type AdminTopUpSummary } from '../../services/adminFinanceApi';
import { AdminTodoCards } from './AdminTodoCards';

vi.mock('../../services/adminModerationApi', () => ({ adminModerationApi: { pendingReviews: vi.fn() } }));
vi.mock('../../services/reportApi', () => ({ reportApi: { list: vi.fn() } }));
vi.mock('../../services/adminAnomalyApi', () => ({ adminAnomalyApi: { report: vi.fn() } }));
vi.mock('../../services/adminFinanceApi', () => ({ adminFinanceApi: { topUpSummary: vi.fn() } }));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

const reportPage = (totalElements: number) => ({ items: [], page: 0, size: 1, totalElements, totalPages: 1 }) as AdminReportPage;

function mockApis({ reconcile = 4 }: { reconcile?: number } = {}) {
  vi.mocked(adminModerationApi.pendingReviews).mockResolvedValue({
    items: [{}, {}],
    checklist: [],
    page: 0,
    size: 100,
  } as unknown as ReviewQueueResponse);
  vi.mocked(reportApi.list).mockResolvedValueOnce(reportPage(1)).mockResolvedValueOnce(reportPage(2));
  vi.mocked(adminAnomalyApi.report).mockResolvedValue({
    generatedAt: '2026-10-27T00:00:00Z',
    pairWindowDays: 90,
    pairThreshold: 5,
    repeatedPairs: [],
  });
  vi.mocked(adminFinanceApi.topUpSummary).mockResolvedValue({ byStatus: [], needsReconcileCount: reconcile } as AdminTopUpSummary);
}

function renderCards() {
  return render(
    <MemoryRouter>
      <AdminTodoCards />
    </MemoryRouter>,
  );
}

describe('AdminTodoCards', () => {
  it('đếm bốn việc cần xử lý từ các API đọc', async () => {
    mockApis();
    renderCards();

    const reviewCard = screen.getByText('Bài chờ duyệt').closest('a')!;
    const reportCard = screen.getByText('Khiếu nại và báo cáo cần xử lý').closest('a')!;
    const anomalyCard = screen.getByText('Cảnh báo giao dịch lặp').closest('a')!;
    const reconcileCard = screen.getByText('Đơn nạp cần đối soát').closest('a')!;

    await waitFor(() => expect(reviewCard.textContent).toContain('2'));
    await waitFor(() => expect(reportCard.textContent).toContain('3'));
    await waitFor(() => expect(reconcileCard.textContent).toContain('4'));
    await waitFor(() => expect(anomalyCard.textContent).toContain('Không có việc chờ.'));

    expect(reportApi.list).toHaveBeenCalledWith({ page: 0, size: 1, status: 'OPEN' });
    expect(adminFinanceApi.topUpSummary).toHaveBeenCalledWith({});
    // Thẻ bấm được dẫn tới đúng trang quản lý, đơn cần đối soát mở sẵn bộ lọc.
    expect(reconcileCard.getAttribute('href')).toBe('/admin/finance/topup-orders?needsReconcile=true');
    // Thẻ có việc nổi bật hơn thẻ bằng 0, mỗi việc giữ màu nhấn riêng.
    expect(reconcileCard.getAttribute('data-state')).toBe('pending');
    expect(reconcileCard.className).toContain('admin-dash-card--red');
    expect(anomalyCard.getAttribute('data-state')).toBe('idle');
    expect(reviewCard.className).toContain('admin-dash-card--gold');
    // Bài đang hiển thị đã chuyển sang nhóm Thống kê hệ thống.
    expect(screen.queryByText('Bài đang hiển thị')).toBeNull();
  });

  it('chỉ thẻ lỗi hiện hai gạch, các thẻ khác vẫn có số', async () => {
    mockApis();
    vi.mocked(adminFinanceApi.topUpSummary).mockRejectedValue({ status: 500 });
    renderCards();

    const reconcileCard = screen.getByText('Đơn nạp cần đối soát').closest('a')!;
    await waitFor(() => expect(reconcileCard.textContent).toContain('--'));
    expect(reconcileCard.textContent).toContain('Chưa tải được số liệu.');
    const reviewCard = screen.getByText('Bài chờ duyệt').closest('a')!;
    await waitFor(() => expect(reviewCard.textContent).toContain('2'));
  });
});
