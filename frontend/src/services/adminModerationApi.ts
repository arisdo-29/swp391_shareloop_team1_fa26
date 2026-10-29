import { http } from './http';
import type { ApiCondition, ItemStatus, OfferType, OperatingStatus } from './itemApi';

export interface ReviewQueueItem {
  id: number;
  donorId: number;
  donorName?: string | null;
  title: string;
  thumbnailUrl?: string | null;
  aiRisk?: string | null;
  moderationFlag?: string | null;
  flagReason?: string | null;
  flagReasonCode?: string | null;
  matchedTerms?: string[];
  donorTrustStars?: number | null;
  createdAt: string;
  flagged?: boolean;
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

// Contract: docs/api/review.md mục 2 (GET /api/v1/admin/reviews/{itemId})
export interface ReviewItemAttribute { attributeId: number; name: string | null; value: string }
export interface ReviewItemDetail {
  id: number;
  title: string;
  description: string | null;
  desiredItem: string | null;
  offerType: OfferType;
  condition: ApiCondition;
  defectNote: string | null;
  brand: string | null;
  status: ItemStatus;
  isActive: boolean;
  operatingStatus: OperatingStatus | null;
  images: string[];
  defectImages: string[];
  categoryId: number;
  categoryName: string | null;
  areaId: number;
  areaName: string | null;
  donorId: number;
  donorName: string | null;
  donorTrustStars: number | null;
  attributes: ReviewItemAttribute[];
  aiRisk: string | null;
  moderationFlag: string | null;
  flagReasonCode: string | null;
  flagReason: string | null;
  matchedTerms: string[];
  authStatus: string | null;
  checklist: ReviewChecklistItem[];
  createdAt: string;
  updatedAt: string;
}

export interface ReviewChecklistItem { id: number; key: string; statement: string; description?: string | null }
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
  async detail(itemId: number) {
    const { data } = await http.get<ReviewItemDetail>(`/api/v1/admin/reviews/${itemId}`);
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
