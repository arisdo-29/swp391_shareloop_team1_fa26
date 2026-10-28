import { http } from './http';
import type { RequestResponse } from './requestApi';
import type { ItemRequestStatus } from '../types/domain';

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
  page: number;
  size: number;
}

export const adminRequestApi = {
  async list(params: AdminRequestListParams) {
    const { data } = await http.get<AdminRequestPage>('/api/v1/admin/requests', { params });
    return data;
  },
  async cancel(id: number, reason: string) {
    const { data } = await http.post<RequestResponse>(`/api/v1/admin/requests/${id}/cancel`, { reason });
    return data;
  },
};
