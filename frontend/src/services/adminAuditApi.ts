import { http } from './http';

export interface ActivityLogResponse {
  id: number;
  actorId?: number | null;
  actorName?: string;
  actorEmail?: string | null;
  typeLabel?: string;
  targetTitle?: string | null;
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

interface ActivityLogWirePage extends Partial<ActivityLogPage> {
  content: ActivityLogResponse[];
  page?: { number: number; size: number; totalElements: number; totalPages: number };
}

export const adminAuditApi = {
  async list(params: ActivityLogParams = {}) {
    const { data } = await http.get<ActivityLogWirePage>('/api/v1/admin/activity-logs', { params });
    const number = data.page?.number ?? data.number ?? 0;
    const totalPages = data.page?.totalPages ?? data.totalPages ?? 0;
    return {
      content: data.content,
      number,
      size: data.page?.size ?? data.size ?? params.size ?? 20,
      totalElements: data.page?.totalElements ?? data.totalElements ?? 0,
      totalPages,
      first: number === 0,
      last: number + 1 >= totalPages,
    };
  },
  async types() {
    const { data } = await http.get<{ type: string; label: string }[]>('/api/v1/admin/activity-logs/types');
    return data;
  },
};
