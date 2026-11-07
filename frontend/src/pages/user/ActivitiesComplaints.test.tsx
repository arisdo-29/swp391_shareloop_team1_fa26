// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { reportApi, type MyReportSummary } from '../../services/reportApi';
import { ActivitiesComplaints } from './ActivitiesComplaints';

vi.mock('../../services/reportApi', () => ({
  reportApi: { listMine: vi.fn(), listAgainstMe: vi.fn() },
}));

function summary(overrides: Partial<MyReportSummary>): MyReportSummary {
  return {
    id: 1,
    targetType: 'ITEM',
    itemId: 5,
    requestId: null,
    reason: 'OTHER',
    status: 'AWAITING_RESPONSE',
    targetName: 'Giáo trình Toán rời rạc',
    createdAt: '2026-11-06T03:00:00Z',
    responseDueAt: '2026-11-09T03:00:00Z',
    respondedAt: null,
    responseWindowOpen: true,
    resolution: null,
    resolvedAt: null,
    ...overrides,
  };
}

function renderTab() {
  render(
    <MemoryRouter>
      <ActivitiesComplaints />
    </MemoryRouter>,
  );
}

afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});

describe('ActivitiesComplaints', () => {
  it('khiếu nại giao dịch còn hạn thì có nút dẫn tới trang giải trình khiếu nại', async () => {
    // given
    vi.mocked(reportApi.listAgainstMe).mockResolvedValue([
      summary({ id: 81, targetType: 'REQUEST', itemId: null, requestId: 7 }),
    ]);
    vi.mocked(reportApi.listMine).mockResolvedValue([]);

    // when
    renderTab();

    // then
    const link = (await screen.findByRole('button', { name: 'Gửi giải trình' })).closest('a');
    expect(link?.getAttribute('href')).toBe('/me/disputes/81');
    expect(screen.getByText('Cần giải trình')).toBeTruthy();
  });

  it('hồ sơ đã quá hạn thì không còn nút giải trình', async () => {
    // given
    vi.mocked(reportApi.listAgainstMe).mockResolvedValue([summary({ responseWindowOpen: false })]);
    vi.mocked(reportApi.listMine).mockResolvedValue([]);

    // when
    renderTab();

    // then
    expect(await screen.findByText('Quá hạn giải trình, chờ kết luận')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Gửi giải trình' })).toBeNull();
  });

  it('báo cáo đã gửi được kết luận thì hiện ghi chú của quản trị viên', async () => {
    // given
    vi.mocked(reportApi.listAgainstMe).mockResolvedValue([]);
    vi.mocked(reportApi.listMine).mockResolvedValue([
      summary({
        targetType: 'USER',
        targetName: 'Trần Thị Bình',
        status: 'RESOLVED',
        responseWindowOpen: false,
        resolution: 'Đã xác minh vi phạm',
        resolvedAt: '2026-11-07T03:00:00Z',
      }),
    ]);

    // when
    renderTab();

    // then
    expect(await screen.findByText('Người dùng: Trần Thị Bình')).toBeTruthy();
    expect(screen.getByText('Có cơ sở, đã xử lý')).toBeTruthy();
    expect(screen.getByText('Đã xác minh vi phạm')).toBeTruthy();
  });

  it('một danh sách lỗi thì danh sách còn lại vẫn hiện', async () => {
    // given
    vi.mocked(reportApi.listAgainstMe).mockRejectedValue(new Error('network'));
    vi.mocked(reportApi.listMine).mockResolvedValue([]);

    // when
    renderTab();

    // then
    expect(await screen.findByText('Không tải được danh sách hồ sơ.')).toBeTruthy();
    expect(await screen.findByText('Bạn chưa gửi báo cáo nào')).toBeTruthy();
  });
});
