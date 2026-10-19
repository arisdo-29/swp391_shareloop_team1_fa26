import { http } from './http';

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

export const notificationApi = {
  async list(params: NotificationListParams = {}) {
    const { data } = await http.get<NotificationResponse[]>('/api/v1/notifications', { params });
    return data;
  },
  async unreadCount() {
    const { data } = await http.get<UnreadCountResponse>('/api/v1/notifications/unread-count');
    return data;
  },
  async markRead(id: number) {
    const { data } = await http.post<NotificationResponse>(`/api/v1/notifications/${id}/read`);
    return data;
  },
};
