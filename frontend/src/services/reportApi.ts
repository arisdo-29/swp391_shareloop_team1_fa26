import { http } from './http';

export type ReportTargetType = 'ITEM' | 'USER' | 'REQUEST';

export type ReportReason =
  | 'NOT_AS_DESCRIBED'
  | 'COUNTERFEIT'
  | 'PROHIBITED_ITEM'
  | 'NO_SHOW'
  | 'WRONG_CONTACT_INFO'
  | 'HARASSMENT'
  | 'OTHER';

export type ReportTarget =
  | { targetType: 'ITEM'; itemId: number; targetId?: never }
  | { targetType: 'ITEM'; targetId: number; itemId?: never }
  | { targetType: 'USER'; targetUserId: number; targetId?: never; requestId?: number }
  | { targetType: 'USER'; targetId: number; targetUserId?: never; requestId?: number }
  | { targetType: 'REQUEST'; requestId: number };

export type CreateReportRequest = ReportTarget & {
  reason: ReportReason;
  description: string;
};

export interface ReportResponse {
  id: number;
  reporterId: number;
  targetType: ReportTargetType;
  itemId: number | null;
  targetUserId: number | null;
  requestId: number | null;
  reason: ReportReason;
  description: string;
  stage: 'BEFORE' | 'AFTER';
  status: 'OPEN' | 'AWAITING_RESPONSE' | 'RESOLVED' | 'DISMISSED';
  createdAt: string;
}

export interface CreateDisputeRequest {
  reason: string;
  note?: string;
  evidenceMediaIds?: number[];
}

export interface DisputeResponseRequest {
  note: string;
  evidenceMediaIds?: number[];
}

export interface DisputeResponse {
  id: number;
  responseDueAt: string;
}

export function openDispute(requestId: number, body: CreateDisputeRequest): Promise<DisputeResponse> {
  return http.post<DisputeResponse>(`/api/v1/requests/${requestId}/disputes`, body).then(res => res.data);
}

export function respondDispute(disputeId: number, body: DisputeResponseRequest): Promise<void> {
  return http.post<void>(`/api/v1/disputes/${disputeId}/response`, body).then(() => undefined);
}

export const reportApi = {
  async create(input: CreateReportRequest): Promise<ReportResponse> {
    const { data } = await http.post<ReportResponse>('/api/v1/reports', input);
    return data;
  },
  openDispute,
  respondDispute,
};
