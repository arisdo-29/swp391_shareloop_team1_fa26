import { http } from './http';
import type { ItemResponse, PageResponse } from './itemApi';

export const aiApiEndpoints = {
  search: '/api/v1/ai/search',
  quota: '/api/v1/ai/quota',
  describeItem: '/api/v1/ai/describe-item',
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

export interface AiDescriptionResponse {
  description: string;
  attributes: Array<{ name: string; value: string; requiresUserInput: boolean; prompt?: string | null }>;
  remainingUses: number;
  message: string;
}

export const aiApi = {
  async describeItem(title: string): Promise<AiDescriptionResponse> {
    const { data } = await http.post<AiDescriptionResponse>(aiApiEndpoints.describeItem, { title });
    return data;
  },
  async quota() {
    const { data } = await http.get<AiQuotaResponse>(aiApiEndpoints.quota);
    return data;
  },
  search: async (query: string): Promise<AiSearchResponse> => {
    const response = await http.post<AiSearchResponse>(aiApiEndpoints.search, { query });
    return response.data;
  },
};
