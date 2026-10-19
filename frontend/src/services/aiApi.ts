import { http } from './http';
import type { ApiCondition, ItemResponse, OfferType, PageResponse } from './itemApi';

export const aiApiEndpoints = {
  search: '/api/v1/ai/search',
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
}

export const aiApi = {
  search: async (query: string, page = 0, size = 20): Promise<AiSearchResponse> => {
    const response = await http.post<AiSearchResponse>(aiApiEndpoints.search, { query }, { params: { page, size } });
    return response.data;
  },
};
