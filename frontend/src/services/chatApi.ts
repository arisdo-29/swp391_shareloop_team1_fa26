import { http } from './http';

export interface MessageResponse {
  id: number;
  requestId: number;
  senderId: number;
  kind: string;
  content: string;
  mediaId?: number;
  refItemId?: number;
  offerStatus?: string;
  moderation: string;
  blockedReason?: string;
  createdAt: string;
}

export interface SendMessageBody {
  content: string;
}

export const chatApi = {
  async getMessages(requestId: number, currentUserId: number, afterId?: number): Promise<MessageResponse[]> {
    const params: { currentUserId: number; afterId?: number } = { currentUserId };
    if (afterId !== undefined && afterId > 0) {
      params.afterId = afterId;
    }
    const { data } = await http.get<MessageResponse[]>(`/api/v1/requests/${requestId}/messages`, {
      params,
    });
    return data;
  },

  async sendMessage(requestId: number, currentUserId: number, content: string): Promise<MessageResponse> {
    const body: SendMessageBody = { content };
    const { data } = await http.post<MessageResponse>(`/api/v1/requests/${requestId}/messages`, body, {
      params: { currentUserId },
    });
    return data;
  },
};
