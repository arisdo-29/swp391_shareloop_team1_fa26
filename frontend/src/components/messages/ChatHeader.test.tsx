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