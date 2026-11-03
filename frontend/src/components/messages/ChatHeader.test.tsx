// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { MessageResponse, PinnedItemResponse } from '../../services/chatApi';
import type { ItemResponse } from '../../services/itemApi';
import type { RequestResponse } from '../../services/requestApi';
import { ChatHeader } from './ChatHeader';
import { ChatMessage } from './ChatMessage';
import type { ApiConversation } from './conversation';

const DONOR_ID = 9;
const RECEIVER_ID = 12;

const item = (overrides: Partial<ItemResponse> = {}): ItemResponse => ({
  id: 45,
  donorId: DONOR_ID,
  categoryId: 1,
  areaId: 1,
  offerType: 'SWAP',
  title: 'Xe đạp cũ',
  description: 'Xe còn tốt, phanh hoạt động bình thường.',
  desiredItem: 'Mũ bảo hiểm',
  condition: 'GOOD',
  defectNote: null,
  brand: null,
  images: ['/api/v1/media/45'],
  defectImages: [],
  operatingStatus: null,
  status: 'APPROVED',
  createdAt: '2026-10-20T08:00:00Z',
  updatedAt: '2026-10-20T08:00:00Z',
  expireAt: null,
  boostedUntil: null,
  donorName: 'An Chủ Bài',
  ...overrides,
});

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
  return <span data-testid="location">{location.pathname}{location.search}</span>;
}

function renderHeader(
  viewerId: number,
  current: ApiConversation,
  canReport = true,
  onReport = vi.fn(),
  pinnedItems: Array<Pick<PinnedItemResponse, 'id' | 'imageUrl'>> = [],
) {
  render(
    <MemoryRouter initialEntries={['/requests/101']}>
      <Routes>
        <Route
          path="/requests/101"
          element={
            <ChatHeader
              conversation={current}
              pinnedItems={pinnedItems}
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

  it('chủ bài xem đủ hai món SWAP khi yêu cầu vẫn đang chờ và mở đúng món đề xuất', async () => {
    const user = userEvent.setup();
    const offeredItem = item({
      id: 77,
      donorId: RECEIVER_ID,
      title: 'Mũ bảo hiểm 3/4',
      description: 'Mũ ít sử dụng, kính còn trong.',
      desiredItem: 'Xe đạp',
      condition: 'LIKE_NEW',
      images: ['/api/v1/media/77'],
      donorName: 'Bình Người Nhận',
    });
    renderHeader(DONOR_ID, {
      ...conversation({
        type: 'SWAP',
        status: 'PENDING',
        offeredItemId: 77,
        offeredItemTitle: offeredItem.title,
      }),
      item: item(),
      offeredItem,
    });

    expect(screen.getByText('Món đồ của bạn')).toBeTruthy();
    expect(screen.getByText('Món đồ bạn sẽ nhận')).toBeTruthy();
    expect(screen.getByText('Mũ bảo hiểm 3/4')).toBeTruthy();
    expect(screen.getByText('Ít sử dụng')).toBeTruthy();
    expect(screen.queryByText('Mũ ít sử dụng, kính còn trong.')).toBeNull();
    expect(screen.queryByText('Xe còn tốt, phanh hoạt động bình thường.')).toBeNull();
    expect(screen.getByText('Chủ sở hữu: Bình Người Nhận')).toBeTruthy();
    expect(screen.getByRole('img', { name: 'Mũ bảo hiểm 3/4' }).getAttribute('src'))
      .toContain('/api/v1/media/77');
    expect(screen.getAllByRole('button', { name: 'Xem chi tiết' })).toHaveLength(2);

    await user.click(screen.getAllByRole('button', { name: 'Xem chi tiết' })[1]);
    expect(screen.getByTestId('location').textContent).toBe(
      '/items/77?from=messages&requestId=101',
    );
  });

  it('SWAP thiếu dữ liệu món đề xuất thì báo rõ và không tự tạo nội dung', () => {
    renderHeader(DONOR_ID, conversation({
      type: 'SWAP',
      status: 'PENDING',
      offeredItemId: undefined,
      offeredItemTitle: null,
    }));

    expect(screen.getByText('Chưa có thông tin món đồ đề xuất cho yêu cầu này.'))
      .toBeTruthy();
    expect(screen.getAllByRole('button', { name: 'Xem chi tiết' })).toHaveLength(1);
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

  it('uses the pinned offered item image when item detail is unavailable in both swap views', async () => {
    const user = userEvent.setup();
    const mainItem = item({
      title: 'Mon cua chu bai',
      images: [' ', '/api/v1/media/main-valid'],
    });
    const swapConversation: ApiConversation = {
      ...conversation({
        type: 'SWAP',
        status: 'PENDING',
        itemTitle: mainItem.title,
        offeredItemId: 77,
        offeredItemTitle: 'Dong ho treo tuong doi chim cong',
      }),
      item: mainItem,
    };
    const pinnedItems = [
      { id: 45, imageUrl: '/api/v1/media/main-pinned' },
      { id: 77, imageUrl: '/api/v1/media/clock' },
    ];

    renderHeader(DONOR_ID, swapConversation, true, vi.fn(), pinnedItems);

    const mainImage = screen.getByRole('img', { name: 'Mon cua chu bai' });
    const offeredImage = screen.getByRole('img', { name: 'Dong ho treo tuong doi chim cong' });
    expect(mainImage.getAttribute('src')).toContain('/api/v1/media/main-valid');
    expect(offeredImage.getAttribute('src')).toContain('/api/v1/media/clock');
    expect(offeredImage.className).toContain('size-20');
    expect(offeredImage.className).toContain('object-cover');
    expect(screen.queryByText('Chưa có ảnh')).toBeNull();

    await user.click(offeredImage.closest('button') as HTMLButtonElement);
    expect(screen.getByTestId('location').textContent).toBe(
      '/items/77?from=messages&requestId=101',
    );

    cleanup();
    renderHeader(RECEIVER_ID, swapConversation, true, vi.fn(), pinnedItems);
    expect(screen.getByRole('img', { name: 'Mon cua chu bai' }).getAttribute('src'))
      .toContain('/api/v1/media/main-valid');
    expect(screen.getByRole('img', { name: 'Dong ho treo tuong doi chim cong' }).getAttribute('src'))
      .toContain('/api/v1/media/clock');
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
