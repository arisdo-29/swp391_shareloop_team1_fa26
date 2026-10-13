import { http } from './http';

// Theo docs/api/item.md
export type OfferType = 'GIVE' | 'SWAP';
export type ApiCondition = 'NEW' | 'LIKE_NEW' | 'GOOD' | 'DEFECTIVE';

export interface CreateItemRequest {
  categoryId: number;
  areaId: number;
  offerType: OfferType;
  title: string;
  description: string;
  desiredItem: string | null;
  condition: ApiCondition;
  defectNote: string | null;
  brand: string | null;
  images?: string[];
}

export interface ItemResponse extends CreateItemRequest {
  id: number;
  donorId: number;
  images: string[];
  status: 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED' | 'RESERVED' | 'TRADED' | 'EXPIRED';
  createdAt: string;
  updatedAt: string;
}

export interface ItemListParams {
  categoryId?: number;
  areaId?: number;
  page?: number;
  size?: number;
}

export interface ItemPageResponse {
  items: ItemResponse[];
  page: number;
  size: number;
  totalElements: number;
}

export const itemApi = {
  async list(params: ItemListParams = {}) {
    const { data } = await http.get<ItemPageResponse>('/api/v1/items', { params });
    return data;
  },
  async create(input: CreateItemRequest) {
    const { data } = await http.post<ItemResponse>('/api/v1/items', input);
    return data;
  },
  async detail(id: number) {
    const { data } = await http.get<ItemResponse>(`/api/v1/items/${id}`);
    return data;
  },
  async mine() {
    const { data } = await http.get<ItemResponse[]>('/api/v1/me/items');
    return data;
  },
};
