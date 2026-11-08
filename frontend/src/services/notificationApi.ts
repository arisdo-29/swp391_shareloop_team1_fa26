import { http, tokenStorage } from './http';

export interface NotificationResponse {
  id: number;
  type: string;
  title: string;
  body: string;
  link: string | null;
  isRead: boolean;
  createdAt: string;
}

export interface UnreadCountResponse {
  unreadCount: number;
}

export interface NotificationListParams {
  page?: number;
  size?: number;
}

export type NotificationCategory = 'moderation' | 'transaction' | 'complaint' | 'credit' | 'other';

export const notificationCategoryLabels: Record<Exclude<NotificationCategory, 'other'>, string> = {
  moderation: 'Duyệt bài',
  transaction: 'Giao dịch & Tin nhắn',
  complaint: 'Khiếu nại & Giải trình',
  credit: 'Credit & Uy tín',
};

// Backend trả tối đa 100 dòng mỗi trang (docs/api/notification.md).
const MAX_PAGE_SIZE = 100;
// Chặn trên để trang "Tất cả thông báo" không gọi API vô hạn khi tài khoản có quá nhiều thông báo.
const MAX_PAGES_TO_LOAD = 10;
const HIDDEN_KEY_PREFIX = 'shareloop:hiddenNotifications:';

/**
 * Xếp loại thông báo vào nhóm bộ lọc theo tiền tố `type` của backend.
 * Loại mới chưa biết rơi vào 'other' và chỉ hiện ở bộ lọc "Tất cả".
 */
export function notificationCategory(type: string): NotificationCategory {
  if (type.startsWith('POST_') || type.startsWith('ITEM_')) return 'moderation';
  if (type.startsWith('DISPUTE_') || type.startsWith('REPORT_')) return 'complaint';
  if (type.startsWith('TRUST_') || type.startsWith('BOOST_') || type.includes('CREDIT'))
    return 'credit';
  if (
    type.startsWith('REQUEST_') ||
    type.startsWith('TRANSACTION_') ||
    type.startsWith('CHAT_') ||
    // Đề nghị đổi món trong chat (docs/api/chat.md): backend đặt tên OFFER_CHANGE_*, không có tiền tố REQUEST_.
    type.startsWith('OFFER_CHANGE_') ||
    [
      'NEW_REQUEST',
      'SELECTED',
      'LOGISTICS_CONFIRMED',
      'SCHEDULE_UPDATED',
      'HANDOVER_CONFIRMED',
    ].includes(type)
  ) {
    return 'transaction';
  }
  return 'other';
}

/*
 * Backend chưa có API xoá thông báo, nên "xoá" chỉ ẩn ở trình duyệt này: lưu id đã ẩn vào
 * localStorage theo từng tài khoản. Thông báo vẫn còn trên server.
 */
function hiddenStorageKey() {
  const userId = tokenStorage.getUserId();
  return userId ? `${HIDDEN_KEY_PREFIX}${userId}` : null;
}

export function readHiddenNotificationIds(): Set<number> {
  const key = hiddenStorageKey();
  if (!key) return new Set();
  try {
    const parsed: unknown = JSON.parse(localStorage.getItem(key) ?? '[]');
    return new Set(
      Array.isArray(parsed) ? parsed.filter((id): id is number => Number.isSafeInteger(id)) : [],
    );
  } catch {
    // Dữ liệu hỏng hoặc trình duyệt chặn storage: coi như chưa ẩn gì.
    return new Set();
  }
}

function hideNotificationIds(ids: number[]) {
  const key = hiddenStorageKey();
  if (!key || !ids.length) return;
  const hidden = readHiddenNotificationIds();
  for (const id of ids) hidden.add(id);
  try {
    localStorage.setItem(key, JSON.stringify([...hidden]));
  } catch {
    // Storage đầy hoặc bị chặn: bỏ qua, lần tải sau thông báo sẽ hiện lại.
  }
}

export function withoutHidden(notifications: NotificationResponse[]) {
  const hidden = readHiddenNotificationIds();
  return notifications.filter((notification) => !hidden.has(notification.id));
}

export const notificationApi = {
  async list(params: NotificationListParams = {}) {
    const { data } = await http.get<NotificationResponse[]>('/api/v1/notifications', { params });
    return data;
  },
  /** Tải lần lượt từng trang cho tới khi hết (tối đa MAX_PAGES_TO_LOAD trang), bỏ các thông báo đã ẩn. */
  async listAll() {
    const all: NotificationResponse[] = [];
    for (let page = 0; page < MAX_PAGES_TO_LOAD; page += 1) {
      const batch = await notificationApi.list({ page, size: MAX_PAGE_SIZE });
      all.push(...batch);
      if (batch.length < MAX_PAGE_SIZE) break;
    }
    return withoutHidden(all);
  },
  async unreadCount() {
    const { data } = await http.get<UnreadCountResponse>('/api/v1/notifications/unread-count');
    return data;
  },
  async markRead(id: number) {
    const { data } = await http.post<NotificationResponse>(`/api/v1/notifications/${id}/read`);
    return data;
  },
  /**
   * Đánh dấu đã đọc nhiều thông báo. Backend chưa có API "đọc tất cả" nên gọi từng cái một;
   * trả về danh sách đã cập nhật thành công.
   */
  async markManyRead(notifications: NotificationResponse[]) {
    const updated: NotificationResponse[] = [];
    for (const notification of notifications) {
      if (notification.isRead) continue;
      updated.push(await notificationApi.markRead(notification.id));
    }
    return updated;
  },
  /** Đánh dấu đã đọc mọi thông báo chưa đọc của tôi. */
  async markAllRead() {
    const all = await notificationApi.listAll();
    return notificationApi.markManyRead(all);
  },
  /** Ẩn một thông báo. */
  async deleteNotification(id: number) {
    await notificationApi.deleteMultiple([id]);
  },
  /**
   * Ẩn nhiều thông báo. Đánh dấu đã đọc trước để số trên chuông không đếm thông báo đã ẩn;
   * lỗi giữa chừng thì chưa ẩn cái nào.
   */
  async deleteMultiple(ids: number[]) {
    for (const id of ids) await notificationApi.markRead(id);
    hideNotificationIds(ids);
  },
  /** Ẩn mọi thông báo đã đọc của tôi; trả về số thông báo đã ẩn. */
  async clearRead() {
    const all = await notificationApi.listAll();
    const readIds = all
      .filter((notification) => notification.isRead)
      .map((notification) => notification.id);
    hideNotificationIds(readIds);
    return readIds.length;
  },
};
