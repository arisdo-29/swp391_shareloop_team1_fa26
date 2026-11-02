// @vitest-environment jsdom

import { cleanup, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { RequestResponse } from '../../services/requestApi';
import { markRequestChatSeen } from '../../utils/request';
import { ConversationList } from './ConversationList';
import type { ApiConversation } from './conversation';

const VIEWER_ID = 12;

const conversation = (overrides: Partial<RequestResponse>): ApiConversation => ({
  request: {
    id: 1,
    itemId: 45,
    receiverId: VIEWER_ID,
    type: 'GIVE',
    status: 'PENDING',
    createdAt: '2026-10-20T09:00:00Z',
    donorId: 9,
    donorName: 'An Chủ Bài',
    receiverName: 'Bình Người Nhận',
    ...overrides,
  },
});

const conversations = [
  conversation({
    id: 1,
    itemTitle: 'Nồi cơm điện',
    itemImage: '/api/v1/media/101',
    status: 'AWAITING_HANDOVER',
    donorName: 'An',
  }),
  conversation({ id: 2, itemTitle: 'Xe đạp cũ', status: 'COMPLETED', donorName: 'Cường' }),
  conversation({
    id: 3,
    itemTitle: 'Bàn học gỗ',
    status: 'CANCELLED',
    donorName: 'Dung',
    lastMessageAt: '2026-10-30T10:00:00Z',
  }),
];

function renderList(selectedRequestId: number | null = null) {
  render(
    <MemoryRouter>
      <ConversationList
        conversations={conversations}
        currentUserId={VIEWER_ID}
        selectedRequestId={selectedRequestId}
        loading={false}
        error=""
        onRetry={vi.fn()}
        onOpen={vi.fn()}
        canSelect={() => false}
        selectSubmitting={false}
        onSelectCandidate={vi.fn()}
      />
    </MemoryRouter>,
  );
}

function visibleTitles() {
  const list = screen.getByRole('complementary', { name: 'Cuộc trò chuyện' });
  return ['Nồi cơm điện', 'Xe đạp cũ', 'Bàn học gỗ'].filter(
    (title) => within(list).queryByText(title) !== null,
  );
}

beforeEach(() => {
  localStorage.clear();
  // Hai cuộc đầu đã xem; cuộc 3 có tin mới sau lần xem cuối nên chưa đọc.
  markRequestChatSeen(1);
  markRequestChatSeen(2);
});

afterEach(() => cleanup());

describe('ConversationList', () => {
  it('hiện tiêu đề, số cuộc và 4 tab lọc, mặc định Tất cả', () => {
    // when
    renderList();

    // then
    expect(screen.getByRole('heading', { level: 1, name: 'Tin nhắn' })).toBeTruthy();
    expect(screen.getByText('3 cuộc')).toBeTruthy();
    const tabs = screen.getAllByRole('tab').map((tab) => tab.textContent);
    expect(tabs).toEqual(['Tất cả', 'Chưa đọc', 'Đang giao dịch', 'Đã đóng']);
    expect(screen.getByRole('tab', { name: 'Tất cả' }).getAttribute('aria-selected')).toBe('true');
    expect(visibleTitles()).toEqual(['Nồi cơm điện', 'Xe đạp cũ', 'Bàn học gỗ']);
  });

  it('tab Đang giao dịch chỉ giữ Request chưa kết thúc', async () => {
    // given
    const user = userEvent.setup();
    renderList();

    // when
    await user.click(screen.getByRole('tab', { name: 'Đang giao dịch' }));

    // then
    expect(visibleTitles()).toEqual(['Nồi cơm điện']);
  });

  it('tab Đã đóng giữ Request hoàn tất, bị huỷ, không được chọn', async () => {
    // given
    const user = userEvent.setup();
    renderList();

    // when
    await user.click(screen.getByRole('tab', { name: 'Đã đóng' }));

    // then
    expect(visibleTitles()).toEqual(['Xe đạp cũ', 'Bàn học gỗ']);
  });

  it('tab Chưa đọc giữ cuộc có tin mới và hiện dấu chưa đọc gọn', async () => {
    // given
    const user = userEvent.setup();
    renderList();

    // when
    await user.click(screen.getByRole('tab', { name: 'Chưa đọc' }));

    // then
    expect(visibleTitles()).toEqual(['Bàn học gỗ']);
    expect(screen.getByLabelText('Có tin chưa đọc')).toBeTruthy();
  });

  it('cuộc đang mở không tính là chưa đọc', async () => {
    // given
    const user = userEvent.setup();
    renderList(3);

    // when
    await user.click(screen.getByRole('tab', { name: 'Chưa đọc' }));

    // then
    expect(visibleTitles()).toEqual([]);
    expect(screen.getByText('Không có cuộc trò chuyện phù hợp')).toBeTruthy();
  });

  it('ô tìm lọc theo tên món hoặc tên người, kết hợp với tab', async () => {
    // given
    const user = userEvent.setup();
    renderList();

    // when
    await user.type(screen.getByRole('searchbox', { name: 'Tìm món hoặc người' }), 'cường');

    // then
    expect(visibleTitles()).toEqual(['Xe đạp cũ']);

    // when: thêm tab Đang giao dịch thì không còn cuộc nào khớp
    await user.click(screen.getByRole('tab', { name: 'Đang giao dịch' }));

    // then
    expect(visibleTitles()).toEqual([]);
  });

  it('nhãn trạng thái lấy từ requestStatusLabel', () => {
    // when
    renderList();

    // then
    expect(screen.getByText('Chờ giao nhận')).toBeTruthy();
    expect(screen.getByText('Hoàn tất')).toBeTruthy();
    expect(screen.getByText('Đã hủy')).toBeTruthy();
  });

  it('hiển thị thumbnail từ API, placeholder và liên kết đúng tới chi tiết món đồ', () => {
    // when
    renderList();

    // then
    const itemLink = screen.getByRole('link', { name: 'Mở chi tiết Nồi cơm điện' });
    expect(itemLink.getAttribute('href')).toBe('/items/45?from=messages&requestId=1');

    const thumbnail = within(itemLink).getByRole('img', { name: 'Nồi cơm điện' });
    expect(thumbnail.tagName).toBe('IMG');
    expect(thumbnail.getAttribute('src')).toContain('/api/v1/media/101');
    expect(thumbnail.className).toContain('size-[52px]');
    expect(thumbnail.className).toContain('rounded-md');
    expect(
      screen.getByRole('button', { name: 'Mở cuộc trò chuyện với An' }).parentElement?.className,
    ).toContain('h-[84px]');

    expect(screen.getByRole('img', { name: 'Xe đạp cũ' }).tagName).toBe('DIV');
  });
});
