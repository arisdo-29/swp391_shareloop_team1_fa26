import { http } from './http';

export interface MessageResponse {
  id: number;
  requestId: number;
  senderId: number;
  kind: string;
  content: string;
  mediaId?: number | null;
  refItemId?: number | null;
  offerStatus?: string | null;
  moderation?: string | null;
  blockedReason?: string | null;
  createdAt: string;
}

export interface SendMessageRequest {
  content: string;
}

export interface PinnedItemResponse {
  id: number;
  title: string;
  offerType: 'GIVE' | 'SWAP';
  status: 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED' | 'RESERVED' | 'TRADED' | 'EXPIRED';
  imageUrl: string | null;
  donorId: number;
}

export const chatApi = {
  async list(requestId: number, currentUserId: number, afterId?: number) {
    const { data } = await http.get<MessageResponse[]>(`/api/v1/requests/${requestId}/messages`, {
      params: { currentUserId, afterId },
    });
    return data;
  },

  async send(requestId: number, currentUserId: number, input: SendMessageRequest) {
    const { data } = await http.post<MessageResponse>(
      `/api/v1/requests/${requestId}/messages`,
      input,
      { params: { currentUserId } },
    );
    return data;
  },

  async pinnedItems(requestId: number, currentUserId: number) {
    const { data } = await http.get<PinnedItemResponse[]>(
      `/api/v1/requests/${requestId}/messages/pinned-items`,
      { params: { currentUserId } },
    );
    return data;
  },
};
