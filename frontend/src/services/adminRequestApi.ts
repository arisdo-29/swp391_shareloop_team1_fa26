import { http } from './http';
import type { RequestResponse } from './requestApi';
import type { ItemRequestStatus } from '../types/domain';

export type DeliveryMethod = 'IN_PERSON' | 'SHIPPING';

// Contract: docs/api/admin.md mục "Giao dịch (Admin)"
export interface AdminRequestRow {
  id: number;
  itemId: number;
  itemTitle: string | null;
  type: 'GIVE' | 'SWAP';
  status: ItemRequestStatus;
  donorId: number | null;
  donorName: string | null;
  receiverId: number;
  receiverName: string | null;
  meetingTime: string | null;
  createdAt: string;
  // Lý do gắn cờ "Bị kẹt"; null khi giao dịch không kẹt.
  flagReason: string | null;
  // Hình thức giao nhận; null khi chưa chọn.
  deliveryMethod: DeliveryMethod | null;
}

export interface AdminRequestPage {
  items: AdminRequestRow[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AdminRequestListParams {
  status?: ItemRequestStatus;
  stuck?: boolean;
  q?: string;
  type?: 'GIVE' | 'SWAP';
  deliveryMethod?: DeliveryMethod;
  from?: string;
  to?: string;
  page: number;
  size: number;
}

// Contract: docs/api/admin.md mục "Chi tiết giao dịch (Admin)"
export interface AdminRequestParty {
  userId: number | null;
  name: string | null;
  email: string | null;
  trustStars: number | null;
  rank: string | null;
  locked: boolean;
}

export interface AdminRequestItemInfo {
  itemId: number;
  title: string | null;
  imageUrl: string | null;
  status: string | null;
  offerType: 'GIVE' | 'SWAP' | null;
}

export interface AdminTimelineEvent {
  label: string;
  at: string;
  note: string | null;
}

export interface AdminRequestPhoto {
  mediaId: number;
  url: string;
  stage: string;
  uploaderName: string | null;
  createdAt: string;
}

export interface AdminRequestReportRow {
  id: number;
  reporterName: string | null;
  targetType: 'ITEM' | 'USER' | 'REQUEST';
  reason: string;
  stage: 'BEFORE' | 'AFTER';
  status: 'OPEN' | 'AWAITING_RESPONSE' | 'RESOLVED' | 'DISMISSED';
  createdAt: string;
}

export interface AdminRequestPoints {
  userName: string | null;
  role: string | null;
  points: number;
}

export interface AdminRequestDetail {
  id: number;
  type: 'GIVE' | 'SWAP';
  status: ItemRequestStatus;
  donor: AdminRequestParty | null;
  receiver: AdminRequestParty;
  item: AdminRequestItemInfo;
  offeredItem: AdminRequestItemInfo | null;
  deliveryMethod: DeliveryMethod | null;
  meetingPlace: string | null;
  meetingTime: string | null;
  timeline: AdminTimelineEvent[];
  photos: AdminRequestPhoto[];
  reports: AdminRequestReportRow[];
  points: AdminRequestPoints[];
  messagesViewable: boolean;
  cancellable: boolean;
  createdAt: string;
}

export interface AdminChatMessage {
  id: number;
  senderName: string | null;
  senderRole: string;
  kind: string;
  content: string | null;
  mediaUrl: string | null;
  itemTitle: string | null;
  createdAt: string;
}

export const adminRequestApi = {
  async detail(id: number) {
    const { data } = await http.get<AdminRequestDetail>(`/api/v1/admin/requests/${id}`);
    return data;
  },
  // Chỉ trả khi giao dịch có báo cáo hoặc khiếu nại; mỗi lần gọi được ghi nhật ký.
  async messages(id: number) {
    const { data } = await http.get<{ items: AdminChatMessage[] }>(`/api/v1/admin/requests/${id}/messages`);
    return data.items;
  },
  async list(params: AdminRequestListParams) {
    const { data } = await http.get<AdminRequestPage>('/api/v1/admin/requests', { params });
    return data;
  },
  async cancel(id: number, reason: string) {
    const { data } = await http.post<RequestResponse>(`/api/v1/admin/requests/${id}/cancel`, { reason });
    return data;
  },
};
