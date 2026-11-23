// @vitest-environment jsdom

import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { requestApi, type RequestResponse } from '../../services/requestApi';
import { HandoverCard } from './HandoverCard';

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

const hoursFromNow = (hours: number) => new Date(Date.now() + hours * 3_600_000).toISOString();

const baseRequest: RequestResponse = {
  id: 101,
  itemId: 45,
  receiverId: 12,
  donorId: 9,
  type: 'GIVE',
  status: 'LOGISTICS_CONFIRMED',
  deliveryMethod: 'IN_PERSON',
  contactRevealedAt: '2026-11-30T08:00:00Z',
  meetingTime: hoursFromNow(5),
  createdAt: '2026-11-20T08:00:00Z',
};

function renderCard(request: RequestResponse, viewerIsDonor: boolean, userId = viewerIsDonor ? 9 : 12) {
  const handlers = {
    onUpdated: vi.fn(),
    onOpenFailed: vi.fn(),
    onOpenRefuse: vi.fn(),
    onOpenCancelRequest: vi.fn(),
    onOpenRejectCancel: vi.fn(),
  };
  render(
    <HandoverCard
      request={request}
      currentUserId={userId}
      viewerIsDonor={viewerIsDonor}
      partnerName="Bình"
      {...handlers}
    />,
  );
  return handlers;
}

describe('HandoverCard trước giờ hẹn (Gặp trực tiếp)', () => {
  it('chỉ có Đề nghị huỷ giao dịch, không có ba nút kết quả', async () => {
    const user = userEvent.setup();
    const handlers = renderCard(baseRequest, false);

    expect(screen.queryByRole('button', { name: 'Giao nhận không thành công' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Từ chối giao nhận' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Đã giao nhận xong' })).toBeNull();
    await user.click(screen.getByRole('button', { name: 'Đề nghị huỷ giao dịch' }));
    expect(handlers.onOpenCancelRequest).toHaveBeenCalledOnce();
  });
});

describe('HandoverCard từ giờ hẹn trở đi', () => {
  it('người nhận Cho tặng thấy ba nút và chủ bài không thấy Từ chối giao nhận', () => {
    const afterMeeting = { ...baseRequest, meetingTime: hoursFromNow(-2) };
    renderCard(afterMeeting, false);
    expect(screen.getByRole('button', { name: 'Đã giao nhận xong' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Giao nhận không thành công' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Từ chối giao nhận' })).toBeTruthy();
    cleanup();
    renderCard(afterMeeting, true);
    expect(screen.getByRole('button', { name: 'Giao nhận không thành công' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Từ chối giao nhận' })).toBeNull();
  });
});

describe('HandoverCard khi có đề nghị huỷ đang chờ', () => {
  const pending: RequestResponse = {
    ...baseRequest,
    meetingTime: hoursFromNow(-2),
    cancelRequestedBy: 9,
    cancelRequestedAt: hoursFromNow(-1),
    cancelRequestCode: 'NO_LONGER_NEEDED',
    cancelRequestLabel: 'Tôi không còn nhu cầu',
    cancelRequestExpiresAt: hoursFromNow(23),
  };

  it('người đề nghị thấy dòng chờ và rút lại được', async () => {
    const user = userEvent.setup();
    vi.spyOn(requestApi, 'withdrawCancelRequest').mockResolvedValue({ ...baseRequest });
    const handlers = renderCard(pending, true);

    expect(screen.getByText(/Đang chờ Bình phản hồi đề nghị huỷ, hạn/)).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Giao nhận không thành công' })).toBeNull();
    await user.click(screen.getByRole('button', { name: 'Rút lại đề nghị' }));

    await waitFor(() => expect(requestApi.withdrawCancelRequest).toHaveBeenCalledWith(101, 9));
    expect(handlers.onUpdated).toHaveBeenCalledOnce();
  });

  it('bên kia thấy Đồng ý và Không đồng ý cả sau giờ hẹn', async () => {
    const user = userEvent.setup();
    vi.spyOn(requestApi, 'acceptCancelRequest').mockResolvedValue({ ...baseRequest, status: 'CANCELLED' });
    const handlers = renderCard(pending, false);

    expect(screen.getByText(/Bình đề nghị huỷ giao dịch vì Tôi không còn nhu cầu/)).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Không đồng ý' }));
    expect(handlers.onOpenRejectCancel).toHaveBeenCalledOnce();
    await user.click(screen.getByRole('button', { name: 'Đồng ý huỷ' }));

    await waitFor(() => expect(requestApi.acceptCancelRequest).toHaveBeenCalledWith(101, 12));
  });
});
