import { http } from './http';
import type { ItemResponse, PageResponse } from './itemApi';

export const aiApiEndpoints = {
  search: '/api/v1/ai/search',
  quota: '/api/v1/ai/quota',
} as const;

export interface AiSearchResponse {
  message: string;
  results: PageResponse<ItemResponse>;
  quota: AiQuotaResponse;
  chargedCredits: number;
  fallback: boolean;
  outOfScope: boolean;
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
  search: async (query: string): Promise<AiSearchResponse> => {
    const response = await http.post<AiSearchResponse>(aiApiEndpoints.search, { query });
    return response.data;
  },
};
