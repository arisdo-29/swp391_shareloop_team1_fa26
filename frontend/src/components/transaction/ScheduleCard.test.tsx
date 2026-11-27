// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { requestApi, type RequestResponse } from '../../services/requestApi';
import { publicConfigApi } from '../../services/publicConfigApi';
import { toLocalMeetingTimeValue } from '../../utils/meetingSchedule';
import { ScheduleCard } from './ScheduleCard';

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe('ScheduleCard meeting place', () => {
  it('shows the character count and a clear limit message', () => {
    const request = {
      id: 101,
      itemId: 45,
      receiverId: 12,
      type: 'GIVE',
      status: 'AWAITING_LOGISTICS',
      deliveryMethod: 'IN_PERSON',
      createdAt: '2026-11-17T08:00:00Z',
    } satisfies RequestResponse;
    render(
      <ScheduleCard
        request={request}
        currentUserId={9}
        viewerIsDonor
        partnerName="Người nhận"
        onUpdated={() => undefined}
      />,
    );

    const place = screen.getByRole('textbox', { name: 'Địa điểm gặp' });
    expect(screen.getByText('0/255')).toBeTruthy();
    fireEvent.change(place, { target: { value: 'x'.repeat(255) } });
    expect(screen.getByText('255/255')).toBeTruthy();
    expect(screen.queryByText('Địa điểm tối đa 255 ký tự.')).toBeNull();
    fireEvent.change(place, { target: { value: 'x'.repeat(256) } });
    expect(screen.getByText('256/255')).toBeTruthy();
    expect(screen.getByText('Địa điểm tối đa 255 ký tự.')).toBeTruthy();
    expect(screen.getByRole<HTMLButtonElement>('button', { name: 'Gửi lịch hẹn' }).disabled).toBe(true);
  });
});

describe('ScheduleCard waiver', () => {
  const savedRequest: RequestResponse = {
    id: 101,
    itemId: 45,
    receiverId: 12,
    type: 'GIVE',
    status: 'AWAITING_LOGISTICS',
    deliveryMethod: 'IN_PERSON',
    meetingTime: new Date(Date.now() + 86_400_000).toISOString(),
    scheduleRevision: 1,
    meetingPlace: 'Cổng trường',
    createdAt: '2026-11-17T08:00:00Z',
    donorScheduleOkAt: '2026-11-17T09:00:00Z',
  };

  function scheduleCard(request: RequestResponse, viewerIsDonor = true) {
    return (
      <ScheduleCard
        request={request}
        currentUserId={viewerIsDonor ? 9 : 12}
        viewerIsDonor={viewerIsDonor}
        partnerName="Đối tác"
        onUpdated={() => undefined}
      />
    );
  }

  function waiverCheckbox() {
    return screen.getByRole<HTMLInputElement>('checkbox', { name: /cam kết trách nhiệm/ });
  }

  it('gửi phiên bản của lịch đang xem khi bấm đồng ý', async () => {
    const request = { ...savedRequest, scheduleRevision: 3 };
    const confirm = vi.spyOn(requestApi, 'confirmLogistics').mockResolvedValue(request);
    render(scheduleCard(request));

    fireEvent.click(waiverCheckbox());
    fireEvent.click(screen.getByRole('button', { name: /^Đồng ý/ }));
    await waitFor(() =>
      expect(confirm).toHaveBeenCalledWith(101, 9, expect.objectContaining({ scheduleRevision: 3 })),
    );
  });

  it('tải lịch mới và yêu cầu xem lại khi phiên bản đã thay đổi', async () => {
    const revised = { ...savedRequest, scheduleRevision: 2, meetingPlace: 'Thư viện' };
    const onUpdated = vi.fn();
    const confirm = vi.spyOn(requestApi, 'confirmLogistics')
      .mockRejectedValue({ code: 'REQUEST_SCHEDULE_CHANGED' });
    const reload = vi.spyOn(requestApi, 'get').mockResolvedValue(revised);
    render(
      <ScheduleCard request={savedRequest} currentUserId={9} viewerIsDonor partnerName="Đối tác" onUpdated={onUpdated} />,
    );
    fireEvent.click(waiverCheckbox());
    fireEvent.click(screen.getByRole('button', { name: /^Đồng ý/ }));

    expect(await screen.findByText(/Hãy xem lại thông tin mới trước khi xác nhận/)).toBeTruthy();
    expect(reload).toHaveBeenCalledWith(101, 9);
    expect(onUpdated).toHaveBeenCalledWith(revised);
    expect(confirm).toHaveBeenCalledTimes(1);
  });

  it.each(['IN_PERSON', 'SHIPPING'] as const)(
    'chỉ hiện nút đồng ý cho %s khi lịch đã có nhưng bên này chưa cam kết',
    async (deliveryMethod) => {
      const request = { ...savedRequest, deliveryMethod };
      const confirm = vi.spyOn(requestApi, 'confirmLogistics').mockResolvedValue(request);
      render(scheduleCard(request));

      const confirmButton = screen.getByRole<HTMLButtonElement>('button', { name: /^Đồng ý/ });
      expect(waiverCheckbox().checked).toBe(false);
      expect(confirmButton.disabled).toBe(true);
      expect(screen.queryByRole('button', { name: /^Gửi (lại )?(lịch hẹn|thông tin giao hàng)$/ })).toBeNull();

      fireEvent.click(waiverCheckbox());
      expect(confirmButton.disabled).toBe(false);
      fireEvent.click(confirmButton);
      await waitFor(() => expect(confirm).toHaveBeenCalledWith(101, 9, { scheduleRevision: 1, agreeToWaiver: true }));
    },
  );

  it('sends the waiver for the edited schedule when it is saved', async () => {
    const update = vi.spyOn(requestApi, 'updateSchedule').mockResolvedValue(savedRequest);
    render(scheduleCard(savedRequest));
    fireEvent.click(screen.getByRole('button', { name: /^Giao hàng/ }));
    fireEvent.click(waiverCheckbox());
    fireEvent.click(screen.getByRole('button', { name: 'Gửi lại thông tin giao hàng' }));

    await waitFor(() =>
      // Luật mới (GN-12): Giao hàng không dùng địa điểm, chủ bài ở Cho tặng cũng không cần địa chỉ
      expect(update).toHaveBeenCalledWith(101, 9, {
        deliveryMethod: 'SHIPPING',
        meetingPlace: undefined,
        meetingTime: expect.any(String),
        agreeToWaiver: true,
        shippingAddress: undefined,
      }),
    );
  });

  it.each([true, false])('restores the viewer waiver and clears it when the schedule changes (%s)', (isDonor) => {
    const savedWaiverAt = '2026-11-17T09:00:00Z';
    const request = {
      ...savedRequest,
      donorWaiverAt: isDonor ? savedWaiverAt : undefined,
      receiverWaiverAt: isDonor ? undefined : savedWaiverAt,
    };
    const { rerender } = render(scheduleCard(request, isDonor));
    expect(waiverCheckbox().checked).toBe(true);

    fireEvent.change(screen.getByRole('textbox', { name: 'Địa điểm gặp' }), { target: { value: 'Thư viện' } });
    expect(waiverCheckbox().checked).toBe(false);
    expect(screen.getByRole<HTMLButtonElement>('button', { name: 'Gửi lại lịch hẹn' }).disabled).toBe(true);
    fireEvent.click(waiverCheckbox());

    rerender(scheduleCard({ ...savedRequest, meetingPlace: 'Sân trường' }, isDonor));
    expect(waiverCheckbox().checked).toBe(false);
    expect(screen.getByRole<HTMLInputElement>('textbox', { name: 'Địa điểm gặp' }).value).toBe('Sân trường');
  });

  it('cho người đã gửi sửa lịch hoặc bỏ bản nháp và quay lại trạng thái chờ', () => {
    render(scheduleCard({ ...savedRequest, donorWaiverAt: savedRequest.donorScheduleOkAt }));
    const place = screen.getByRole<HTMLInputElement>('textbox', { name: 'Địa điểm gặp' });
    expect(screen.getByRole<HTMLButtonElement>('button', { name: 'Đã gửi, chờ Đối tác đồng ý' }).disabled).toBe(true);
    expect(screen.getByText('Bạn đã đồng ý')).toBeTruthy();
    expect(screen.getByText('Đối tác chưa đồng ý')).toBeTruthy();

    fireEvent.change(place, { target: { value: 'Thư viện' } });
    expect(screen.getByRole<HTMLButtonElement>('button', { name: 'Gửi lại lịch hẹn' }).disabled).toBe(true);
    expect(screen.getByText('Đánh dấu cam kết trách nhiệm giao nhận.')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Bỏ thay đổi' }));
    expect(place.value).toBe(savedRequest.meetingPlace);
    expect(waiverCheckbox().checked).toBe(true);
    expect(screen.getByRole('button', { name: 'Đã gửi, chờ Đối tác đồng ý' })).toBeTruthy();
  });

  it('giải thích khi lịch đã qua và cho đề xuất thời gian mới', () => {
    render(scheduleCard({ ...savedRequest, meetingTime: new Date(Date.now() - 86_400_000).toISOString() }, false));
    expect(screen.getByRole<HTMLButtonElement>('button', { name: 'Đồng ý lịch hẹn này' }).disabled).toBe(true);
    expect(screen.getAllByText(/Thời gian giao nhận đã qua/).length).toBeGreaterThan(0);
    fireEvent.change(screen.getByLabelText(/Thời gian gặp/), {
      target: { value: toLocalMeetingTimeValue(new Date(Date.now() + 86_400_000)) },
    });
    expect(screen.getByRole('button', { name: 'Gửi lại lịch hẹn' })).toBeTruthy();
  });
});

describe('ScheduleCard giao hàng', () => {
  const shippingRequest: RequestResponse = {
    id: 101,
    itemId: 45,
    receiverId: 12,
    donorId: 9,
    type: 'GIVE',
    status: 'AWAITING_LOGISTICS',
    deliveryMethod: 'SHIPPING',
    meetingTime: new Date(Date.now() + 86_400_000).toISOString(),
    scheduleRevision: 1,
    createdAt: '2026-11-17T08:00:00Z',
    donorScheduleOkAt: '2026-11-17T09:00:00Z',
    donorWaiverAt: '2026-11-17T09:00:00Z',
  };

  function card(request: RequestResponse, viewerIsDonor: boolean) {
    return (
      <ScheduleCard
        request={request}
        currentUserId={viewerIsDonor ? 9 : 12}
        viewerIsDonor={viewerIsDonor}
        partnerName="Bình"
        onUpdated={() => undefined}
      />
    );
  }

  it('người nhận thấy nhãn Ngày gửi hàng dự kiến, ô địa chỉ của mình và không có ô địa điểm', () => {
    render(card(shippingRequest, false));
    expect(screen.getByLabelText(/Ngày gửi hàng dự kiến/)).toBeTruthy();
    expect(screen.getByRole('textbox', { name: 'Địa chỉ nhận hàng của bạn' })).toBeTruthy();
    expect(screen.queryByRole('textbox', { name: 'Địa điểm gặp' })).toBeNull();
  });

  it('chủ bài ở Cho tặng không có ô địa chỉ, ở Trao đổi thì có', () => {
    render(card(shippingRequest, true));
    expect(screen.queryByRole('textbox', { name: 'Địa chỉ nhận hàng của bạn' })).toBeNull();
    cleanup();
    render(card({ ...shippingRequest, type: 'SWAP', offeredItemId: 77 }, true));
    expect(screen.getByRole('textbox', { name: 'Địa chỉ nhận hàng của bạn' })).toBeTruthy();
  });

  it('hiện trạng thái địa chỉ của bên kia mà không hiện nội dung', () => {
    render(card({ ...shippingRequest, receiverAddressProvided: true }, true));
    expect(screen.getByText(/Bình đã nhập địa chỉ nhận hàng/)).toBeTruthy();
    cleanup();
    render(card({ ...shippingRequest, receiverAddressProvided: false }, true));
    expect(screen.getByText(/Bình chưa nhập địa chỉ nhận hàng/)).toBeTruthy();
  });

  it('người nhận điền địa chỉ còn thiếu rồi đồng ý trong cùng một thao tác', async () => {
    const confirm = vi.spyOn(requestApi, 'confirmLogistics').mockResolvedValue(shippingRequest);
    const update = vi.spyOn(requestApi, 'updateSchedule');
    render(card(shippingRequest, false));
    fireEvent.click(screen.getByRole('checkbox'));
    const button = screen.getByRole<HTMLButtonElement>('button', { name: 'Đồng ý thông tin giao hàng' });
    expect(button.disabled).toBe(true);

    fireEvent.change(screen.getByRole('textbox', { name: 'Địa chỉ nhận hàng của bạn' }), {
      target: { value: '12 Lê Lợi' },
    });
    expect(button.disabled).toBe(false);
    fireEvent.click(button);

    await waitFor(() =>
      expect(confirm).toHaveBeenCalledWith(
        101,
        12,
        { scheduleRevision: 1, agreeToWaiver: true, shippingAddress: '12 Lê Lợi' },
      ),
    );
    expect(update).not.toHaveBeenCalled();
  });

  it('chủ bài trao đổi đổi ngày gửi thì đề xuất lại và giữ địa chỉ nhận hàng', async () => {
    const request = { ...shippingRequest, type: 'SWAP' as const, offeredItemId: 77, donorAddress: '5 Lê Lai' };
    const update = vi.spyOn(requestApi, 'updateSchedule').mockResolvedValue(request);
    render(card(request, true));
    fireEvent.change(screen.getByLabelText(/Ngày gửi hàng dự kiến/), {
      target: { value: toLocalMeetingTimeValue(new Date(Date.now() + 172_800_000)) },
    });
    fireEvent.click(screen.getByRole('checkbox'));
    fireEvent.click(screen.getByRole('button', { name: 'Gửi lại thông tin giao hàng' }));
    await waitFor(() => expect(update).toHaveBeenCalledWith(101, 9, expect.objectContaining({
      deliveryMethod: 'SHIPPING', shippingAddress: '5 Lê Lai',
    })));
  });

  it('hiện cách tính hạn gửi và hạn xác nhận theo cấu hình công khai', async () => {
    vi.spyOn(publicConfigApi, 'load').mockResolvedValue({
      meeting_max_days_ahead: 7,
      shipping_send_deadline_days: 4,
      auto_confirm_days_shipping: 8,
    });
    render(card(shippingRequest, true));
    expect(await screen.findByText(/đến 4 ngày sau ngày gửi dự kiến/)).toBeTruthy();
    expect(screen.getByText(/8 ngày kể từ lúc bên gửi đánh dấu đã gửi/)).toBeTruthy();
  });
});
