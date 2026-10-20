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
  evidenceMediaIds?: number[];
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

export type ReportStatus = ReportResponse['status'];
export type ReportDecision = 'UPHOLD' | 'DISMISS';
export interface ReportConclusionResponse {
  id: number;
  targetType: ReportTargetType;
  itemId: number | null;
  targetUserId: number | null;
  requestId: number | null;
  reason: ReportReason;
  status: ReportStatus;
  resolvedBy: number;
  resolution: string | null;
  starsDeducted: number;
  resolvedAt: string;
}
export interface AdminReportPage {
  items: ReportResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
export interface AdminReportDetail {
  report: ReportResponse;
  reporter: { id: number; fullName: string | null };
  reportedUser: { id: number; fullName: string | null } | null;
  relatedItemId: number | null;
  response: string | null;
  respondedAt: string | null;
  responseDueAt: string | null;
  evidence: ReportEvidence[];
  responseEvidence: ReportEvidence[];
  resolvedBy: number | null;
  resolution: string | null;
  starsDeducted: number;
  resolvedAt: string | null;
}
export interface ReportEvidence {
  id: number;
  url: string | null;
  available: boolean;
}

export interface CreateDisputeRequest {
  reason: ReportReason;
  note?: string;
  evidenceMediaIds?: number[];
}

export interface DisputeResponseRequest {
  note: string;
  evidenceMediaIds?: number[];
}

export interface RespondReportRequest {
  response: string;
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
  async respond(reportId: number, input: RespondReportRequest): Promise<void> {
    await http.post<void>(`/api/v1/reports/${reportId}/response`, input);
  },
  async evidence(reportId: number, mediaId: number, signal?: AbortSignal) {
    const { data } = await http.get<Blob>(`/api/v1/admin/reports/${reportId}/evidence/${mediaId}`, {
      responseType: 'blob',
      signal,
    });
    return data;
  },
  async list(
    params: {
      page?: number;
      size?: number;
      status?: ReportStatus;
      targetType?: ReportTargetType;
    } = {},
    signal?: AbortSignal,
  ) {
    const { data } = await http.get<AdminReportPage>('/api/v1/admin/reports', { params, signal });
    return data;
  },
  async detail(reportId: number, signal?: AbortSignal) {
    const { data } = await http.get<AdminReportDetail>(`/api/v1/admin/reports/${reportId}`, {
      signal,
    });
    return data;
  },
  async conclude(reportId: number, input: { decision: ReportDecision; note?: string }) {
    const { data } = await http.post<ReportConclusionResponse>(`/api/v1/admin/reports/${reportId}/conclude`, input);
    return data;
  },
  async create(input: CreateReportRequest): Promise<ReportResponse> {
    const { data } = await http.post<ReportResponse>('/api/v1/reports', input);
    return data;
  },
  openDispute,
  respondDispute,
};
