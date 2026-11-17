import { describe, expect, it } from 'vitest';
import type { NotificationResponse } from '../services/notificationApi';
import { isFreshPointsNotification, pointsToastMessage } from './usePointsToast';

const base: NotificationResponse = {
  id: 1,
  type: 'POINTS_AWARDED',
  title: 'Bạn được cộng 10 điểm uy tín',
  body: 'Cộng 10 điểm hoàn tất giao dịch món “Nồi cơm” (vai trò cho/tặng). Tổng điểm hiện tại: 60.',
  link: '/profile?tab=reputation',
  isRead: false,
  createdAt: '2026-11-16T05:00:00Z',
};

describe('pointsToastMessage', () => {
  it('ghép tiêu đề với câu lên hạng khi có', () => {
    const message = pointsToastMessage({
      ...base,
      body: `${base.body} Chúc mừng, bạn đã lên hạng Bạc!`,
    });
    expect(message).toBe('Bạn được cộng 10 điểm uy tín. Bạn đã lên hạng Bạc!');
  });

  it('chỉ dẫn tới trang uy tín khi không lên hạng', () => {
    expect(pointsToastMessage(base)).toBe(
      'Bạn được cộng 10 điểm uy tín. Xem chi tiết ở Hồ sơ › Uy tín & Hạng.',
    );
  });
});

describe('isFreshPointsNotification', () => {
  const now = Date.parse('2026-11-16T06:00:00Z');

  it('nhận thông báo cộng điểm chưa đọc trong 24 giờ', () => {
    expect(isFreshPointsNotification(base, now)).toBe(true);
  });

  it('nhận thông báo lên hạng RANK_UP chưa đọc trong 24 giờ (R13)', () => {
    expect(
      isFreshPointsNotification(
        {
          ...base,
          type: 'RANK_UP',
          title: 'Bạn đã lên hạng Bạc!',
          body: 'Còn 100 điểm nữa để lên hạng Vàng.',
        },
        now,
      ),
    ).toBe(true);
  });

  it('bỏ qua thông báo đã đọc, loại khác hoặc quá cũ', () => {
    expect(isFreshPointsNotification({ ...base, isRead: true }, now)).toBe(false);
    expect(isFreshPointsNotification({ ...base, type: 'NEW_REQUEST' }, now)).toBe(false);
    expect(isFreshPointsNotification({ ...base, createdAt: '2026-11-14T05:00:00Z' }, now)).toBe(
      false,
    );
  });
});
