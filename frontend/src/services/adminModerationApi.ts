import { http } from './http';

export interface ReviewQueueItem {
  id: number;
  donorId: number;
  title: string;
  images?: string[] | null;
  defectImages?: string[] | null;
  aiRisk?: string | null;
  moderationFlag?: string | null;
  flagReason?: string | null;
  flagReasonCode?: string | null;
  matchedTerms?: string[];
  donorTrustStars?: number | null;
  createdAt: string;
}

export interface PendingItemsParams {
  page?: number;
  size?: number;
}

export interface ReviewQueueResponse {
  items: ReviewQueueItem[];
  checklist: ReviewChecklistItem[];
  page: number;
  size: number;
}

export interface ReviewChecklistItem { id: number; key: string; statement: string }
export interface ApproveReviewRequest { answers: { id: number; passed: boolean }[] }
export interface RejectReviewRequest { reason: string }

export const adminModerationApi = {
  async pendingItems(params: PendingItemsParams = {}) {
    const { data } = await http.get<ReviewQueueItem[]>('/api/v1/admin/items/pending', { params });
    return data;
  },
  async pendingReviews(params: PendingItemsParams = {}) {
    const { data } = await http.get<ReviewQueueResponse>('/api/v1/admin/reviews/pending', { params });
    return data;
  },
  async rejectReasons() {
    const { data } = await http.get<string[]>('/api/v1/admin/reviews/reject-reasons');
    return data;
  },
  async approve(itemId: number, input: ApproveReviewRequest) {
    await http.post(`/api/v1/admin/reviews/${itemId}/approve`, input);
  },
  async reject(itemId: number, input: RejectReviewRequest) {
    await http.post(`/api/v1/admin/reviews/${itemId}/reject`, input);
  },
};
