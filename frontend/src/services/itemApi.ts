import { http, type ApiError } from './http';

export const itemApiEndpoints = {
  list: '/api/v1/items',
  detail: (id: number) => `/api/v1/items/${id}`,
  create: '/api/v1/items',
  update: (id: number) => `/api/v1/items/${id}`,
  myItems: '/api/v1/me/items',
  editQuote: (id: number) => `/api/v1/items/${id}/edit-quote`,
  renew: (id: number) => `/api/v1/items/${id}/renew`,
} as const;

export interface ItemListParams {
  q?: string;
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
export type OperatingStatus = 'NORMAL' | 'PARTIAL' | 'NOT_WORKING';
export type ItemStatus = 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED' | 'RESERVED' | 'TRADED' | 'EXPIRED';

export function itemErrorMessage(error: unknown, fallback: string): string {
  const apiError = error as Partial<ApiError>;
  return typeof apiError.message === 'string' && apiError.message.trim() ? apiError.message : fallback;
}

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
  images: string[];
  defectImages: string[] | null;
  operatingStatus: OperatingStatus | null;
}

export type UpdateItemRequest = Omit<CreateItemRequest, 'images' | 'defectImages'> & {
  images?: string[];
  defectImages?: string[] | null;
};

export interface ItemResponse extends CreateItemRequest {
  id: number;
  donorId: number;
  images: string[];
  defectImages: string[];
  operatingStatus: OperatingStatus | null;
  status: ItemStatus;
  createdAt: string;
  updatedAt: string;
}

// Contract: docs/api/listingfee.md (GET /api/v1/items/{id}/edit-quote)
export type EditQuoteReason =
  | 'NOT_YET_APPROVED'
  | 'REPOST_FEE'
  | 'PENDING_REVIEW_OVERWRITE'
  | 'FIRST_FREE_EDIT'
  | 'PAID_EDIT';

export interface EditQuote {
  free: boolean; // true khi amount = 0
  amount: number; // số Credit sẽ giữ khi gửi bản sửa
  reason: EditQuoteReason;
}

// Contract: docs/api/listingfee.md (POST /api/v1/items/{id}/renew)
export interface RenewResponse {
  itemId: number;
  fee: number; // số Credit vừa trừ; 0 khi Admin cấu hình miễn phí
  expireAt: string; // hạn hiển thị mới, ISO-8601 UTC
}

export function itemListUrl(params: ItemListParams = {}): string {
  const query = new URLSearchParams();
  if (params.q !== undefined) query.set('q', params.q);
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
  async update(id: number, input: UpdateItemRequest): Promise<ItemResponse> {
    const { data } = await http.put<ItemResponse>(itemApiEndpoints.update(id), input);
    return data;
  },
  async detail(id: number): Promise<ItemResponse> {
    const { data } = await http.get<ItemResponse>(itemApiEndpoints.detail(id));
    return data;
  },
  async remove(id: number): Promise<void> {
    await http.delete(itemApiEndpoints.detail(id));
  },
  async mine(): Promise<ItemResponse[]> {
    const { data } = await http.get<ItemResponse[]>(itemApiEndpoints.myItems);
    return data;
  },
  async getEditQuote(id: number): Promise<EditQuote> {
    const { data } = await http.get<EditQuote>(itemApiEndpoints.editQuote(id));
    return data;
  },
  async renewItem(id: number): Promise<RenewResponse> {
    const { data } = await http.post<RenewResponse>(itemApiEndpoints.renew(id));
    return data;
  },
};
