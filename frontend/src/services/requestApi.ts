import { http } from './http';
import type { ItemRequest } from '../types/domain';
import { apiUserId } from './mappers';

// Theo docs/api/request.md
export interface RequestResponse {
  id: number;
  itemId: number;
  receiverId: number;
  offeredItemId?: number;
  type: string;
  status: string;
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
  createdAt: string;
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

export interface RequestContactResponse {
  userId: number;
  fullName: string;
  email: string;
  phone: string;
}

export const toBackendUserId = (userId: string | null | undefined) => {
  const match = /^api_([1-9]\d*)$/.exec(userId ?? '');
  if (!match) return null;
  const id = Number(match[1]);
  return Number.isSafeInteger(id) ? id : null;
};

export function toFeItemRequest(request: RequestResponse, requesterId = apiUserId(request.receiverId)): ItemRequest {
  return {
    id: `api_req_${request.id}`,
    type: request.type === 'SWAP' ? 'trade' : 'gift',
    itemId: String(request.itemId),
    offeredItemId: request.offeredItemId ? String(request.offeredItemId) : undefined,
    requesterId,
    status: request.status as ItemRequest['status'],
    message: '',
    createdAt: request.createdAt,
  };
}

export const requestApi = {
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
