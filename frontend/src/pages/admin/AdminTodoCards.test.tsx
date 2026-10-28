// @vitest-environment jsdom

import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { adminModerationApi, type ReviewQueueResponse } from '../../services/adminModerationApi';
import { reportApi, type AdminReportPage } from '../../services/reportApi';
import { adminFinanceApi } from '../../services/adminFinanceApi';
import { adminAnomalyApi } from '../../services/adminAnomalyApi';
import { AdminTodoCards } from './AdminTodoCards';

vi.mock('../../services/adminModerationApi', () => ({ adminModerationApi: { pendingReviews: vi.fn() } }));
vi.mock('../../services/reportApi', () => ({ reportApi: { list: vi.fn() } }));
vi.mock('../../services/adminFinanceApi', () => ({ adminFinanceApi: { topUpOrders: vi.fn() } }));
vi.mock('../../services/adminAnomalyApi', () => ({ adminAnomalyApi: { report: vi.fn() } }));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

const reportPage = (totalElements: number) => ({ items: [], page: 0, size: 1, totalElements, totalPages: 1 }) as AdminReportPage;

describe('AdminTodoCards', () => {
  it('đếm việc chờ từ các API đọc và ô lỗi không làm hỏng ô khác', async () => {
    vi.mocked(adminModerationApi.pendingReviews).mockResolvedValue({
      items: [{}, {}],
      checklist: [],
      page: 0,
      size: 100,
    } as unknown as ReviewQueueResponse);
    vi.mocked(reportApi.list).mockResolvedValueOnce(reportPage(1)).mockResolvedValueOnce(reportPage(2));
    vi.mocked(adminFinanceApi.topUpOrders).mockRejectedValue(new Error('down'));
    vi.mocked(adminAnomalyApi.report).mockResolvedValue({
      generatedAt: '2026-10-27T00:00:00Z',
      pairWindowDays: 90,
      pairThreshold: 5,
      repeatedPairs: [],
    });

    render(
      <MemoryRouter>
        <AdminTodoCards />
      </MemoryRouter>,
    );

    const reviewCard = screen.getByText('Bài chờ duyệt').closest('a')!;
    const reportCard = screen.getByText('Khiếu nại và báo cáo đang mở').closest('a')!;
    await waitFor(() => expect(reviewCard.querySelector('strong')?.textContent).toBe('2'));
    await waitFor(() => expect(reportCard.querySelector('strong')?.textContent).toBe('3'));
    expect(await screen.findByText('Chưa tải được số liệu.')).toBeTruthy();
    expect(reportApi.list).toHaveBeenCalledWith({ page: 0, size: 1, status: 'OPEN' });
  });
});
