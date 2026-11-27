// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { requestApi, type RequestResponse } from '../../services/requestApi';
import { maximumMeetingTimeValue, validateLocalMeetingTime } from '../../utils/meetingSchedule';
import { ReschedulePanel } from './ReschedulePanel';

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

const hoursFromNow = (hours: number) => new Date(Date.now() + hours * 3_600_000).toISOString();

const confirmed: RequestResponse = {
  id: 101,
  itemId: 45,
  receiverId: 12,
  donorId: 9,
  type: 'GIVE',
  status: 'LOGISTICS_CONFIRMED',
  deliveryMethod: 'IN_PERSON',
  contactRevealedAt: '2026-11-30T08:00:00Z',
  meetingTime: hoursFromNow(24),
  rescheduleCount: 0,
  createdAt: '2026-11-20T08:00:00Z',
};

function renderPanel(request: RequestResponse, userId = 9) {
  const onUpdated = vi.fn();
  render(<ReschedulePanel request={request} currentUserId={userId} partnerName="Bình" onUpdated={onUpdated} />);
  return onUpdated;
}

describe('ReschedulePanel', () => {
  it('trước giờ hẹn hiện nút đề nghị dời lịch kèm số lần còn lại từ cấu hình', () => {
    renderPanel({ ...confirmed, rescheduleCount: 1 });
    expect(screen.getByRole('button', { name: 'Đề nghị dời lịch (còn 1 lần)' })).toBeTruthy();
  });

  it('hết số lần dời thì không còn nút', () => {
    renderPanel({ ...confirmed, rescheduleCount: 2 });
    expect(screen.queryByRole('button', { name: /Đề nghị dời lịch/ })).toBeNull();
  });

  it('đã có bên bấm hoàn tất hoặc có đề nghị huỷ thì không còn nút', () => {
    renderPanel({ ...confirmed, donorHandoverAt: '2026-12-01T00:00:00Z' });
    expect(screen.queryByRole('button', { name: /Đề nghị dời lịch/ })).toBeNull();
    cleanup();
    renderPanel({ ...confirmed, cancelRequestedAt: hoursFromNow(-1), cancelRequestedBy: 12 });
    expect(screen.queryByRole('button', { name: /Đề nghị dời lịch/ })).toBeNull();
  });

  it('từ giờ hẹn trở đi không dời lịch được', () => {
    renderPanel({ ...confirmed, meetingTime: hoursFromNow(-2) });
    expect(screen.queryByRole('button', { name: /Đề nghị dời lịch/ })).toBeNull();
  });

  it('gửi hàng còn dời lịch được sau giờ gửi dự kiến khi chưa chiều nào gửi', () => {
    renderPanel({ ...confirmed, deliveryMethod: 'SHIPPING', meetingTime: hoursFromNow(-2) });
    expect(screen.getByRole('button', { name: /Đề nghị dời lịch/ })).toBeTruthy();
  });

  it('gửi hàng không dời lịch được khi một chiều đã gửi dù chưa tới giờ dự kiến', () => {
    renderPanel({
      ...confirmed,
      type: 'SWAP',
      offeredItemId: 77,
      status: 'AWAITING_HANDOVER',
      deliveryMethod: 'SHIPPING',
      donorShippedAt: hoursFromNow(-1),
    });
    expect(screen.queryByRole('button', { name: /Đề nghị dời lịch/ })).toBeNull();
  });

  it('gửi đề nghị với giờ mới trong giới hạn và địa điểm mới', async () => {
    const user = userEvent.setup();
    vi.spyOn(requestApi, 'proposeReschedule').mockResolvedValue({ ...confirmed });
    renderPanel(confirmed);

    await user.click(screen.getByRole('button', { name: /Đề nghị dời lịch/ }));
    const newTime = new Date(Date.now() + 3 * 86_400_000);
    const local = new Date(newTime.getTime() - newTime.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
    fireEvent.change(screen.getByLabelText(/Thời gian mới/), { target: { value: local } });
    fireEvent.change(screen.getByLabelText('Địa điểm mới (không bắt buộc)'), { target: { value: 'Thư viện' } });
    await user.click(screen.getByRole('button', { name: 'Gửi đề nghị' }));

    await waitFor(() => expect(requestApi.proposeReschedule).toHaveBeenCalledOnce());
    const body = vi.mocked(requestApi.proposeReschedule).mock.calls[0][2];
    expect(body.meetingPlace).toBe('Thư viện');
    expect(new Date(body.meetingTime).getTime()).toBeGreaterThan(Date.now());
  });

  it('giờ xa hơn giới hạn thì báo chỉ hẹn trong vòng 7 ngày và khoá nút gửi', async () => {
    const user = userEvent.setup();
    renderPanel(confirmed);

    await user.click(screen.getByRole('button', { name: /Đề nghị dời lịch/ }));
    const far = new Date(Date.now() + 9 * 86_400_000);
    const local = new Date(far.getTime() - far.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
    fireEvent.change(screen.getByLabelText(/Thời gian mới/), { target: { value: local } });

    expect(screen.getByText('Chỉ hẹn trong vòng 7 ngày tới.', { selector: '[role="alert"], p, span' })).toBeTruthy();
    expect((screen.getByRole('button', { name: 'Gửi đề nghị' }) as HTMLButtonElement).disabled).toBe(true);
  });

  it('người đề nghị thấy dòng chờ phản hồi và hạn, không có nút Đồng ý', () => {
    renderPanel(
      {
        ...confirmed,
        rescheduleProposedBy: 9,
        rescheduleProposedAt: hoursFromNow(-1),
        rescheduleMeetingTime: hoursFromNow(48),
        rescheduleExpiresAt: hoursFromNow(10),
      },
      9,
    );
    expect(screen.getByText(/Đang chờ Bình phản hồi đề nghị dời lịch sang/)).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Đồng ý dời lịch' })).toBeNull();
  });

  it('bên kia thấy Đồng ý dời lịch và Từ chối, gọi đúng API', async () => {
    const user = userEvent.setup();
    vi.spyOn(requestApi, 'acceptReschedule').mockResolvedValue({ ...confirmed });
    vi.spyOn(requestApi, 'rejectReschedule').mockResolvedValue({ ...confirmed });
    const pending = {
      ...confirmed,
      rescheduleProposedBy: 9,
      rescheduleProposedAt: hoursFromNow(-1),
      rescheduleMeetingTime: hoursFromNow(48),
      rescheduleExpiresAt: hoursFromNow(10),
    };
    const onUpdated = renderPanel(pending, 12);

    await user.click(screen.getByRole('button', { name: 'Đồng ý dời lịch' }));
    await waitFor(() => expect(requestApi.acceptReschedule).toHaveBeenCalledWith(101, 12));
    await user.click(screen.getByRole('button', { name: 'Từ chối' }));
    await waitFor(() => expect(requestApi.rejectReschedule).toHaveBeenCalledWith(101, 12));
    expect(onUpdated).toHaveBeenCalledTimes(2);
  });
});

describe('giới hạn giờ hẹn', () => {
  it('validateLocalMeetingTime chặn giờ xa hơn số ngày cấu hình', () => {
    const now = new Date('2026-12-01T10:00:00');
    expect(validateLocalMeetingTime('2026-12-07T10:00', now, 7)).toBeNull();
    expect(validateLocalMeetingTime('2026-12-09T10:00', now, 7)).toBe('Chỉ hẹn trong vòng 7 ngày tới.');
  });

  it('maximumMeetingTimeValue cộng đúng số ngày', () => {
    const now = new Date('2026-12-01T10:00:00');
    expect(maximumMeetingTimeValue(now, 7)).toBe('2026-12-08T10:00');
  });
});
