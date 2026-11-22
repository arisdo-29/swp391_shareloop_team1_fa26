import { http } from './http';
import type { ActivityLogResponse } from './adminAuditApi';
import type { ItemResponse, ItemStatus, OfferType, PageResponse } from './itemApi';
import type { WalletTransactionResponse } from './walletApi';

// Contract: docs/api/admin.md mục "Quản lý bài đăng"

/** Ai đã ẩn bài: ADMIN, OWNER_LOCKED (chủ bài bị khoá) hoặc OTHER (báo cáo hoặc hệ thống). */
export type HiddenSource = 'ADMIN' | 'OWNER_LOCKED' | 'OTHER';

export interface AdminItemRow {
  id: number;
  title: string;
  imageUrl: string | null;
  ownerId: number;
  ownerName: string | null;
  ownerEmail: string | null;
  categoryName: string | null;
  offerType: OfferType;
  status: ItemStatus;
  active: boolean;
  hiddenSource: HiddenSource | null;
  postFeePaid: number;
  pendingRequestCount: number;
  createdAt: string;
  approvedAt: string | null;
  expireAt: string | null;
  boostedUntil: string | null;
  boosting: boolean;
}

export interface AdminItemRequestRow {
  id: number;
  receiverName: string | null;
  type: 'GIVE' | 'SWAP';
  status: string;
  offeredItemTitle: string | null;
  running: boolean;
  createdAt: string;
}

export interface AdminItemReportRow {
  id: number;
  reason: string;
  status: 'OPEN' | 'AWAITING_RESPONSE' | 'RESOLVED' | 'DISMISSED';
  createdAt: string;
}

export interface AdminItemAttribute {
  attributeId: number;
  name: string | null;
  value: string;
}

export interface AdminItemFullDetail {
  item: { item: ItemResponse; active: boolean; attributes: AdminItemAttribute[] };
  summary: AdminItemRow;
  feeLines: WalletTransactionResponse[];
  requests: AdminItemRequestRow[];
  hasRunningTransaction: boolean;
  runningRequestId: number | null;
  reports: AdminItemReportRow[];
  hiddenReason: string | null;
  visibilityHistory: ActivityLogResponse[];
}

export interface AdminItemFilters {
  status?: ItemStatus;
  /** true chỉ bài đang hiện, false chỉ bài đang ẩn, bỏ trống là cả hai. */
  active?: boolean;
  offerType?: OfferType;
  categoryId?: number;
  ownerId?: number;
  q?: string;
  from?: string;
  to?: string;
  boosted?: boolean;
}

export const adminItemApi = {
  async list(params: AdminItemFilters & { page: number; size: number }) {
    const { data } = await http.get<PageResponse<AdminItemRow>>('/api/v1/admin/items', { params });
    return data;
  },
  async detail(itemId: number) {
    const { data } = await http.get<AdminItemFullDetail>(`/api/v1/admin/items/${itemId}`);
    return data;
  },
  async hide(itemId: number, reason: string) {
    const { data } = await http.post<AdminItemFullDetail>(`/api/v1/admin/items/${itemId}/hide`, { reason });
    return data;
  },
  async unhide(itemId: number, reason: string) {
    const { data } = await http.post<AdminItemFullDetail>(`/api/v1/admin/items/${itemId}/unhide`, { reason });
    return data;
  },
};
