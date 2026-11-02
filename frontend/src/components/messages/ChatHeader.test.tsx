// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { MessageResponse } from '../../services/chatApi';
import type { RequestResponse } from '../../services/requestApi';
import { ChatHeader } from './ChatHeader';
import { ChatMessage } from './ChatMessage';
import type { ApiConversation } from './conversation';

const DONOR_ID = 9;
const RECEIVER_ID = 12;

const conversation = (overrides: Partial<RequestResponse> = {}): ApiConversation => ({
  request: {
    id: 101,
    itemId: 45,
    receiverId: RECEIVER_ID,
    type: 'GIVE',
    status: 'AWAITING_HANDOVER',
    createdAt: '2026-10-20T09:00:00Z',
    itemTitle: 'Xe đạp cũ',
    donorId: DONOR_ID,
    donorName: 'An Chủ Bài',
    receiverName: 'Bình Người Nhận',
    ...overrides,
  },
});

function LocationProbe() {
  const location = useLocation();
  return <span data-testid="location">{location.pathname}</span>;
}

function renderHeader(
  viewerId: number,
  current: ApiConversation,
  canReport = true,
  onReport = vi.fn(),
) {
  render(
    <MemoryRouter initialEntries={['/requests/101']}>
      <Routes>
        <Route
          path="/requests/101"
          element={
            <ChatHeader
              conversation={current}
              currentUserId={viewerId}
              canReport={canReport}
              onBack={vi.fn()}
              onReport={onReport}
            />
          }
        />
        <Route path="*" element={<LocationProbe />} />
      </Routes>
    </MemoryRouter>,
  );
}

afterEach(() => cleanup());

describe('ChatHeader', () => {
  it('người nhận thấy tên và vai Người cho tặng của chủ bài', () => {
    // when
    renderHeader(RECEIVER_ID, conversation());

    // then
    expect(screen.getByRole('heading', { name: 'An Chủ Bài' })).toBeTruthy();
    expect(screen.getByText('Người cho tặng')).toBeTruthy();
  });

  it('chủ bài thấy tên và vai Người nhận', () => {
    // when
    renderHeader(DONOR_ID, conversation());

    // then
    expect(screen.getByRole('heading', { name: 'Bình Người Nhận' })).toBeTruthy();
    expect(screen.getByText('Người nhận')).toBeTruthy();
  });

  it('giao dịch Trao đổi gọi vai theo bài đăng', () => {
    // when
    renderHeader(RECEIVER_ID, conversation({ type: 'SWAP' }));

    // then
    expect(screen.getByText('Chủ bài đăng')).toBeTruthy();
  });

  it('Xem hồ sơ mở hồ sơ của người bên kia', async () => {
    // given
    const user = userEvent.setup();
    renderHeader(RECEIVER_ID, conversation());

    // when
    await user.click(screen.getByRole('button', { name: 'Xem hồ sơ' }));

    // then
    expect(screen.getByTestId('location').textContent).toBe(`/users/${DONOR_ID}`);
  });

  it('chưa biết chủ bài thì ẩn Xem hồ sơ', () => {
    // when
    renderHeader(RECEIVER_ID, conversation({ donorId: null }));

    // then
    expect(screen.queryByRole('button', { name: 'Xem hồ sơ' })).toBeNull();
  });

  it('Xem tiến trình mở trang tiến trình của đúng giao dịch', async () => {
    // given
    const user = userEvent.setup();
    renderHeader(RECEIVER_ID, conversation());

    // when
    await user.click(screen.getByRole('button', { name: 'Xem tiến trình' }));

    // then
    expect(screen.getByTestId('location').textContent).toBe('/messages/101/progress');
  });

  it('chỉ có một thẻ món, kèm nhãn loại, nhãn trạng thái và thanh 4 đoạn', () => {
    // when
    renderHeader(RECEIVER_ID, conversation());

    // then
    expect(screen.getAllByText('Xe đạp cũ')).toHaveLength(1);
    expect(screen.getByText('Cho tặng')).toBeTruthy();
    expect(screen.getAllByText('Chờ giao nhận').length).toBeGreaterThan(0);
    expect(screen.getByLabelText('Đã xong 2 trên 4 bước')).toBeTruthy();
  });

  it('Request chưa vào giao dịch thì không vẽ thanh tiến trình', () => {
    // when
    renderHeader(RECEIVER_ID, conversation({ status: 'PENDING' }));

    // then
    expect(screen.queryByLabelText(/trên 4 bước/)).toBeNull();
  });

  it('Báo cáo gọi lại hành động cũ, ẩn khi không được báo cáo', async () => {
    // given
    const user = userEvent.setup();
    const onReport = vi.fn();
    renderHeader(RECEIVER_ID, conversation(), true, onReport);

    // when
    await user.click(screen.getByRole('button', { name: 'Báo cáo' }));

    // then
    expect(onReport).toHaveBeenCalledOnce();
    cleanup();
    renderHeader(RECEIVER_ID, conversation(), false);
    expect(screen.queryByRole('button', { name: 'Báo cáo' })).toBeNull();
  });
});

describe('ChatMessage', () => {
  const systemMessage: MessageResponse = {
    id: 1,
    requestId: 101,
    senderId: 0,
    kind: 'SYSTEM',
    content: 'Giao dịch đã hoàn tất.',
    mediaId: null,
    refItemId: null,
    itemCard: null,
    offerStatus: null,
    moderation: 'PASS',
    blockedReason: null,
    createdAt: '2026-10-30T10:00:00Z',
  };

  it('tin hệ thống có liên kết Xem chi tiết sang trang tiến trình', () => {
    // when
    render(
      <MemoryRouter>
        <ChatMessage
          entry={systemMessage}
          mine={false}
          progressPath="/messages/101/progress"
          onOpenItem={vi.fn()}
        />
      </MemoryRouter>,
    );

    // then
    expect(screen.getByText('Giao dịch đã hoàn tất.')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Xem chi tiết' }).getAttribute('href')).toBe(
      '/messages/101/progress',
    );
  });
});
