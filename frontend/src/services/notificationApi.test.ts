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

describe('notificationCategory với thông báo nạp tiền', () => {
  it('xếp TOPUP_RECONCILED vào nhóm Credit', () => {
    // when
    const category = notificationCategory('TOPUP_RECONCILED');

    // then
    expect(category).toBe('credit');
  });
});

describe('notificationCategory các loại thông báo mới của luật huỷ, dời lịch và giao hàng', () => {
  it.each([
    'CANCEL_REQUESTED',
    'CANCEL_REQUEST_WITHDRAWN',
    'CANCEL_REQUEST_REJECTED',
    'CANCEL_REQUEST_REMINDER',
    'RESCHEDULE_PROPOSED',
    'RESCHEDULE_ACCEPTED',
    'RESCHEDULE_REJECTED',
    'RESCHEDULE_EXPIRED',
    'RESCHEDULE_REMINDER',
    'SHIPMENT_SENT',
    'SHIP_DATE_REMINDER',
    'SHIP_OVERDUE_REMINDER',
    'SHIP_OVERDUE_NOTICE',
    'HANDOVER_STALE_REMINDER',
    'HANDOVER_AUTO_CONFIRM_REMINDER',
    'SCHEDULE_AUTO_CANCEL_REMINDER',
    'MEETING_REMINDER',
  ])('%s thuộc nhóm Giao dịch', (type) => {
    expect(notificationCategory(type)).toBe('transaction');
  });
});
