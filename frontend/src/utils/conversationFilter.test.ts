import { describe, expect, it } from 'vitest';
import type { ItemRequestStatus } from '../types/domain';
import {
  isClosedRequestStatus,
  matchesConversationFilter,
  matchesConversationQuery,
} from './conversationFilter';

const openStatuses: ItemRequestStatus[] = [
  'PENDING',
  'RESERVED',
  'AWAITING_LOGISTICS',
  'LOGISTICS_CONFIRMED',
  'AWAITING_HANDOVER',
  'DISPUTED',
];
const closedStatuses: ItemRequestStatus[] = ['COMPLETED', 'CANCELLED', 'REJECTED'];

describe('Bộ lọc danh sách cuộc trò chuyện', () => {
  it.each(closedStatuses)('xếp %s vào tab Đã đóng, không vào Đang giao dịch', (status) => {
    // when, then
    expect(isClosedRequestStatus(status)).toBe(true);
    expect(matchesConversationFilter('closed', status, false)).toBe(true);
    expect(matchesConversationFilter('open', status, false)).toBe(false);
  });

  it.each(openStatuses)('xếp %s vào tab Đang giao dịch, không vào Đã đóng', (status) => {
    // when, then
    expect(matchesConversationFilter('open', status, false)).toBe(true);
    expect(matchesConversationFilter('closed', status, false)).toBe(false);
  });

  it('tab Chưa đọc chỉ giữ cuộc có tin chưa đọc, bất kể trạng thái', () => {
    // when, then
    expect(matchesConversationFilter('unread', 'COMPLETED', true)).toBe(true);
    expect(matchesConversationFilter('unread', 'PENDING', false)).toBe(false);
  });

  it('tab Tất cả giữ mọi cuộc', () => {
    // when, then
    expect(matchesConversationFilter('all', 'CANCELLED', false)).toBe(true);
    expect(matchesConversationFilter('all', 'PENDING', true)).toBe(true);
  });
});

describe('Tìm cuộc trò chuyện', () => {
  it('khớp không phân biệt hoa thường trong tên món hoặc tên người', () => {
    // given
    const parts = ['Nồi cơm điện', 'Trần Thị Bình', 'Hoàn tất'];

    // when, then
    expect(matchesConversationQuery('nồi CƠM', parts)).toBe(true);
    expect(matchesConversationQuery('bình', parts)).toBe(true);
    expect(matchesConversationQuery('xe đạp', parts)).toBe(false);
  });

  it('ô tìm trống hoặc chỉ có khoảng trắng thì không lọc', () => {
    // when, then
    expect(matchesConversationQuery('   ', ['Bất kỳ'])).toBe(true);
  });
});
