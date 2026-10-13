import { http } from './http';

export const itemApiEndpoints = {
  list: '/api/v1/items',
  detail: (id: number) => `/api/v1/items/${id}`,
  create: '/api/v1/items',
  myItems: '/api/v1/me/items',
} as const;

export interface ItemListParams {
  categoryId?: number;
  areaId?: number;
  page?: number;
  size?: number;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
}

export type OfferType = 'GIVE' | 'SWAP';
export type ApiCondition = 'NEW' | 'LIKE_NEW' | 'GOOD' | 'DEFECTIVE';
export type ItemStatus = 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED' | 'RESERVED' | 'TRADED' | 'EXPIRED';

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
  status: ItemStatus;
  createdAt: string;
  updatedAt: string;
}

export function itemListUrl(params: ItemListParams = {}): string {
  const query = new URLSearchParams();
  if (params.categoryId !== undefined) query.set('categoryId', String(params.categoryId));
  if (params.areaId !== undefined) query.set('areaId', String(params.areaId));
  if (params.page !== undefined) query.set('page', String(params.page));
  if (params.size !== undefined) query.set('size', String(params.size));
  return query.size ? `${itemApiEndpoints.list}?${query}` : itemApiEndpoints.list;
}

export async function listItems(params: ItemListParams = {}): Promise<PageResponse<ItemResponse>> {
  return itemApi.list(params);
}

export const itemApi = {
  async list(params: ItemListParams = {}): Promise<PageResponse<ItemResponse>> {
    const { data } = await http.get<PageResponse<ItemResponse>>(itemApiEndpoints.list, { params });
    return data;
  },
  async create(input: CreateItemRequest): Promise<ItemResponse> {
    const { data } = await http.post<ItemResponse>(itemApiEndpoints.create, input);
    return data;
  },
  async detail(id: number): Promise<ItemResponse> {
    const { data } = await http.get<ItemResponse>(itemApiEndpoints.detail(id));
    return data;
  },
  async mine(): Promise<ItemResponse[]> {
    const { data } = await http.get<ItemResponse[]>(itemApiEndpoints.myItems);
    return data;
  },
};
