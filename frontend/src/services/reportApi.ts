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

export const reportApi = {
  async createReport(data: CreateReportRequest): Promise<ReportResponse> {
    const response = await http.post<ReportResponse>('/api/v1/reports', data);
    return response.data;
  },
};

