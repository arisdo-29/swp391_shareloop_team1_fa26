// @vitest-environment jsdom

import { cleanup, renderHook, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { toast } from '../components/toast';
import { notificationApi, type NotificationResponse } from '../services/notificationApi';
import { isFreshPointsNotification, pointsToastMessage, usePointsToast } from './usePointsToast';

vi.mock('../components/toast', () => ({ toast: { success: vi.fn(), warning: vi.fn() } }));
vi.mock('../services/notificationApi', () => ({ notificationApi: { list: vi.fn() } }));
vi.mock('./useRealtimeRefresh', () => ({ useRealtimeRefresh: vi.fn() }));

afterEach(() => {
  cleanup();
  localStorage.clear();
  vi.clearAllMocks();
});

const base: NotificationResponse = {
  id: 1,
  type: 'POINTS_AWARDED',
  title: 'Bạn được cộng 10 điểm tích luỹ',
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
    expect(message).toBe('Bạn được cộng 10 điểm tích luỹ. Bạn đã lên hạng Bạc!');
  });

  it('chỉ dẫn tới trang uy tín khi không lên hạng', () => {
    expect(pointsToastMessage(base)).toBe(
      'Bạn được cộng 10 điểm tích luỹ. Xem chi tiết ở Hồ sơ › Hạng và Uy tín.',
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

describe('usePointsToast', () => {
  it('hiển thị riêng toast cộng điểm và toast lên hạng từ một giao dịch', async () => {
    const createdAt = new Date().toISOString();
    const pointsNotice = { ...base, createdAt };
    const rankNotice = {
      ...pointsNotice,
      id: 2,
      type: 'RANK_UP',
      title: 'Bạn đã lên hạng Bạc!',
      body: 'Còn 100 điểm nữa để lên hạng Vàng.',
    };
    vi.mocked(notificationApi.list).mockResolvedValue([rankNotice, pointsNotice]);

    renderHook(() => usePointsToast('api_7'));

    await waitFor(() => expect(toast.success).toHaveBeenCalledTimes(2));
    expect(toast.success).toHaveBeenNthCalledWith(1, pointsToastMessage(pointsNotice));
    expect(toast.success).toHaveBeenNthCalledWith(2, 'Bạn đã lên hạng Bạc!');
    expect(toast.warning).not.toHaveBeenCalled();
  });
});
