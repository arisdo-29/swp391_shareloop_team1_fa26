// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { requestApi, type RequestResponse } from '../../services/requestApi';
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
    expect(screen.getByText('Địa điểm tối đa 255 ký tự.')).toBeTruthy();
    fireEvent.change(place, { target: { value: 'x'.repeat(256) } });
    expect(screen.getByText('256/255')).toBeTruthy();
    expect((screen.getByRole('button', { name: 'Lưu lịch hẹn' }) as HTMLButtonElement).disabled).toBe(true);
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

  it.each(['IN_PERSON', 'SHIPPING'] as const)(
    'requires consent before saving %s and lets the legacy proposer confirm',
    async (deliveryMethod) => {
      const request = { ...savedRequest, deliveryMethod };
      const confirm = vi.spyOn(requestApi, 'confirmLogistics').mockResolvedValue(request);
      render(scheduleCard(request));

      const save = screen.getByRole<HTMLButtonElement>('button', { name: 'Lưu lịch hẹn' });
      const confirmButton = screen.getByRole<HTMLButtonElement>('button', { name: 'Xác nhận lịch' });
      expect(waiverCheckbox().checked).toBe(false);
      expect(save.disabled).toBe(true);
      expect(confirmButton.disabled).toBe(true);

      fireEvent.click(waiverCheckbox());
      expect(save.disabled).toBe(false);
      expect(confirmButton.disabled).toBe(false);
      fireEvent.click(confirmButton);
      await waitFor(() => expect(confirm).toHaveBeenCalledWith(101, 9, { agreeToWaiver: true }));
    },
  );

  it('sends the waiver for the edited schedule when it is saved', async () => {
    const update = vi.spyOn(requestApi, 'updateSchedule').mockResolvedValue(savedRequest);
    render(scheduleCard(savedRequest));
    fireEvent.click(screen.getByRole('button', { name: /Giao hàng/ }));
    fireEvent.click(waiverCheckbox());
    fireEvent.click(screen.getByRole('button', { name: 'Lưu lịch hẹn' }));

    await waitFor(() =>
      expect(update).toHaveBeenCalledWith(101, 9, {
        deliveryMethod: 'SHIPPING',
        meetingPlace: 'Cổng trường',
        meetingTime: expect.any(String),
        agreeToWaiver: true,
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
    expect(screen.getByRole<HTMLButtonElement>('button', { name: 'Lưu lịch hẹn' }).disabled).toBe(true);
    fireEvent.click(waiverCheckbox());

    rerender(scheduleCard({ ...savedRequest, meetingPlace: 'Sân trường' }, isDonor));
    expect(waiverCheckbox().checked).toBe(false);
    expect(screen.getByRole<HTMLInputElement>('textbox', { name: 'Địa điểm gặp' }).value).toBe('Sân trường');
  });
});
