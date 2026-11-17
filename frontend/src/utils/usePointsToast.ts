import { useEffect, useRef } from 'react';
import { toast } from '../components/toast';
import { notificationApi, type NotificationResponse } from '../services/notificationApi';
import { isRealtimeConnected } from '../services/realtime';
import { useRealtimeRefresh } from './useRealtimeRefresh';

/** Loại thông báo backend gửi khi giao dịch hoàn tất và điểm uy tín đã được tính (ReputationService). */
export const POINTS_NOTIFICATION_TYPE = 'POINTS_AWARDED';
/** Loại thông báo backend gửi khi người dùng được lên hạng tín nhiệm (R13). */
export const RANK_UP_NOTIFICATION_TYPE = 'RANK_UP';

const SEEN_KEY_PREFIX = 'shareloop:pointsToastSeen:';
// Chỉ bật toast cho thông báo mới; thông báo cũ hơn mốc này người dùng xem trong chuông.
const MAX_AGE_MS = 24 * 60 * 60 * 1000;
const FALLBACK_POLL_MS = 30_000;

function readSeen(userId: string): Set<number> {
  try {
    const parsed: unknown = JSON.parse(localStorage.getItem(SEEN_KEY_PREFIX + userId) ?? '[]');
    return new Set(
      Array.isArray(parsed) ? parsed.filter((id): id is number => Number.isSafeInteger(id)) : [],
    );
  } catch {
    return new Set();
  }
}

function writeSeen(userId: string, seen: Set<number>) {
  try {
    // Giữ 50 id gần nhất là đủ để không bật lại toast cũ.
    localStorage.setItem(SEEN_KEY_PREFIX + userId, JSON.stringify([...seen].slice(-50)));
  } catch {
    // Trình duyệt chặn storage: chấp nhận có thể thấy lại toast ở lần tải trang sau.
  }
}

/** Câu toast ngắn: tiêu đề kèm câu đầu của nội dung (lý do cộng / không cộng điểm). */
export function pointsToastMessage(notification: NotificationResponse) {
  const extra = notification.body.match(/(lên hạng [^!.]+!|hồi 1 sao[^.]*\.)/i)?.[0];
  if (!extra) return `${notification.title}. Xem chi tiết ở Hồ sơ › Uy tín & Hạng.`;
  return `${notification.title}. Bạn đã ${extra.charAt(0).toLowerCase()}${extra.slice(1)}`;
}

export function isFreshPointsNotification(notification: NotificationResponse, now = Date.now()) {
  if (
    (notification.type !== POINTS_NOTIFICATION_TYPE && notification.type !== RANK_UP_NOTIFICATION_TYPE)
    || notification.isRead
  ) {
    return false;
  }
  const createdAt = Date.parse(notification.createdAt);
  return Number.isFinite(createdAt) ? now - createdAt <= MAX_AGE_MS : true;
}

/**
 * Hiện một thông báo nhỏ (toast) khi người dùng vừa được cộng điểm sau giao dịch hoàn tất hoặc lên hạng.
 * Nghe sự kiện thông báo realtime; nếu realtime chưa nối thì hỏi lại mỗi 30 giây.
 */
export function usePointsToast(userId: string | null) {
  const seenRef = useRef<Set<number>>(new Set());
  const checkingRef = useRef(false);

  const check = async () => {
    if (!userId || checkingRef.current || document.visibilityState === 'hidden') return;
    checkingRef.current = true;
    try {
      const latest = await notificationApi.list({ page: 0, size: 10 });
      const fresh = latest.filter(
        (notification) =>
          isFreshPointsNotification(notification) && !seenRef.current.has(notification.id),
      );
      for (const notification of fresh.reverse()) {
        seenRef.current.add(notification.id);
        if (notification.type === RANK_UP_NOTIFICATION_TYPE) {
          toast.success(notification.title);
        } else {
          const message = pointsToastMessage(notification);
          if (notification.title.startsWith('Bạn được cộng')) toast.success(message);
          else toast.warning(message);
        }
      }
      if (fresh.length) writeSeen(userId, seenRef.current);
    } catch {
      // Không tải được thì thôi: thông báo vẫn nằm trong chuông.
    } finally {
      checkingRef.current = false;
    }
  };

  useEffect(() => {
    if (!userId) return undefined;
    seenRef.current = readSeen(userId);
    void check();
    const intervalId = window.setInterval(() => {
      if (!isRealtimeConnected()) void check();
    }, FALLBACK_POLL_MS);
    return () => window.clearInterval(intervalId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userId]);

  useRealtimeRefresh(['NOTIFICATION'], () => {
    void check();
  });
}
