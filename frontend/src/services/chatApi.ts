import { http } from './http';

export interface ItemCardResponse {
  id: number;
  title: string;
  offerType: 'GIVE' | 'SWAP';
  status: string;
  imageUrl?: string | null;
  donorId: number;
}

export interface MessageResponse {
  id: number;
  requestId: number;
  senderId: number;
  kind: string;
  content: string;
  mediaId?: number | null;
  refItemId?: number | null;
  itemCard?: ItemCardResponse | null;
  offerStatus?: string | null;
  moderation?: string | null;
  blockedReason?: string | null;
  createdAt: string;
}

export interface SendMessageRequest {
  content?: string;
  mediaId?: number | null;
  refItemId?: number | null;
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

  async getPinnedItems(requestId: number, currentUserId: number) {
    const { data } = await http.get<ItemCardResponse[]>(
      `/api/v1/requests/${requestId}/pinned-items`,
      { params: { currentUserId } },
    );
    return data;
  },

  async sendItemCard(requestId: number, currentUserId: number, refItemId: number, content?: string) {
    return this.send(requestId, currentUserId, { refItemId, content: content ?? '' });
  },
};
