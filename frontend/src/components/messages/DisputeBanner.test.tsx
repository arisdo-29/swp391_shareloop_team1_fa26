// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { RequestDispute, RequestResponse } from '../../services/requestApi';
import { DisputeBanner } from './DisputeBanner';

vi.mock('../../services/reportApi', () => ({
  reportApi: { respond: vi.fn() },
  respondDispute: vi.fn(),
}));
vi.mock('../../services/mediaApi', () => ({ mediaApi: { upload: vi.fn() } }));

afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});

const dispute = (overrides: Partial<RequestDispute> = {}): RequestDispute => ({
  id: 7,
  reason: 'NOT_AS_DESCRIBED',
  openedByMe: false,
  status: 'AWAITING_RESPONSE',
  responseDueAt: '2099-01-01T03:00:00Z',
  respondedAt: null,
  canRespond: true,
  ...overrides,
});

const request = (disputes: RequestDispute[] | null): RequestResponse => ({
  id: 101,
  itemId: 45,
  receiverId: 12,
  type: 'GIVE',
  status: 'DISPUTED',
  createdAt: '2026-10-20T09:00:00Z',
  disputes,
});

function renderBanner(disputes: RequestDispute[] | null) {
  render(
    <MemoryRouter>
      <DisputeBanner
        request={request(disputes)}
        partnerName="An Chủ Bài"
        progressPath="/messages/101/progress"
        onResponded={vi.fn()}
      />
    </MemoryRouter>,
  );
}

describe('DisputeBanner', () => {
  it('bên bị khiếu nại thấy banner vàng có nút Giải trình ngay và liên kết Xem tiến trình', async () => {
    // given
    const user = userEvent.setup();
    renderBanner([dispute()]);

    // then
    expect(screen.getByText(/An Chủ Bài đã khiếu nại giao dịch này: Không đúng mô tả/)).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Xem tiến trình' }).getAttribute('href')).toBe('/messages/101/progress');

    // when
    await user.click(screen.getByRole('button', { name: 'Giải trình ngay' }));

    // then
    expect(screen.getByRole('heading', { name: 'Giải trình khiếu nại' })).toBeTruthy();
  });

  it('bên khiếu nại thấy banner thông tin, không có nút giải trình', () => {
    // when
    renderBanner([dispute({ openedByMe: true, canRespond: false })]);

    // then
    expect(screen.getByText(/Bạn đã khiếu nại giao dịch này: Không đúng mô tả/)).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Giải trình ngay' })).toBeNull();
  });

  it('quá hạn thì báo Admin sẽ kết luận, không còn nút', () => {
    // when
    renderBanner([dispute({ canRespond: false })]);

    // then
    expect(screen.getByText(/Đã quá hạn giải trình/)).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Giải trình ngay' })).toBeNull();
  });

  it('không hiện gì khi không có khiếu nại hoặc khiếu nại đã kết luận', () => {
    // when
    renderBanner(null);
    renderBanner([dispute({ status: 'RESOLVED', canRespond: false })]);

    // then
    expect(screen.queryByRole('alert')).toBeNull();
    expect(screen.queryByText(/khiếu nại/)).toBeNull();
  });

  it('giao dịch huỷ vì từ chối nhận: bên bị báo cáo thấy báo cáo vi phạm và nút Gửi giải trình', () => {
    // when
    render(
      <MemoryRouter>
        <DisputeBanner
          request={{
            ...request(null),
            status: 'CANCELLED',
            refusalReport: dispute({ id: 9, status: 'OPEN', responseDueAt: null }),
          }}
          partnerName="An Chủ Bài"
          progressPath="/messages/101/progress"
          onResponded={vi.fn()}
        />
      </MemoryRouter>,
    );

    // then
    expect(screen.getByText('An Chủ Bài đã từ chối nhận vì món không đúng mô tả.')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Gửi giải trình' })).toBeTruthy();
    expect(screen.queryByText(/khiếu nại/)).toBeNull();
  });
});
