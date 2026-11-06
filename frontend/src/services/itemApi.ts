import { http, type ApiError } from './http';

export const itemApiEndpoints = {
  list: '/api/v1/items',
  featured: '/api/v1/items/featured',
  detail: (id: number) => `/api/v1/items/${id}`,
  create: '/api/v1/items',
  update: (id: number) => `/api/v1/items/${id}`,
  myItems: '/api/v1/me/items',
  editQuote: (id: number) => `/api/v1/items/${id}/edit-quote`,
  renewQuote: (id: number) => `/api/v1/items/${id}/renew-quote`,
  boostQuote: (id: number) => `/api/v1/items/${id}/boost-quote`,
  renew: (id: number) => `/api/v1/items/${id}/renew`,
  boost: (id: number) => `/api/v1/items/${id}/boost`,
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
  // Lượt xoay bài đẩy backend trả ở trang trước; gửi lại khi chuyển trang trong cùng bộ lọc (docs/api/item.md mục 4).
  rotationSeed?: number;
  // Khoá xoay của khách chưa đăng nhập (getViewerKey); đã đăng nhập thì không gửi, backend dùng id người dùng.
  viewerKey?: number;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages?: number;
}

// GET /api/v1/items trả thêm lượt xoay bài đẩy đã dùng cho trang này.
export interface ItemListPage extends PageResponse<ItemResponse> {
  rotationSeed?: number | null;
}

// Contract: docs/api/item.md mục 4b (GET /api/v1/items/featured)
export interface FeaturedItemsParams {
  limit?: number;
  viewerKey?: number;
}

export interface FeaturedItemsResponse {
  items: ItemResponse[];
  rotatedAt: string; // lúc bắt đầu nhóm hiện tại, ISO-8601 UTC
  nextRotateAt: string; // lúc đổi sang nhóm kế tiếp; FE hẹn giờ tải lại tới mốc này
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
  // Thời điểm giao dịch hoàn tất; chỉ có khi bài TRADED (chi tiết và tab DONE của hồ sơ).
  completedAt?: string | null;
  // Bài còn nhận yêu cầu không, backend tính. false thì ẩn nút gửi yêu cầu. null ở các danh sách khác.
  requestable?: boolean | null;
  // Ba mốc đợt đẩy do backend tính sẵn; chỉ có ở "Bài của tôi" và chi tiết bài khi chủ bài xem, nơi khác null.
  boostStartedAt?: string | null;
  boostChainEndsAt?: string | null; // mốc đẩy muộn nhất của đợt (mua thêm được tới đây)
  boostCooldownUntil?: string | null; // mốc được mở đợt đẩy mới
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

export interface BoostResponse {
  itemId: number;
  boostedUntil: string;
  creditBalance: number;
}

export interface RenewQuote {
  fee: number;
  newExpireAt: string;
  allowed: boolean;
  reason: string | null;
}

// Mã lý do không đẩy được (docs/api/listingfee.md, bảng reasonCode của boost-quote). Mã khác thì hiện reason.
export type BoostReasonCode =
  | 'LISTINGFEE_BOOST_NOT_ALLOWED'
  | 'LISTINGFEE_CATEGORY_NOT_BOOSTABLE'
  | 'CATALOG_CATEGORY_NOT_FOUND'
  | 'LISTINGFEE_BOOST_COOLDOWN'
  | 'LISTINGFEE_BOOST_CHAIN_LIMIT'
  | 'LISTINGFEE_BOOST_EXCEEDS_EXPIRY'
  | 'LISTINGFEE_BOOST_USER_LIMIT'
  | 'LISTINGFEE_BOOST_SOLD_OUT';

// Contract: docs/api/listingfee.md (GET /api/v1/items/{id}/boost-quote). Mọi mốc do backend tính, FE chỉ định dạng.
export interface BoostQuote {
  fee: number;
  newBoostedUntil: string;
  allowed: boolean;
  reason: string | null;
  reasonCode: BoostReasonCode | string | null; // null khi allowed = true
  boostStartedAt: string; // đợt đang chạy, hoặc now nếu là đợt mới
  chainEndsAt: string; // mốc đẩy muộn nhất của đợt
  cooldownUntil: string | null; // mốc được đẩy lại (xem bảng cooldownUntil trong listingfee.md)
  extending: boolean; // bài đang được đẩy: mua thêm, không chiếm suất mới
  slotsLeft: number; // suất còn trống toàn hệ thống
  earliestSlotAt: string | null; // chỉ có khi slotsLeft = 0
  userActiveBoosts: number;
  userEarliestBoostEndAt: string | null; // chỉ có khi đã đủ số bài đang đẩy tối đa
  expireAt: string | null;
}

export interface ListingFeeConfigResponse {
  postFeeSwap: number;
  /** Phí đăng Trao đổi khi vượt hạn mức theo hạng. BE chưa trả field này ở GET /listing-fees nên để optional. */
  postFeeSwapOverQuota?: number;
  postFeeGive: number;
  editFee: number;
  renewFee: number;
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
  if (params.rotationSeed !== undefined) query.set('rotationSeed', String(params.rotationSeed));
  if (params.viewerKey !== undefined) query.set('viewerKey', String(params.viewerKey));
  return query.size ? `${itemApiEndpoints.list}?${query}` : itemApiEndpoints.list;
}

export async function listItems(params: ItemListParams = {}): Promise<ItemListPage> {
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
  async list(params: ItemListParams = {}): Promise<ItemListPage> {
    const { data } = await http.get<ItemListPage>(itemApiEndpoints.list, { params });
    return data;
  },
  // Banner trang chủ: một nhóm bài đẩy theo vòng xoay; backend quyết định nhóm và mốc đổi nhóm.
  async featured(params: FeaturedItemsParams = {}): Promise<FeaturedItemsResponse> {
    const { data } = await http.get<FeaturedItemsResponse>(itemApiEndpoints.featured, { params });
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
  async getRenewQuote(id: number): Promise<RenewQuote> {
    const { data } = await http.get<RenewQuote>(itemApiEndpoints.renewQuote(id));
    return data;
  },
  async getBoostQuote(id: number): Promise<BoostQuote> {
    const { data } = await http.get<BoostQuote>(itemApiEndpoints.boostQuote(id));
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
