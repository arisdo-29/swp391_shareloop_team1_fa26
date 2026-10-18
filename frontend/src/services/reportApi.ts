import { http } from './http';

export type TargetType = 'ITEM' | 'USER' | 'REQUEST';

export type ReportReason =
  | 'NOT_AS_DESCRIBED'
  | 'COUNTERFEIT'
  | 'PROHIBITED_ITEM'
  | 'NO_SHOW'
  | 'WRONG_CONTACT_INFO'
  | 'HARASSMENT'
  | 'OTHER';

export type ReportStage = 'BEFORE' | 'AFTER';

export type ReportStatus = 'OPEN' | 'AWAITING_RESPONSE' | 'RESOLVED' | 'DISMISSED';

export interface CreateReportRequest {
  targetType: TargetType;
  targetId?: number;
  itemId?: number;
  targetUserId?: number;
  requestId?: number;
  reason: ReportReason;
  description: string;
}

export interface ReportResponse {
  id: number;
  reporterId: number;
  targetType: TargetType;
  itemId: number | null;
  targetUserId: number | null;
  requestId: number | null;
  reason: ReportReason;
  description: string;
  stage: ReportStage;
  status: ReportStatus;
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
  async createReport(data: CreateReportRequest): Promise<ReportResponse> {
    const response = await http.post<ReportResponse>('/api/v1/reports', data);
    return response.data;
  },
  openDispute,
  respondDispute,
};

