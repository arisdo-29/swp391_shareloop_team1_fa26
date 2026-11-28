import { http } from './http';
import type { ItemRequest, ItemRequestStatus, ItemType } from '../types/domain';
import { apiUserId } from './mappers';
import type { ReportReason } from './reportApi';

// Backend tính openedByMe và canRespond theo người xem, frontend không tự so giờ (docs/api/request.md).
export interface RequestDispute {
  id: number;
  reason: ReportReason;
  openedByMe: boolean;
  status: 'OPEN' | 'AWAITING_RESPONSE' | 'RESOLVED' | 'DISMISSED';
  responseDueAt?: string | null;
  respondedAt?: string | null;
  canRespond: boolean;
}

export type CancelRequestCode =
  | 'BUSY'
  | 'NO_LONGER_NEEDED'
  | 'OTHER'
  | 'COUNTERPART_NO_SHOW'
  | 'COUNTERPART_NOT_SHIPPED'
  | 'CANNOT_CONTACT';

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
  scheduleRevision?: number;
  donorScheduleOkAt?: string;
  receiverScheduleOkAt?: string;
  donorWaiverAt?: string;
  receiverWaiverAt?: string;
  contactRevealedAt?: string;
  donorHandoverAt?: string;
  receiverHandoverAt?: string;
  completedAt?: string;
  // Lý do huỷ kèm tiền tố người huỷ: "[Chủ bài] ", "[Người nhận] ", "[Hệ thống] "; giữ nguyên khi hiển thị.
  cancelReason?: string | null;
  createdAt: string;
  receiverName?: string | null;
  receiverTrustStars?: number | null;
  receiverRank?: string | null;
  // Tên món đồ và chủ bài để không phải hiện id (docs/api/request.md).
  itemTitle?: string | null;
  itemImage?: string | null;
  donorId?: number | null;
  donorName?: string | null;
  offeredItemTitle?: string | null;
  offeredItemReferencePrice?: number | null;
  offeredItemAreaId?: number | null;
  trustStars?: number | null;
  rank?: string | null;
  complaintDeadline?: string | null;
  lastMessageAt?: string | null;
  disputeResponseDueAt?: string | null;
  // Khiếu nại nhìn từ phía người xem (tối đa hai). Chỉ có ở chi tiết, danh sách cuộc trò chuyện và kết quả của
  // Từ chối giao nhận / Giao nhận không thành công; nơi khác null.
  disputes?: RequestDispute[] | null;
  // Báo cáo vi phạm khi một bên từ chối nhận (giao dịch đã huỷ). Không phải khiếu nại nên tách khỏi disputes;
  // không có hạn giải trình. Null khi giao dịch không bị từ chối nhận.
  refusalReport?: RequestDispute | null;
  // Ảnh đại diện hai bên, dạng /api/v1/media/{id}; null khi chưa có ảnh.
  donorAvatarUrl?: string | null;
  receiverAvatarUrl?: string | null;
  // Backend tính: true khi giao dịch đã hoàn tất và đã qua complaintDeadline.
  disputeExpired?: boolean;
  // Chỉ có ở GET /requests/{id}; các danh sách trả null.
  handoverMedia?: RequestMediaResponse[] | null;
  // Hình thức hiện tại của bài chính; khác `type` khi người xin gửi chéo loại và chưa được chọn.
  itemOfferType?: 'GIVE' | 'SWAP' | null;
  // Số Credit chủ bài trả nếu giao dịch chuyển sang Trao đổi; backend đọc từ cấu hình, không tự tính.
  conversionFee?: number | null;
  // Cờ cảnh báo backend tính cho chủ bài: OFFERED_ITEM_BUSY, RECEIVER_TOO_BUSY, ITEM_UNDER_REVIEW.
  flags?: string[] | null;
  // Đề nghị huỷ đang chờ bên kia phản hồi (GN-06). Mọi trường null khi không có đề nghị nào.
  cancelRequestedBy?: number | null;
  cancelRequestedAt?: string | null;
  cancelRequestCode?: CancelRequestCode | null;
  cancelRequestLabel?: string | null;
  cancelRequestReason?: string | null;
  // Đề nghị dời lịch đang chờ bên kia phản hồi (GN-11) và số lần đã dời.
  rescheduleProposedBy?: number | null;
  rescheduleProposedAt?: string | null;
  rescheduleMeetingTime?: string | null;
  rescheduleMeetingPlace?: string | null;
  rescheduleCount?: number | null;
  // Giao hàng hai chiều. Chiều 1 là món chính chủ bài gửi, chiều 2 là món đổi người nhận gửi. Địa chỉ của bên kia chỉ có
  // sau khi chốt lịch, flag Provided cho biết đã nhập hay chưa.
  receiverAddress?: string | null;
  donorAddress?: string | null;
  receiverAddressProvided?: boolean | null;
  donorAddressProvided?: boolean | null;
  exchangeShippingWindowStart?: string | null;
  exchangeShippingUnsentLeg?: 1 | 2 | null;
  donorItemReopenAt?: string | null;
  receiverItemReopenAt?: string | null;
  donorShippedAt?: string | null;
  receiverShippedAt?: string | null;
  // Mốc hạn do backend tính, FE chỉ hiện.
  sendDeadlineAt?: string | null;
  autoCancelAt?: string | null;
  autoConfirmAt?: string | null;
  // Giao hàng: lúc tự ghi nhận Đã nhận cho từng chiều đã gửi mà bên nhận im lặng.
  leg1AutoReceiveAt?: string | null;
  leg2AutoReceiveAt?: string | null;
  cancelRequestExpiresAt?: string | null;
  rescheduleExpiresAt?: string | null;
}

export interface CreateRequestBody {
  offeredItemId?: number;
}

export interface ScheduleRequestBody {
  deliveryMethod: 'IN_PERSON' | 'SHIPPING';
  // Giao hàng không dùng địa điểm, meetingTime là ngày gửi hàng dự kiến.
  meetingPlace?: string;
  meetingTime: string;
  agreeToWaiver: true;
  // Địa chỉ nhận hàng của chính người gọi khi Giao hàng (tối đa 255 ký tự).
  shippingAddress?: string;
}

export interface ConfirmLogisticsRequestBody {
  scheduleRevision: number;
  agreeToWaiver?: boolean;
  shippingAddress?: string;
}

export interface ShipmentRequestBody {
  mediaIds?: number[];
}

export interface HandoverConfirmRequestBody {
  mediaIds?: number[];
}

// Dùng chung cho "Giao nhận không thành công" (5 đến 500 ký tự) và "Từ chối giao nhận" (5 đến 1000 ký tự).
export interface HandoverOutcomeBody {
  // Mã lý do chọn trong danh sách (chỉ dùng cho Giao nhận không thành công); bỏ trống thì backend coi là OTHER.
  code?: CancelRequestCode;
  reason?: string;
  evidenceMediaIds?: number[];
}

export interface RescheduleRequestBody {
  meetingTime: string;
  // Chỉ Gặp trực tiếp, bỏ trống nếu không đổi địa điểm.
  meetingPlace?: string;
}

export interface CancelRequestBody {
  code: CancelRequestCode;
  // Bắt buộc từ 5 ký tự khi code là OTHER.
  reason?: string;
}

export interface RejectCancelRequestBody {
  explanation: string;
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

export function toFeItemRequest(
  request: RequestResponse,
  requesterId = apiUserId(request.receiverId),
): ItemRequest {
  const trustStars = request.receiverTrustStars ?? request.trustStars;
  const rank = request.receiverRank ?? request.rank;
  return {
    id: `api_req_${request.id}`,
    type: request.type === 'SWAP' ? 'trade' : 'gift',
    itemId: String(request.itemId),
    itemTitle: request.itemTitle ?? undefined,
    itemImage: request.itemImage ?? undefined,
    donorName: request.donorName ?? undefined,
    offeredItemId: request.offeredItemId ? String(request.offeredItemId) : undefined,
    offeredItemTitle: request.offeredItemTitle ?? undefined,
    offeredItemReferencePrice: request.offeredItemReferencePrice ?? undefined,
    offeredItemAreaId: request.offeredItemAreaId ?? undefined,
    requesterId,
    requesterName: request.receiverName ?? undefined,
    requesterTrustStars: typeof trustStars === 'number' ? trustStars : undefined,
    requesterRank: rank ?? undefined,
    status: request.status,
    message: '',
    createdAt: request.createdAt,
    itemOfferType: toFeOfferType(request.itemOfferType),
    conversionFee: typeof request.conversionFee === 'number' ? request.conversionFee : undefined,
    flags: request.flags ?? undefined,
  };
}

function toFeOfferType(offerType: RequestResponse['itemOfferType']): ItemType | undefined {
  if (offerType === 'SWAP') return 'trade';
  if (offerType === 'GIVE') return 'gift';
  return undefined;
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
  /** Mọi yêu cầu tới các bài của mình trong một lần (G2), thay cho gọi forItem từng bài. */
  async received(currentUserId: number, statuses?: ItemRequestStatus[]) {
    const { data } = await http.get<RequestResponse[]>('/api/v1/me/received-requests', {
      params: { currentUserId, status: statuses?.length ? statuses.join(',') : undefined },
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
  async changeOfferedItem(id: number, currentUserId: number, offeredItemId: number) {
    const { data } = await http.post<RequestResponse>(
      `/api/v1/requests/${id}/offered-item`,
      { offeredItemId },
      { params: { currentUserId } },
    );
    return data;
  },
  async updateSchedule(id: number, currentUserId: number, body: ScheduleRequestBody) {
    const { data } = await http.post<RequestResponse>(`/api/v1/requests/${id}/schedule`, body, {
      params: { currentUserId },
    });
    return data;
  },
  async confirmLogistics(id: number, currentUserId: number, body: ConfirmLogisticsRequestBody) {
    const { data } = await http.post<RequestResponse>(
      `/api/v1/requests/${id}/confirm-logistics`,
      body,
      {
        params: { currentUserId },
      },
    );
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
  // Bên kia chưa bấm hoàn tất thì backend huỷ giao dịch, đã bấm thì mở tranh chấp (docs/api/request.md).
  async handoverFailed(id: number, currentUserId: number, body: HandoverOutcomeBody) {
    const { data } = await http.post<RequestResponse>(
      `/api/v1/requests/${id}/handover-failed`,
      body,
      { params: { currentUserId } },
    );
    return data;
  },
  async refuseHandover(id: number, currentUserId: number, body: HandoverOutcomeBody) {
    const { data } = await http.post<RequestResponse>(
      `/api/v1/requests/${id}/refuse-handover`,
      body,
      { params: { currentUserId } },
    );
    return data;
  },
  // Đề nghị huỷ trước giờ hẹn. Giao dịch chưa huỷ cho tới khi bên kia phản hồi (docs/api/request.md).
  async requestCancel(id: number, currentUserId: number, body: CancelRequestBody) {
    const { data } = await http.post<RequestResponse>(`/api/v1/requests/${id}/cancel-request`, body, {
      params: { currentUserId },
    });
    return data;
  },
  async acceptCancelRequest(id: number, currentUserId: number) {
    const { data } = await http.post<RequestResponse>(
      `/api/v1/requests/${id}/cancel-request/accept`,
      {},
      { params: { currentUserId } },
    );
    return data;
  },
  async rejectCancelRequest(id: number, currentUserId: number, body: RejectCancelRequestBody) {
    const { data } = await http.post<RequestResponse>(
      `/api/v1/requests/${id}/cancel-request/reject`,
      body,
      { params: { currentUserId } },
    );
    return data;
  },
  async withdrawCancelRequest(id: number, currentUserId: number) {
    const { data } = await http.delete<RequestResponse>(`/api/v1/requests/${id}/cancel-request`, {
      params: { currentUserId },
    });
    return data;
  },
  // Dời lịch sau khi chốt (docs/api/request.md). Lịch cũ giữ nguyên cho tới khi bên kia đồng ý.
  async proposeReschedule(id: number, currentUserId: number, body: RescheduleRequestBody) {
    const { data } = await http.post<RequestResponse>(`/api/v1/requests/${id}/reschedule`, body, {
      params: { currentUserId },
    });
    return data;
  },
  async acceptReschedule(id: number, currentUserId: number) {
    const { data } = await http.post<RequestResponse>(
      `/api/v1/requests/${id}/reschedule/accept`,
      {},
      { params: { currentUserId } },
    );
    return data;
  },
  async rejectReschedule(id: number, currentUserId: number) {
    const { data } = await http.post<RequestResponse>(
      `/api/v1/requests/${id}/reschedule/reject`,
      {},
      { params: { currentUserId } },
    );
    return data;
  },
  // Báo đã gửi hàng cho chiều của mình (Giao hàng). Lần gửi đầu chuyển sang bước giao nhận.
  async markShipped(id: number, currentUserId: number, body: ShipmentRequestBody) {
    const { data } = await http.post<RequestResponse>(`/api/v1/requests/${id}/shipment`, body, {
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
    const { data } = await http.post<RequestResponse>(
      `/api/v1/requests/${id}/confirm-handover`,
      body ?? {},
      {
        params: { currentUserId },
      },
    );
    return data;
  },
};
