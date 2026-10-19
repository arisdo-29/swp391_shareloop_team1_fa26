import { http } from './http';
import type { ApiCondition, ItemResponse, OfferType, PageResponse } from './itemApi';

export const aiApiEndpoints = {
  search: '/api/v1/ai/search',
  quota: '/api/v1/ai/quota',
} as const;

export interface AiSearchFilters {
  keywords: string | null;
  categoryId: number | null;
  areaId: number | null;
  condition: ApiCondition | null;
  offerType: OfferType | null;
}

export interface AiSearchResponse {
  filters: AiSearchFilters;
  results: PageResponse<ItemResponse>;
  quota: AiQuotaResponse;
  chargedCredits: number;
  fallback: boolean;
}

export interface AiQuotaResponse {
  freeUsesRemaining: number;
  feeCredits: number;
  hasToppedUp: boolean;
}

export const aiApi = {
  async quota() {
    const { data } = await http.get<AiQuotaResponse>(aiApiEndpoints.quota);
    return data;
  },
  search: async (query: string, page = 0, size = 20): Promise<AiSearchResponse> => {
    const response = await http.post<AiSearchResponse>(aiApiEndpoints.search, { query }, { params: { page, size } });
    return response.data;
  },
};
