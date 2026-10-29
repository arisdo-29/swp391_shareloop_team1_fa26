import { http, type ApiError } from './http';

export const itemApiEndpoints = {
  list: '/api/v1/items',
  detail: (id: number) => `/api/v1/items/${id}`,
  create: '/api/v1/items',
  update: (id: number) => `/api/v1/items/${id}`,
  myItems: '/api/v1/me/items',
  editQuote: (id: number) => `/api/v1/items/${id}/edit-quote`,
  renew: (id: number) => `/api/v1/items/${id}/renew`,
  boost: (id: number) => `/api/v1/items/${id}/boost`,
  boostQuote: (id: number) => `/api/v1/items/${id}/boost-quote`,
  renewQuote: (id: number) => `/api/v1/items/${id}/renew-quote`,
  listingFees: '/api/v1/listing-fees',
  postingQuota: '/api/v1/me/posting-quota',
} as const;

export interface ItemListParams {
  q?: string;
  categoryId?: number;
  areaId?: number;
  condition?: ApiCondition;
  offerType?: OfferType;
  page?: number;
  size?: number;
  boosted?: boolean;
  sort?: 'newest' | 'expiring';
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages?: number;
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
  expireAt: string | null;
  boostedUntil: string | null;
  donorName?: string | null;
  donorTrustStars?: number | null;
  donorRank?: string | null;
  donorAsDescribedRate?: number | null;
  donorLocked?: boolean | null;
  // Chỉ có khi chủ bài hoặc Admin xem; người khác nhận null (U04).
  hidden?: boolean | null;
  hiddenReason?: string | null;
}

// Contract: docs/api/listingfee.md (GET /api/v1/items/{id}/edit-quote)
export type EditQuoteReason =
  | 'NOT_YET_APPROVED'
  | 'REPOST_FEE'
  | 'PENDING_REVIEW_OVERWRITE'
  | 'FIRST_FREE_EDIT'
  | 'PAID_EDIT'
  | 'OFFER_TYPE_CHANGE_FREE'
  | 'OFFER_TYPE_CHANGE_POST_FEE';

export interface EditQuote {
  free: boolean; // true khi amount = 0
  amount: number; // số Credit sẽ giữ khi gửi bản sửa
  reason: string;
}

// Contract: docs/api/listingfee.md (POST /api/v1/items/{id}/renew)
export interface RenewResponse {
  itemId: number;
  fee: number; // số Credit vừa trừ; 0 khi Admin cấu hình miễn phí
  expireAt: string; // hạn hiển thị mới, ISO-8601 UTC
}

// Contract: docs/api/listingfee.md (GET /api/v1/items/{id}/boost-quote)
export interface BoostQuote {
  fee: number;
  newBoostedUntil: string; // mốc hết đẩy mới nếu đẩy ngay, ISO-8601 UTC
  allowed: boolean;
  reason: string | null; // lý do không đẩy được, tiếng Việt; null khi allowed
}

// Contract: docs/api/listingfee.md (GET /api/v1/items/{id}/renew-quote)
export interface RenewQuote {
  fee: number; // 0 là miễn phí
  newExpireAt: string; // hạn mới nếu gia hạn ngay, ISO-8601 UTC
  allowed: boolean;
  reason: string | null;
}

export interface BoostResponse {
  itemId: number;
  boostedUntil: string;
  creditBalance: number;
}

export interface ListingFeeConfigResponse {
  postFeeSwap: number;
  postFeeGive: number;
  editFee: number;
  renewFee: number;
  renewExpiredWindowDays: number;
  boostFee: number;
  boostDays: number;
  boostMaxDays: number;
  postDisplayDays: number;
  postDisplayDaysGold: number;
}

export function itemListUrl(params: ItemListParams = {}): string {
  const query = new URLSearchParams();
  if (params.q !== undefined) query.set('q', params.q);
  if (params.categoryId !== undefined) query.set('categoryId', String(params.categoryId));
  if (params.areaId !== undefined) query.set('areaId', String(params.areaId));
  if (params.condition !== undefined) query.set('condition', params.condition);
  if (params.offerType !== undefined) query.set('offerType', params.offerType);
  if (params.boosted !== undefined) query.set('boosted', String(params.boosted));
  if (params.sort !== undefined) query.set('sort', params.sort);
  if (params.page !== undefined) query.set('page', String(params.page));
  if (params.size !== undefined) query.set('size', String(params.size));
  return query.size ? `${itemApiEndpoints.list}?${query}` : itemApiEndpoints.list;
}

export async function listItems(params: ItemListParams = {}): Promise<PageResponse<ItemResponse>> {
  return itemApi.list(params);
}

// Hạn mức bài đăng (docs/api/item.md mục 3b); limit = -1 là không giới hạn.
export interface PostingQuotaEntry {
  active: number;
  limit: number;
}

export interface PostingQuota {
  give: PostingQuotaEntry;
  swap: PostingQuotaEntry;
  rank: 'BRONZE' | 'SILVER' | 'GOLD' | 'DIAMOND';
  nextPostFee: number;
}

export const itemApi = {
  async getListingFees(): Promise<ListingFeeConfigResponse> {
    const { data } = await http.get<ListingFeeConfigResponse>(itemApiEndpoints.listingFees);
    return data;
  },
  async list(params: ItemListParams = {}): Promise<PageResponse<ItemResponse>> {
    const { data } = await http.get<PageResponse<ItemResponse>>(itemApiEndpoints.list, { params });
    return data;
  },
  async postingQuota(): Promise<PostingQuota> {
    const { data } = await http.get<PostingQuota>(itemApiEndpoints.postingQuota);
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
    await http.delete<void>(itemApiEndpoints.detail(id));
  },
  async mine(): Promise<ItemResponse[]> {
    const { data } = await http.get<ItemResponse[]>(itemApiEndpoints.myItems);
    return data;
  },
  async getEditQuote(id: number, offerType?: OfferType): Promise<EditQuote> {
    const { data } = await http.get<EditQuote>(itemApiEndpoints.editQuote(id), { params: offerType ? { offerType } : undefined });
    return data;
  },
  async getBoostQuote(id: number): Promise<BoostQuote> {
    const { data } = await http.get<BoostQuote>(itemApiEndpoints.boostQuote(id));
    return data;
  },
  async getRenewQuote(id: number): Promise<RenewQuote> {
    const { data } = await http.get<RenewQuote>(itemApiEndpoints.renewQuote(id));
    return data;
  },
  async renewItem(id: number): Promise<RenewResponse> {
    const { data } = await http.post<RenewResponse>(itemApiEndpoints.renew(id));
    return data;
  },
  async boostItem(id: number): Promise<BoostResponse> {
    const { data } = await http.post<BoostResponse>(itemApiEndpoints.boost(id));
    return data;
  },
};
