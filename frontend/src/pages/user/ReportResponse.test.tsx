// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mediaApi } from '../../services/mediaApi';
import { reportApi, respondDispute } from '../../services/reportApi';
import { ReportResponse } from './ReportResponse';

vi.mock('../../services/reportApi', () => ({
  reportApi: { respond: vi.fn() },
  respondDispute: vi.fn(),
}));
vi.mock('../../services/mediaApi', () => ({ mediaApi: { upload: vi.fn() } }));

afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});

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
    const user = userEvent.setup();
    const kept = new File(['photo'], 'giao-nhan.jpg', { type: 'image/jpeg' });
    const removed = new File(['photo'], 'anh-nham.jpg', { type: 'image/jpeg' });
    vi.mocked(mediaApi.upload).mockResolvedValue({ mediaId: 25, url: '/media/25' });
    vi.mocked(respondDispute).mockResolvedValue(undefined);
    renderResponse('/me/disputes/81');

    expect(screen.getByRole('heading', { name: 'Giải trình khiếu nại giao dịch' })).toBeTruthy();
    expect(screen.getByRole<HTMLTextAreaElement>('textbox').maxLength).toBe(1000);
    await user.type(screen.getByRole('textbox'), 'Tôi đã giao món đồ lúc 9 giờ.');
    await user.upload(screen.getByLabelText('Ảnh minh chứng (không bắt buộc)'), [kept, removed]);
    await user.click(screen.getByRole('button', { name: 'Bỏ ảnh anh-nham.jpg' }));
    await user.click(screen.getByRole('button', { name: 'Gửi giải trình' }));

    expect(await screen.findByText('Giải trình đã được gửi đến quản trị viên.')).toBeTruthy();
    expect(mediaApi.upload).toHaveBeenCalledExactlyOnceWith(kept);
    expect(respondDispute).toHaveBeenCalledExactlyOnceWith(81, {
      note: 'Tôi đã giao món đồ lúc 9 giờ.',
      evidenceMediaIds: [25],
    });
    expect(reportApi.respond).not.toHaveBeenCalled();
  });

  it('giữ API và giới hạn riêng cho giải trình báo cáo bài hoặc người dùng', async () => {
    const user = userEvent.setup();
    vi.mocked(reportApi.respond).mockResolvedValue(undefined);
    renderResponse('/me/reports/82');

    expect(screen.getByRole('heading', { name: 'Giải trình báo cáo' })).toBeTruthy();
    expect(screen.getByRole<HTMLTextAreaElement>('textbox').maxLength).toBe(2000);
    await user.type(screen.getByRole('textbox'), 'Thông tin bài đăng đã được kiểm tra.');
    await user.click(screen.getByRole('button', { name: 'Gửi giải trình' }));

    expect(await screen.findByText('Giải trình đã được gửi đến quản trị viên.')).toBeTruthy();
    expect(reportApi.respond).toHaveBeenCalledExactlyOnceWith(82, {
      response: 'Thông tin bài đăng đã được kiểm tra.',
      evidenceMediaIds: undefined,
    });
    expect(respondDispute).not.toHaveBeenCalled();
  });
});
