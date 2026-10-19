import { http } from './http';

export interface ActivityLogResponse {
  id: number;
  actorId?: number | null;
  type: string;
  targetType?: string | null;
  targetId?: number | null;
  data?: Record<string, unknown> | null;
  reason?: string | null;
  detail?: string | null;
  createdAt: string;
}

export interface ActivityLogParams {
  actorId?: number;
  type?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

export interface ActivityLogPage {
  content: ActivityLogResponse[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  numberOfElements: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export const adminAuditApi = {
  async list(params: ActivityLogParams = {}) {
    const { data } = await http.get<ActivityLogPage>('/api/v1/admin/activity-logs', { params });
    return data;
  },
};
