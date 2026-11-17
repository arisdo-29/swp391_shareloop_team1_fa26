// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import type { RequestResponse } from '../../services/requestApi';
import { ScheduleCard } from './ScheduleCard';

afterEach(cleanup);

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
