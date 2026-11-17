import { describe, expect, it } from 'vitest';
import {
  notificationActionLabel,
  notificationIcon,
  notificationTarget,
} from './notificationDisplay';

describe('notificationTarget', () => {
  it('B24: BOOST_ENDING_SOON giữ link mở hộp Đẩy bài do backend gửi', () => {
    expect(
      notificationTarget({
        type: 'BOOST_ENDING_SOON',
        link: '/activities?boost=31&until=1790000000',
      }),
    ).toBe('/activities?boost=31&until=1790000000');
  });

  it('B24: BOOST_ENDING_SOON gửi trước bản sửa (link /items/{id}) cũng mở hộp Đẩy bài', () => {
    expect(notificationTarget({ type: 'BOOST_ENDING_SOON', link: '/items/31' })).toBe(
      '/activities?boost=31',
    );
  });

  it('loại khác giữ nguyên link; link ngoài hoặc trống thì không mở', () => {
    expect(notificationTarget({ type: 'BOOST_ENDED', link: '/items/31' })).toBe('/items/31');
    expect(notificationTarget({ type: 'NEW_REQUEST', link: '//evil.example' })).toBeNull();
    expect(notificationTarget({ type: 'NEW_REQUEST', link: null })).toBeNull();
  });
});

describe('nhãn và icon', () => {
  it('thông báo sắp hết đẩy có icon đẩy bài và nút "Đẩy tiếp bài"', () => {
    expect(notificationIcon('BOOST_ENDING_SOON')).toBe('boost');
    expect(notificationActionLabel('BOOST_ENDING_SOON')).toBe('Đẩy tiếp bài');
    expect(notificationIcon('NEW_REQUEST')).toBeNull();
  });

  it('B19: nhắc sắp hết hạn 48 giờ và 24 giờ có icon đồng hồ cát và nút "Gia hạn bài"', () => {
    for (const type of ['ITEM_EXPIRING_48H', 'ITEM_EXPIRING_24H']) {
      expect(notificationIcon(type)).toBe('expiring');
      expect(notificationActionLabel(type)).toBe('Gia hạn bài');
      expect(notificationTarget({ type, link: '/activities?renew=31&until=1790000000' })).toBe(
        '/activities?renew=31&until=1790000000',
      );
    }
    expect(notificationActionLabel('NEW_REQUEST')).toBe('Xem chi tiết');
  });
});
