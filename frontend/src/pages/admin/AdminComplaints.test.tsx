// @vitest-environment jsdom

import { cleanup, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { reportApi, type AdminReportDetail, type ReportResponse } from '../../services/reportApi';
import { AdminComplaints } from './AdminComplaints';

vi.mock('../../services/reportApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../services/reportApi')>();
  return { ...actual, reportApi: { ...actual.reportApi, list: vi.fn(), detail: vi.fn() } };
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function report(overrides: Partial<ReportResponse> = {}): ReportResponse {
  return {
    id: 7,
    reporterId: 2,
    targetType: 'ITEM',
    itemId: 5,
    targetUserId: 3,
    requestId: null,
    reason: 'NOT_AS_DESCRIBED',
    description: 'Ảnh không đúng món đồ',
    stage: 'BEFORE',
    status: 'DISMISSED',
    createdAt: '2026-10-20T03:00:00Z',
    reporterName: 'Nguyễn Văn An',
    targetName: 'Bàn học gỗ',
    ...overrides,
  };
}

function detail(reportOverrides: Partial<ReportResponse>, overrides: Partial<AdminReportDetail> = {}) {
  return {
    report: report(reportOverrides),
    reporter: { id: 2, fullName: 'Nguyễn Văn An' },
    reportedUser: { id: 3, fullName: 'Trần Thị Bình' },
    relatedItemId: 5,
    relatedItemTitle: 'Bàn học gỗ',
    response: null,
    respondedAt: null,
    responseDueAt: null,
    evidence: [],
    responseEvidence: [],
    resolvedBy: 1,
    resolution: null,
    starsDeducted: 0,
    resolvedAt: '2026-10-21T03:00:00Z',
    resolvedByName: 'Quản trị viên A',
    ...overrides,
  } satisfies AdminReportDetail;
}

async function openDetail(value: AdminReportDetail) {
  const user = userEvent.setup();
  vi.mocked(reportApi.list).mockResolvedValue({
    items: [value.report],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
  });
  vi.mocked(reportApi.detail).mockResolvedValue(value);
  render(<AdminComplaints />);
  await user.click(await screen.findByRole('button', { name: 'Xem chi tiết' }));
  const dialog = screen.getByRole('dialog');
  const heading = await within(dialog).findByText('Kết quả xử lý');
  return { dialog, result: heading.closest('section') as HTMLElement };
}

// Giá trị của một dòng trong <dl>: dt là nhãn, dd ngay sau là giá trị.
function rowValue(container: HTMLElement, label: string) {
  return within(container).getByText(label, { selector: 'dt' }).nextElementSibling?.textContent;
}

describe('AdminComplaints', () => {
  it('hồ sơ bác bỏ không trừ sao: ghi "Không trừ sao", không còn "trừ 0 sao"', async () => {
    const { dialog, result } = await openDetail(detail({ status: 'DISMISSED' }, { starsDeducted: 0 }));

    expect(rowValue(result, 'Kết luận')).toBe('Không có cơ sở');
    expect(rowValue(result, 'Người bị báo cáo')).toBe('Trần Thị Bình');
    expect(rowValue(result, 'Hệ quả')).toBe('Không trừ sao · Không ẩn bài');
    expect(dialog.textContent).not.toMatch(/trừ 0 sao/i);
    expect(within(result).getByText('Quản trị viên A kết luận lúc', { exact: false })).toBeTruthy();
    expect(within(result).queryByText('Ghi chú', { selector: 'dt' })).toBeNull();
  });

  it('hồ sơ có cơ sở trừ 1 sao: hiện người bị xử lý và hệ quả đầy đủ', async () => {
    const { dialog, result } = await openDetail(
      detail({ status: 'RESOLVED' }, { starsDeducted: 1, resolution: 'Ảnh không khớp mô tả' }),
    );

    expect(rowValue(result, 'Kết luận')).toBe('Có cơ sở');
    expect(rowValue(result, 'Người bị xử lý')).toBe('Trần Thị Bình');
    expect(rowValue(result, 'Hệ quả')).toBe('Trừ 1 sao uy tín · Ẩn bài đăng');
    expect(rowValue(result, 'Ghi chú')).toBe('Ảnh không khớp mô tả');
    expect(dialog.textContent).not.toMatch(/Đã trừ/);
  });
});
