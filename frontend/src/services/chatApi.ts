import { http } from './http';
import type { RequestResponse } from './requestApi';

// Vòng đời đề nghị đưa món vào đổi trong chat (docs/api/chat.md, mục "Đề nghị đưa món vào đổi").
export type OfferChangeStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED';

export interface MessageResponse {
  id: number;
  requestId: number;
  senderId: number;
  kind: string;
  content: string | null;
  mediaId: number | null;
  refItemId: number | null;
  // Thẻ món backend ghép sẵn cho tin ITEM_CARD và OFFER_CHANGE.
  itemCard: PinnedItemResponse | null;
  // Chỉ có ở tin OFFER_CHANGE; các loại tin khác là null.
  offerStatus: OfferChangeStatus | null;
  moderation: string;
  blockedReason: string | null;
  createdAt: string;
}

export interface SendMessageRequest {
  content?: string | null;
  mediaId?: number | null;
  refItemId?: number | null;
}

export interface ProposeOfferChangeBody {
  offeredItemId: number;
  content?: string;
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

  // Người nhận đề nghị đưa một món của mình vào đổi; đề nghị PENDING cũ (nếu có) bị thay.
  async proposeOfferChange(requestId: number, offeredItemId: number, content?: string) {
    const body: ProposeOfferChangeBody = content ? { offeredItemId, content } : { offeredItemId };
    const { data } = await http.post<MessageResponse>(
      `/api/v1/requests/${requestId}/offer-changes`,
      body,
    );
    return data;
  },

  // Chủ bài đồng ý: backend trừ phí đăng Trao đổi và trả RequestResponse mới (type SWAP).
  async acceptOfferChange(requestId: number, messageId: number) {
    const { data } = await http.post<RequestResponse>(
      `/api/v1/requests/${requestId}/offer-changes/${messageId}/accept`,
    );
    return data;
  },

  // Chủ bài từ chối: không tốn phí, trả lại tin đề nghị với offerStatus DECLINED.
  async declineOfferChange(requestId: number, messageId: number) {
    const { data } = await http.post<MessageResponse>(
      `/api/v1/requests/${requestId}/offer-changes/${messageId}/decline`,
    );
    return data;
  },
};
