// @vitest-environment jsdom

import { cleanup, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { reportApi, type AdminReportDetail, type ReportResponse } from '../../services/reportApi';
import { adminRequestApi, type AdminRequestDetail } from '../../services/adminRequestApi';
import { adminItemApi } from '../../services/adminItemApi';
import { formatDeadline } from '../../utils/request';
import { AdminComplaints } from './AdminComplaints';

vi.mock('../../services/reportApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../services/reportApi')>();
  return {
    ...actual,
    reportApi: {
      ...actual.reportApi,
      list: vi.fn(),
      detail: vi.fn(),
      conclude: vi.fn(),
      requestResponse: vi.fn(),
    },
  };
});

vi.mock('../../services/adminRequestApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../services/adminRequestApi')>();
  // Mặc định giao dịch không còn DISPUTED nên các test cũ không thấy khối chọn từng món
  const detail = vi.fn().mockResolvedValue({ status: 'COMPLETED' });
  return { ...actual, adminRequestApi: { ...actual.adminRequestApi, detail } };
});

vi.mock('../../services/adminItemApi', () => ({
  adminItemApi: {
    detail: vi.fn(async (itemId: number) => ({
      summary: { id: itemId, title: 'Bàn học gỗ', imageUrl: `/media/items/${itemId}.jpg` },
    })),
  },
}));

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

function detail(
  reportOverrides: Partial<ReportResponse>,
  overrides: Partial<AdminReportDetail> = {},
) {
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

function renderPage() {
  return render(
    <MemoryRouter>
      <AdminComplaints />
    </MemoryRouter>,
  );
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
  renderPage();
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
  renderPage();
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
  it('hiện danh sách dạng bảng với đúng bài liên quan và các cột quản trị', async () => {
    const value = detail({ status: 'OPEN' }, { ...pending });
    vi.mocked(reportApi.list).mockResolvedValue({
      items: [value.report],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    vi.mocked(reportApi.detail).mockResolvedValue(value);

    renderPage();

    const table = await screen.findByRole('table', { name: 'Danh sách hồ sơ' });
    expect(within(table).getByRole('columnheader', { name: 'Mã hồ sơ' })).toBeTruthy();
    expect(within(table).getByRole('columnheader', { name: 'Bài đăng liên quan' })).toBeTruthy();
    expect(within(table).getByRole('columnheader', { name: 'Loại khiếu nại/báo cáo' })).toBeTruthy();
    expect(within(table).getByRole('columnheader', { name: 'Người gửi báo cáo' })).toBeTruthy();
    expect(within(table).getByRole('columnheader', { name: 'Lý do báo cáo' })).toBeTruthy();
    expect(within(table).getByRole('columnheader', { name: 'Ngày gửi' })).toBeTruthy();
    expect(within(table).getByRole('columnheader', { name: 'Trạng thái xử lý' })).toBeTruthy();
    expect(within(table).getByRole('columnheader', { name: 'Người bị báo cáo' })).toBeTruthy();
    expect(within(table).getByText('Báo cáo bài đăng')).toBeTruthy();
    expect(within(table).getByText('HS-7')).toBeTruthy();
    expect(await within(table).findByRole('img', { name: 'Bàn học gỗ' })).toBeTruthy();
    expect(adminItemApi.detail).toHaveBeenCalledWith(5);
  });

  it('lọc được riêng khiếu nại giao dịch đang chờ giải trình', async () => {
    const user = userEvent.setup();
    vi.mocked(reportApi.list).mockResolvedValue({
      items: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    });
    renderPage();

    await user.selectOptions(screen.getByLabelText('Loại hồ sơ'), 'REQUEST');
    await user.click(screen.getByRole('button', { name: 'Chờ giải trình' }));

    expect(reportApi.list).toHaveBeenLastCalledWith(
      { targetType: 'REQUEST', status: 'AWAITING_RESPONSE', page: 0, size: 20 },
      expect.any(AbortSignal),
    );
  });

  it('hồ sơ bác bỏ không trừ sao: ghi "Không trừ sao", không còn "trừ 0 sao"', async () => {
    const { dialog, result } = await openDetail(
      detail({ status: 'DISMISSED' }, { starsDeducted: 0 }),
    );

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

  it('F1: bài đang có giao dịch thì hộp kết luận báo trước giao dịch sẽ bị huỷ hay vẫn tiếp tục', async () => {
    const serious = await openPendingDetail(
      detail(
        { status: 'OPEN' },
        { ...pending, openTransactionPartnerName: 'Lê Văn Cường', seriousReason: true },
      ),
    );
    await serious.user.click(serious.concludeButton);
    expect(
      within(serious.dialog).getByText(/Bài đang có giao dịch với Lê Văn Cường\./),
    ).toBeTruthy();
    expect(within(serious.dialog).getByText(/Chấp thuận thì giao dịch sẽ bị huỷ/)).toBeTruthy();
    cleanup();

    const other = await openPendingDetail(
      detail(
        { status: 'OPEN' },
        { ...pending, openTransactionPartnerName: 'Lê Văn Cường', seriousReason: false },
      ),
    );
    await other.user.click(other.concludeButton);
    expect(within(other.dialog).getByText(/Chấp thuận thì giao dịch vẫn tiếp tục/)).toBeTruthy();
  });

  it('F2: báo cáo của tài khoản mới hiện nhãn Tài khoản mới ở danh sách', async () => {
    vi.mocked(reportApi.list).mockResolvedValue({
      items: [report({ status: 'OPEN', newReporterAccount: true })],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    renderPage();

    expect(await screen.findByText('Tài khoản mới')).toBeTruthy();
  });

  it('F7: bác bỏ có ô Báo cáo sai có chủ ý, tích thì gửi cờ phạt, mặc định không gửi', async () => {
    const { user, dialog, concludeButton } = await openPendingDetail(
      detail({ status: 'OPEN' }, { ...pending }),
    );
    vi.mocked(reportApi.conclude).mockResolvedValue({} as never);
    await user.click(concludeButton);
    await user.click(within(dialog).getByRole('radio', { name: 'Bác bỏ · Không có cơ sở' }));
    const checkbox = within(dialog).getByRole('checkbox', {
      name: /Báo cáo sai có chủ ý/,
    }) as HTMLInputElement;
    expect(checkbox.checked).toBe(false);
    await user.click(checkbox);
    await user.click(within(dialog).getByRole('button', { name: 'Kiểm tra kết luận' }));
    await user.click(within(dialog).getByRole('button', { name: 'Xác nhận gửi' }));

    expect(reportApi.conclude).toHaveBeenCalledWith(
      expect.any(Number),
      expect.objectContaining({ decision: 'DISMISS', deliberateFalseReport: true }),
    );
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
    const reviewButton = within(dialog).getByRole<HTMLButtonElement>('button', {
      name: 'Kiểm tra kết luận',
    });
    expect(reviewButton.disabled).toBe(true);
    await user.click(within(dialog).getByRole('radio', { name: /Chấp thuận/ }));
    await user.click(reviewButton);
    expect(reportApi.conclude).not.toHaveBeenCalled();
    await user.click(within(dialog).getByRole('button', { name: 'Xác nhận gửi' }));

    expect(
      await screen.findByText('Bên bị báo cáo vẫn đang trong hạn giải trình, chưa thể kết luận.'),
    ).toBeTruthy();
    expect(vi.mocked(reportApi.detail).mock.calls.length).toBeGreaterThanOrEqual(2);
  });

  it('D-07: báo cáo bài đang Chờ xử lý có nút Yêu cầu giải trình, bấm thì gọi API và tải lại', async () => {
    const { user, dialog } = await openPendingDetail(
      detail({ status: 'OPEN', targetType: 'ITEM' }, { ...pending, responseWindowOpen: false }),
    );
    vi.mocked(reportApi.requestResponse).mockResolvedValue(undefined);

    await user.click(within(dialog).getByRole('button', { name: 'Yêu cầu giải trình' }));

    expect(reportApi.requestResponse).toHaveBeenCalledWith(7);
    expect(
      await screen.findByText('Đã gửi yêu cầu giải trình. Xem hạn phản hồi trong hồ sơ.'),
    ).toBeTruthy();
    expect(vi.mocked(reportApi.detail).mock.calls.length).toBeGreaterThanOrEqual(2);
  });

  it('D-07: khiếu nại giao dịch hoặc hồ sơ đã có giải trình thì không có nút Yêu cầu giải trình', async () => {
    const { dialog } = await openPendingDetail(
      detail(
        { status: 'OPEN', targetType: 'REQUEST', requestId: 9 },
        { ...pending, responseWindowOpen: false },
      ),
    );
    expect(within(dialog).queryByRole('button', { name: 'Yêu cầu giải trình' })).toBeNull();
    cleanup();

    const responded = await openPendingDetail(
      detail(
        { status: 'OPEN', targetType: 'ITEM' },
        { ...pending, response: 'Ảnh tự chụp', responseWindowOpen: false },
      ),
    );
    expect(
      within(responded.dialog).queryByRole('button', { name: 'Yêu cầu giải trình' }),
    ).toBeNull();
  });
});

describe('AdminComplaints kết luận từng món của giao dịch', () => {
  const disputedSwap = {
    id: 90,
    type: 'SWAP',
    status: 'DISPUTED',
    donor: {
      userId: 3,
      name: 'Trần Thị Bình',
      email: null,
      trustStars: 5,
      rank: null,
      locked: false,
    },
    receiver: {
      userId: 2,
      name: 'Nguyễn Văn An',
      email: null,
      trustStars: 5,
      rank: null,
      locked: false,
    },
    item: { itemId: 5, title: 'Máy ảnh', imageUrl: null, status: 'RESERVED', offerType: 'SWAP' },
    offeredItem: {
      itemId: 6,
      title: 'Mũ bảo hiểm',
      imageUrl: null,
      status: 'RESERVED',
      offerType: 'SWAP',
    },
    deliveryMethod: 'IN_PERSON',
    meetingPlace: null,
    meetingTime: null,
    timeline: [],
    photos: [],
    reports: [],
    points: [],
    messagesViewable: true,
    cancellable: true,
    createdAt: '2026-11-20T08:00:00Z',
  } as unknown as AdminRequestDetail;

  const requestReport = detail(
    {
      targetType: 'REQUEST',
      requestId: 90,
      status: 'AWAITING_RESPONSE',
      reason: 'NO_SHOW',
      stage: 'AFTER',
    },
    { ...pending, responseWindowOpen: false },
  );

  it('modal hiện mã hồ sơ, hồ sơ hai bên, giao dịch và đúng hai món SWAP', async () => {
    vi.mocked(adminRequestApi.detail).mockResolvedValue(disputedSwap);

    const { dialog } = await openPendingDetail(requestReport);

    expect(within(dialog).getByText(/Chi tiết hồ sơ HS-7/)).toBeTruthy();
    expect(within(dialog).getByRole('link', { name: 'Nguyễn Văn An' }).getAttribute('href')).toBe('/admin/users/2');
    expect(within(dialog).getByRole('link', { name: 'Trần Thị Bình' }).getAttribute('href')).toBe('/admin/users/3');
    expect(within(dialog).getByRole('link', { name: 'Xem chi tiết giao dịch' }).getAttribute('href')).toBe(
      '/admin/transactions/90',
    );
    expect((await within(dialog).findByRole('link', { name: 'Máy ảnh' })).getAttribute('href')).toBe('/admin/items/5');
    expect(within(dialog).getByRole('link', { name: 'Mũ bảo hiểm' }).getAttribute('href')).toBe('/admin/items/6');
  });

  it('mặc định món của bên vi phạm là Ẩn, món bên kia là Mở lại, và gửi đủ mọi món khi chấp thuận', async () => {
    vi.mocked(adminRequestApi.detail).mockResolvedValue(disputedSwap);
    vi.mocked(reportApi.conclude).mockResolvedValue({} as never);
    const { user, dialog, concludeButton } = await openPendingDetail(requestReport);

    await user.click(concludeButton);
    await user.click(within(dialog).getByRole('radio', { name: /Chấp thuận/ }));
    const outcomes = await within(dialog).findByTestId('dispute-item-outcomes');

    const main = within(outcomes).getByLabelText('Cách xử lý món Máy ảnh') as HTMLSelectElement;
    const offered = within(outcomes).getByLabelText(
      'Cách xử lý món Mũ bảo hiểm',
    ) as HTMLSelectElement;
    // Bên bị báo cáo là chủ bài nên món chính bị Ẩn
    expect(main.value).toBe('HIDE');
    expect(offered.value).toBe('REOPEN');

    await user.selectOptions(offered, 'TRANSFERRED');
    expect(within(outcomes).getByText(/Hệ thống báo hai bên liên hệ để hoàn trả/)).toBeTruthy();
    await user.click(within(dialog).getByRole('button', { name: 'Kiểm tra kết luận' }));
    await user.click(within(dialog).getByRole('button', { name: 'Xác nhận gửi' }));

    expect(reportApi.conclude).toHaveBeenCalledWith(
      7,
      expect.objectContaining({
        decision: 'UPHOLD',
        itemOutcomes: [
          { itemId: 5, choice: 'HIDE' },
          { itemId: 6, choice: 'TRANSFERRED' },
        ],
      }),
    );
  });

  it('người nhận vi phạm thì món đổi của người nhận mặc định là Ẩn (trước đây không)', async () => {
    vi.mocked(adminRequestApi.detail).mockResolvedValue(disputedSwap);
    const receiverViolates = {
      ...requestReport,
      reportedUser: { id: 2, fullName: 'Nguyễn Văn An' },
    };
    const { user, dialog, concludeButton } = await openPendingDetail(receiverViolates);

    await user.click(concludeButton);
    await user.click(within(dialog).getByRole('radio', { name: /Chấp thuận/ }));
    const outcomes = await within(dialog).findByTestId('dispute-item-outcomes');

    expect(
      (within(outcomes).getByLabelText('Cách xử lý món Mũ bảo hiểm') as HTMLSelectElement).value,
    ).toBe('HIDE');
    expect(
      (within(outcomes).getByLabelText('Cách xử lý món Máy ảnh') as HTMLSelectElement).value,
    ).toBe('REOPEN');
  });

  const cancelNotAccusing = {
    ...requestReport,
    report: { ...requestReport.report, reason: 'OTHER' as const },
    reportedUser: { id: 2, fullName: 'Nguyễn Văn An' },
  };

  it('đề nghị huỷ không tố ai: mặc định không trừ sao ai, món mở lại và không gửi penalizedUserId', async () => {
    vi.mocked(adminRequestApi.detail).mockResolvedValue(disputedSwap);
    vi.mocked(reportApi.conclude).mockResolvedValue({} as never);
    const { user, dialog, concludeButton } = await openPendingDetail(cancelNotAccusing);

    await user.click(concludeButton);
    await user.click(within(dialog).getByRole('radio', { name: /Chấp thuận/ }));
    const outcomes = await within(dialog).findByTestId('dispute-item-outcomes');
    expect(
      (within(outcomes).getByLabelText('Cách xử lý món Mũ bảo hiểm') as HTMLSelectElement).value,
    ).toBe('REOPEN');
    expect((within(dialog).getByLabelText(/Bên bị trừ 1 sao/) as HTMLSelectElement).value).toBe('');
    await user.click(within(dialog).getByRole('button', { name: 'Kiểm tra kết luận' }));
    await user.click(within(dialog).getByRole('button', { name: 'Xác nhận gửi' }));

    expect(reportApi.conclude).toHaveBeenCalledWith(
      7,
      expect.objectContaining({ penalizedUserId: undefined }),
    );
  });

  it('đề nghị huỷ không tố ai: chọn bên bị trừ thì gửi penalizedUserId', async () => {
    vi.mocked(adminRequestApi.detail).mockResolvedValue(disputedSwap);
    vi.mocked(reportApi.conclude).mockResolvedValue({} as never);
    const { user, dialog, concludeButton } = await openPendingDetail(cancelNotAccusing);

    await user.click(concludeButton);
    await user.click(within(dialog).getByRole('radio', { name: /Chấp thuận/ }));
    await user.selectOptions(await within(dialog).findByLabelText(/Bên bị trừ 1 sao/), '3');
    await user.click(within(dialog).getByRole('button', { name: 'Kiểm tra kết luận' }));
    await user.click(within(dialog).getByRole('button', { name: 'Xác nhận gửi' }));

    expect(reportApi.conclude).toHaveBeenCalledWith(
      7,
      expect.objectContaining({ penalizedUserId: 3 }),
    );
  });

  it('khiếu nại có tố bên kia (NO_SHOW) thì không hiện ô chọn bên bị trừ', async () => {
    vi.mocked(adminRequestApi.detail).mockResolvedValue(disputedSwap);
    const { user, dialog, concludeButton } = await openPendingDetail(requestReport);

    await user.click(concludeButton);
    await user.click(within(dialog).getByRole('radio', { name: /Chấp thuận/ }));
    await within(dialog).findByTestId('dispute-item-outcomes');

    expect(within(dialog).queryByLabelText(/Bên bị trừ 1 sao/)).toBeNull();
  });

  it('giao dịch không còn DISPUTED thì không hiện chọn từng món và không gửi itemOutcomes', async () => {
    vi.mocked(adminRequestApi.detail).mockResolvedValue({
      ...disputedSwap,
      status: 'COMPLETED',
    } as AdminRequestDetail);
    vi.mocked(reportApi.conclude).mockResolvedValue({} as never);
    const { user, dialog, concludeButton } = await openPendingDetail(requestReport);

    await user.click(concludeButton);
    await user.click(within(dialog).getByRole('radio', { name: /Chấp thuận/ }));
    expect(within(dialog).queryByTestId('dispute-item-outcomes')).toBeNull();
    await user.click(within(dialog).getByRole('button', { name: 'Kiểm tra kết luận' }));
    await user.click(within(dialog).getByRole('button', { name: 'Xác nhận gửi' }));

    expect(reportApi.conclude).toHaveBeenCalledWith(
      7,
      expect.objectContaining({ itemOutcomes: undefined }),
    );
  });
});
