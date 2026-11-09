// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mediaApi } from '../../services/mediaApi';
import { reportApi, respondDispute, type RespondentReportDetail } from '../../services/reportApi';
import { ReportResponse } from './ReportResponse';

vi.mock('../../services/reportApi', () => ({
  reportApi: { respond: vi.fn(), respondentView: vi.fn(), respondentEvidence: vi.fn() },
  respondDispute: vi.fn(),
}));
vi.mock('../../services/mediaApi', () => ({ mediaApi: { upload: vi.fn() } }));

beforeEach(() => {
  // Trang tự tải ảnh minh chứng của hồ sơ; trả Blob rỗng để không gọi mạng thật.
  vi.mocked(reportApi.respondentEvidence).mockResolvedValue(new Blob());
});

afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});

function detail(overrides: Partial<RespondentReportDetail>): RespondentReportDetail {
  return {
    id: 81,
    targetType: 'REQUEST',
    itemId: null,
    requestId: 7,
    reason: 'NOT_AS_DESCRIBED',
    description: 'Đồng hồ bị trầy mặt kính.',
    targetName: 'Đồng hồ nữ Casio',
    status: 'AWAITING_RESPONSE',
    createdAt: '2026-11-06T03:00:00Z',
    // Hạn ở xa trong tương lai để "còn ..." luôn dương khi chạy test.
    responseDueAt: '2099-01-01T03:00:00Z',
    responseWindowOpen: true,
    evidence: [{ id: 12, url: '/api/v1/reports/81/evidence/12', available: true }],
    response: null,
    respondedAt: null,
    responseEvidence: [],
    resolution: null,
    resolvedAt: null,
    ...overrides,
  };
}

function renderResponse(path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/me/disputes/:reportId" element={<ReportResponse kind="dispute" />} />
        <Route path="/me/reports/:reportId" element={<ReportResponse />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('ReportResponse', () => {
  it('giải trình khiếu nại giao dịch qua API hiện có và chỉ gửi ảnh còn được chọn', async () => {
    // given
    const user = userEvent.setup();
    const kept = new File(['photo'], 'giao-nhan.jpg', { type: 'image/jpeg' });
    const removed = new File(['photo'], 'anh-nham.jpg', { type: 'image/jpeg' });
    vi.mocked(reportApi.respondentView).mockResolvedValue(detail({}));
    vi.mocked(mediaApi.upload).mockResolvedValue({ mediaId: 25, url: '/media/25' });
    vi.mocked(respondDispute).mockResolvedValue(undefined);
    renderResponse('/me/disputes/81');

    // when
    const textbox = await screen.findByRole<HTMLTextAreaElement>('textbox');
    await user.type(textbox, 'Tôi đã giao món đồ lúc 9 giờ.');
    await user.upload(screen.getByLabelText('Ảnh minh chứng (không bắt buộc)'), [kept, removed]);
    await user.click(screen.getByRole('button', { name: 'Bỏ ảnh anh-nham.jpg' }));
    await user.click(screen.getByRole('button', { name: 'Gửi giải trình' }));

    // then
    expect(screen.getByRole('heading', { name: 'Giải trình khiếu nại giao dịch' })).toBeTruthy();
    expect(textbox.maxLength).toBe(2000);
    expect(await screen.findByText('Giải trình đã được gửi đến quản trị viên.')).toBeTruthy();
    expect(mediaApi.upload).toHaveBeenCalledExactlyOnceWith(kept);
    expect(respondDispute).toHaveBeenCalledExactlyOnceWith(81, {
      note: 'Tôi đã giao món đồ lúc 9 giờ.',
      evidenceMediaIds: [25],
    });
    expect(reportApi.respond).not.toHaveBeenCalled();
  });

  it('giữ API và giới hạn riêng cho giải trình báo cáo bài hoặc người dùng', async () => {
    // given
    const user = userEvent.setup();
    vi.mocked(reportApi.respondentView).mockResolvedValue(
      detail({ id: 82, targetType: 'ITEM', itemId: 5, requestId: null }),
    );
    vi.mocked(reportApi.respond).mockResolvedValue(undefined);
    renderResponse('/me/reports/82');

    // when
    const textbox = await screen.findByRole<HTMLTextAreaElement>('textbox');
    await user.type(textbox, 'Thông tin bài đăng đã được kiểm tra.');
    await user.click(screen.getByRole('button', { name: 'Gửi giải trình' }));

    // then
    expect(screen.getByRole('heading', { name: 'Giải trình báo cáo' })).toBeTruthy();
    expect(textbox.maxLength).toBe(2000);
    expect(await screen.findByText('Giải trình đã được gửi đến quản trị viên.')).toBeTruthy();
    expect(reportApi.respond).toHaveBeenCalledExactlyOnceWith(82, {
      response: 'Thông tin bài đăng đã được kiểm tra.',
      evidenceMediaIds: undefined,
    });
    expect(respondDispute).not.toHaveBeenCalled();
  });

  it('hiện hạn giải trình, mô tả của bên kia và nút nhắn tin với đối phương', async () => {
    // given
    vi.mocked(reportApi.respondentView).mockResolvedValue(detail({}));

    // when
    renderResponse('/me/disputes/81');

    // then
    expect(await screen.findByText(/Hạn giải trình:/)).toBeTruthy();
    expect(screen.getByText('Đồng hồ bị trầy mặt kính.')).toBeTruthy();
    expect(screen.getByText('Ảnh bên kia gửi kèm (1)')).toBeTruthy();
    const chatLink = screen.getByRole('button', { name: 'Nhắn tin với đối phương' }).closest('a');
    expect(chatLink?.getAttribute('href')).toBe('/requests/7');
    expect(screen.queryByText(/Hồ sơ #/)).toBeNull();
  });

  it('báo cáo bài đăng thì không có nút nhắn tin', async () => {
    // given
    vi.mocked(reportApi.respondentView).mockResolvedValue(
      detail({ targetType: 'ITEM', itemId: 5, requestId: null }),
    );

    // when
    renderResponse('/me/reports/81');

    // then
    await screen.findByText('Đồng hồ bị trầy mặt kính.');
    expect(screen.queryByRole('button', { name: 'Nhắn tin với đối phương' })).toBeNull();
  });

  it('hết hạn giải trình thì ẩn form', async () => {
    // given
    vi.mocked(reportApi.respondentView).mockResolvedValue(detail({ responseWindowOpen: false }));

    // when
    renderResponse('/me/disputes/81');

    // then
    expect(await screen.findByText(/Đã hết hạn giải trình/)).toBeTruthy();
    expect(screen.queryByRole('textbox')).toBeNull();
  });
});
