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

export const reportApi = {
  async create(input: CreateReportRequest): Promise<ReportResponse> {
    const { data } = await http.post<ReportResponse>('/api/v1/reports', input);
    return data;
  },
};
