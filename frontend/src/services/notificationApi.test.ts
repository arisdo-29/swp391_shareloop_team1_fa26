import { describe, expect, it } from 'vitest';
import { notificationCategory } from './notificationApi';

describe('notificationCategory', () => {
  it.each(['OFFER_CHANGE_PROPOSED', 'OFFER_CHANGE_ACCEPTED', 'OFFER_CHANGE_DECLINED'])(
    'xếp thông báo đổi món %s vào nhóm Giao dịch',
    (type) => {
      // when
      const category = notificationCategory(type);

      // then
      expect(category).toBe('transaction');
    },
  );

  it('xếp thông báo món từng yêu cầu đã mở lại vào nhóm Giao dịch', () => {
    // when
    const category = notificationCategory('REQUESTED_ITEM_REOPENED');

    // then
    expect(category).toBe('transaction');
  });

  it('giữ thông báo bài được hiện lại sau báo cáo ở nhóm Kiểm duyệt', () => {
    // when
    const category = notificationCategory('ITEM_REOPENED');

    // then
    expect(category).toBe('moderation');
  });

  it('vẫn xếp thông báo yêu cầu cũ vào nhóm Giao dịch', () => {
    // when
    const category = notificationCategory('REQUEST_CANCELLED');

    // then
    expect(category).toBe('transaction');
  });
});
