// @vitest-environment jsdom

import { cleanup, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { reportApi, type AdminReportDetail, type ReportResponse } from '../../services/reportApi';
import { formatDeadline } from '../../utils/request';
import { AdminComplaints } from './AdminComplaints';

vi.mock('../../services/reportApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../services/reportApi')>();
  return {
    ...actual,
    reportApi: { ...actual.reportApi, list: vi.fn(), detail: vi.fn(), conclude: vi.fn() },
  };
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

// Hồ sơ chưa kết luận: không có khối "Kết quả xử lý", chờ nút Kết luận hiện ra.
async function openPendingDetail(value: AdminReportDetail) {
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
  const concludeButton = (await within(dialog).findByRole('button', {
    name: 'Kết luận',
  })) as HTMLButtonElement;
  return { user, dialog, concludeButton };
}

const pending = { resolvedBy: null, resolvedAt: null, resolvedByName: null };

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

  it('D-15: còn hạn giải trình thì khoá nút Kết luận và hiện hạn dạng giờ:phút ngày/tháng', async () => {
    const dueAt = '2026-10-31T07:30:00Z';
    const { dialog, concludeButton } = await openPendingDetail(
      detail(
        { status: 'AWAITING_RESPONSE' },
        { ...pending, responseDueAt: dueAt, responseWindowOpen: true },
      ),
    );

    expect(concludeButton.disabled).toBe(true);
    const notice = within(dialog).getByText(/Chỉ kết luận được khi bên bị báo cáo đã giải trình/);
    expect(notice.textContent).toContain(`Hạn giải trình: ${formatDeadline(dueAt)}.`);
    expect(reportApi.conclude).not.toHaveBeenCalled();
  });

  it('D-15: đã hết hạn giải trình thì Admin kết luận được', async () => {
    const { dialog, concludeButton } = await openPendingDetail(
      detail(
        { status: 'AWAITING_RESPONSE' },
        { ...pending, responseDueAt: '2026-10-20T03:00:00Z', responseWindowOpen: false },
      ),
    );

    expect(concludeButton.disabled).toBe(false);
    expect(within(dialog).queryByText(/Chỉ kết luận được khi/)).toBeNull();
  });

  it('D-15: server báo còn hạn giải trình thì hiện cảnh báo và tải lại hồ sơ', async () => {
    const { user, dialog, concludeButton } = await openPendingDetail(
      detail({ status: 'OPEN' }, { ...pending, responseWindowOpen: false }),
    );
    vi.mocked(reportApi.conclude).mockRejectedValue({
      code: 'REPORT_RESPONSE_WINDOW_OPEN',
      status: 409,
      message: 'Bên bị báo cáo vẫn đang trong hạn giải trình, chưa thể kết luận.',
    });

    await user.click(concludeButton);
    await user.click(within(dialog).getByRole('button', { name: 'Xác nhận gửi' }));

    expect(
      await screen.findByText('Bên bị báo cáo vẫn đang trong hạn giải trình, chưa thể kết luận.'),
    ).toBeTruthy();
    expect(reportApi.detail).toHaveBeenCalledTimes(2);
  });
});
