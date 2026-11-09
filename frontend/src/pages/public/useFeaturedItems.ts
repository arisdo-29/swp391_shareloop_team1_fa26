import { useEffect, useState } from 'react';
import { itemApi, type ItemResponse } from '../../services/itemApi';

// Gọi lại trễ hơn nextRotateAt một chút để chắc chắn backend đã sang nhóm mới (lệch đồng hồ, độ trễ mạng).
export const FEATURED_REFRESH_DELAY_MS = 1000;
// Chỉ dùng khi lần tải đầu đã lỗi nên chưa biết mốc đổi nhóm từ backend: thử lại sau khoảng này.
export const FEATURED_RETRY_WITHOUT_SCHEDULE_MS = 60_000;
// setTimeout chạy ngay nếu độ trễ vượt số nguyên 32-bit; chặn lại phòng khi API trả mốc rất xa.
const MAX_TIMEOUT_MS = 2_147_483_647;

export interface FeaturedRotation {
  items: ItemResponse[];
  // Đổi mỗi khi backend trả nhóm mới, dùng làm key để chạy hiệu ứng chuyển nhóm.
  rotatedAt: string | null;
}

/**
 * Nhóm bài đẩy của banner trang chủ (GET /items/featured). Backend quyết định nhóm và mốc đổi nhóm; hook chỉ hẹn giờ
 * tải lại tới nextRotateAt. Lỗi mạng thì giữ nhóm cũ và thử lại ở mốc kế tiếp.
 */
export function useFeaturedItems(limit: number, viewerKey: number | undefined, reloadKey = 0): FeaturedRotation {
  const [rotation, setRotation] = useState<FeaturedRotation>({ items: [], rotatedAt: null });

  useEffect(() => {
    let active = true;
    let timer: number | undefined;
    // Mốc đổi nhóm và độ dài một lượt theo lần tải thành công gần nhất (đều lấy từ API).
    let nextRotateAtMs: number | null = null;
    let rotationLengthMs: number | null = null;

    const scheduleAt = (dueAtMs: number) => {
      window.clearTimeout(timer);
      const delayMs = Math.max(0, dueAtMs - Date.now()) + FEATURED_REFRESH_DELAY_MS;
      timer = window.setTimeout(load, Math.min(delayMs, MAX_TIMEOUT_MS));
    };

    // Lỗi mạng: mốc kế tiếp là mốc đổi nhóm sau mốc vừa lỡ, tính bằng đúng độ dài lượt backend đã trả.
    const scheduleRetry = () => {
      if (nextRotateAtMs !== null && rotationLengthMs !== null && rotationLengthMs > 0) {
        while (nextRotateAtMs <= Date.now()) nextRotateAtMs += rotationLengthMs;
        scheduleAt(nextRotateAtMs);
        return;
      }
      window.clearTimeout(timer);
      timer = window.setTimeout(load, FEATURED_RETRY_WITHOUT_SCHEDULE_MS);
    };

    async function load() {
      try {
        const response = await itemApi.featured({ limit, viewerKey });
        if (!active) return;
        setRotation({ items: response.items, rotatedAt: response.rotatedAt });
        const parsedNext = Date.parse(response.nextRotateAt);
        // Mốc không đọc được thì không hẹn theo mốc (tránh timer chạy liên tục), chờ khoảng thử lại cố định.
        if (!Number.isFinite(parsedNext)) {
          nextRotateAtMs = null;
          scheduleRetry();
          return;
        }
        nextRotateAtMs = parsedNext;
        rotationLengthMs = parsedNext - Date.parse(response.rotatedAt);
        scheduleAt(parsedNext);
      } catch {
        if (active) scheduleRetry();
      }
    }

    // Trình duyệt làm chậm timer ở tab ẩn: quay lại tab mà đã quá mốc đổi nhóm thì tải ngay.
    const onVisibilityChange = () => {
      if (document.visibilityState !== 'visible' || nextRotateAtMs === null) return;
      if (Date.now() >= nextRotateAtMs) {
        window.clearTimeout(timer);
        void load();
      }
    };

    void load();
    document.addEventListener('visibilitychange', onVisibilityChange);
    return () => {
      active = false;
      window.clearTimeout(timer);
      document.removeEventListener('visibilitychange', onVisibilityChange);
    };
  }, [limit, viewerKey, reloadKey]);

  return rotation;
}
