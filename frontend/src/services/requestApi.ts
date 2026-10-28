import { http } from './http';
import type { ItemRequest, ItemRequestStatus } from '../types/domain';
import { apiUserId } from './mappers';

// Theo docs/api/request.md
export interface RequestResponse {
  id: number;
  itemId: number;
  receiverId: number;
  offeredItemId?: number;
  type: 'GIVE' | 'SWAP';
  status: ItemRequestStatus;
  deliveryMethod?: string;
  meetingPlace?: string;
  meetingTime?: string;
  donorScheduleOkAt?: string;
  receiverScheduleOkAt?: string;
  donorWaiverAt?: string;
  receiverWaiverAt?: string;
  contactRevealedAt?: string;
  donorHandoverAt?: string;
  receiverHandoverAt?: string;
  asDescribed?: boolean;
  completedAt?: string;
  // Lý do huỷ kèm tiền tố người huỷ: "[Chủ bài] ", "[Người nhận] ", "[Hệ thống] "; giữ nguyên khi hiển thị.
  cancelReason?: string | null;
  createdAt: string;
  receiverName?: string | null;
  receiverTrustStars?: number | null;
  receiverRank?: string | null;
  receiverAsDescribedRate?: number | null;
  // Tên món đồ và chủ bài để không phải hiện id (docs/api/request.md).
  itemTitle?: string | null;
  itemImage?: string | null;
  donorId?: number | null;
  donorName?: string | null;
  offeredItemTitle?: string | null;
  trustStars?: number | null;
  rank?: string | null;
  asDescribedRate?: number | null;
  complaintDeadline?: string | null;
  lastMessageAt?: string | null;
}

export interface CreateRequestBody {
  offeredItemId?: number;
}

export interface ScheduleRequestBody {
  deliveryMethod: 'IN_PERSON' | 'SHIPPING';
  meetingPlace: string;
  meetingTime: string;
  agreeToWaiver?: boolean;
}

export interface ConfirmLogisticsRequestBody {
  agreeToWaiver?: boolean;
}

export interface HandoverConfirmRequestBody {
  asDescribed?: boolean;
  notAsDescribedReason?: string;
}

export interface HandoverIssueBody {
  note: string;
  evidenceMediaIds?: number[];
}

export interface RequestContactResponse {
  userId: number;
  fullName: string;
  email: string;
  phone: string;
}

export type RequestMediaStage = 'HANDOVER' | 'DISPUTE';

export interface RequestMediaResponse {
  mediaId: number;
  url: string;
  stage: RequestMediaStage;
  uploadedBy: number;
  createdAt: string;
}

export interface AttachRequestMediaBody {
  mediaIds: number[];
  stage?: RequestMediaStage;
}

export const toBackendUserId = (userId: string | null | undefined) => {
  const match = /^api_([1-9]\d*)$/.exec(userId ?? '');
  if (!match) return null;
  const id = Number(match[1]);
  return Number.isSafeInteger(id) ? id : null;
};

export function toFeItemRequest(request: RequestResponse, requesterId = apiUserId(request.receiverId)): ItemRequest {
  const trustStars = request.receiverTrustStars ?? request.trustStars;
  const rank = request.receiverRank ?? request.rank;
  const asDescribedRate = request.receiverAsDescribedRate ?? request.asDescribedRate;
  return {
    id: `api_req_${request.id}`,
    type: request.type === 'SWAP' ? 'trade' : 'gift',
    itemId: String(request.itemId),
    itemTitle: request.itemTitle ?? undefined,
    itemImage: request.itemImage ?? undefined,
    donorName: request.donorName ?? undefined,
    offeredItemId: request.offeredItemId ? String(request.offeredItemId) : undefined,
    offeredItemTitle: request.offeredItemTitle ?? undefined,
    requesterId,
    requesterName: request.receiverName ?? undefined,
    requesterTrustStars: typeof trustStars === 'number' ? trustStars : undefined,
    requesterRank: rank ?? undefined,
    requesterAsDescribedRate: typeof asDescribedRate === 'number' ? asDescribedRate : undefined,
    status: request.status,
    message: '',
    createdAt: request.createdAt,
  };
}

export const requestApi = {
  async media(id: number): Promise<RequestMediaResponse[]> {
    const { data } = await http.get<RequestMediaResponse[]>(`/api/v1/requests/${id}/media`);
    return data;
  },
  async attachMedia(id: number, body: AttachRequestMediaBody): Promise<RequestMediaResponse[]> {
    const { data } = await http.post<RequestMediaResponse[]>(`/api/v1/requests/${id}/media`, body);
    return data;
  },
  async create(itemId: number, currentUserId: number, offeredItemId?: number) {
    const body: CreateRequestBody = offeredItemId === undefined ? {} : { offeredItemId };
    const { data } = await http.post<RequestResponse>(`/api/v1/items/${itemId}/requests`, body, {
      params: { currentUserId },
    });
    return data;
  },
  async forItem(itemId: number, currentUserId: number) {
    const { data } = await http.get<RequestResponse[]>(`/api/v1/items/${itemId}/requests`, {
      params: { currentUserId },
    });
    return data;
  },
  async mine(currentUserId: number) {
    const { data } = await http.get<RequestResponse[]>('/api/v1/me/requests', {
      params: { currentUserId },
    });
    return data;
  },
  async get(id: number, currentUserId: number) {
    const { data } = await http.get<RequestResponse>(`/api/v1/requests/${id}`, {
      params: { currentUserId },
    });
    return data;
  },
  async cancel(id: number, currentUserId: number) {
    const { data } = await http.post<RequestResponse>(`/api/v1/requests/${id}/cancel`, undefined, {
      params: { currentUserId },
    });
    return data;
  },
  async select(id: number, currentUserId: number) {
    const { data } = await http.post<RequestResponse>(`/api/v1/requests/${id}/select`, undefined, {
      params: { currentUserId },
    });
    return data;
  },
  async updateSchedule(id: number, currentUserId: number, body: ScheduleRequestBody) {
    const { data } = await http.post<RequestResponse>(`/api/v1/requests/${id}/schedule`, body, {
      params: { currentUserId },
    });
    return data;
  },
  async confirmLogistics(id: number, currentUserId: number, body?: ConfirmLogisticsRequestBody) {
    const { data } = await http.post<RequestResponse>(`/api/v1/requests/${id}/confirm-logistics`, body ?? {}, {
      params: { currentUserId },
    });
    return data;
  },
  async cancelTransaction(id: number, currentUserId: number, reason: string) {
    const { data } = await http.post<RequestResponse>(
      `/api/v1/requests/${id}/cancel-transaction`,
      { reason },
      { params: { currentUserId } },
    );
    return data;
  },
  async reportHandoverIssue(id: number, currentUserId: number, body: HandoverIssueBody) {
    const { data } = await http.post<RequestResponse>(`/api/v1/requests/${id}/handover-issue`, body, {
      params: { currentUserId },
    });
    return data;
  },
  async getContact(id: number, currentUserId: number) {
    const { data } = await http.get<RequestContactResponse>(`/api/v1/requests/${id}/contact`, {
      params: { currentUserId },
    });
    return data;
  },
  async confirmHandover(id: number, currentUserId: number, body?: HandoverConfirmRequestBody) {
    const { data } = await http.post<RequestResponse>(`/api/v1/requests/${id}/confirm-handover`, body ?? {}, {
      params: { currentUserId },
    });
    return data;
  },
};
