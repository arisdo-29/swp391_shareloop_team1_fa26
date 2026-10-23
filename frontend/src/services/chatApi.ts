import { http } from './http';

export interface MessageResponse {
  id: number;
  requestId: number;
  senderId: number;
  kind: string;
  content: string | null;
  mediaId: number | null;
  refItemId: number | null;
  itemCard: PinnedItemResponse | null;
  offerStatus: string | null;
  moderation: string;
  blockedReason: string | null;
  createdAt: string;
}

export interface SendMessageRequest {
  content?: string | null;
  mediaId?: number | null;
  refItemId?: number | null;
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
  async list(requestId: number, afterId?: number) {
    const { data } = await http.get<MessageResponse[]>(`/api/v1/requests/${requestId}/messages`, {
      params: { afterId },
    });
    return data;
  },

  async send(requestId: number, input: SendMessageRequest) {
    const { data } = await http.post<MessageResponse>(
      `/api/v1/requests/${requestId}/messages`,
      input,
    );
    return data;
  },

  async pinnedItems(requestId: number) {
    const { data } = await http.get<PinnedItemResponse[]>(
      `/api/v1/requests/${requestId}/pinned-items`,
    );
    return data;
  },
};
