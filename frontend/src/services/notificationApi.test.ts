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

  it('vẫn xếp thông báo yêu cầu cũ vào nhóm Giao dịch', () => {
    // when
    const category = notificationCategory('REQUEST_CANCELLED');

    // then
    expect(category).toBe('transaction');
  });
});
