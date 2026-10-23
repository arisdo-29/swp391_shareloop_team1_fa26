import { http } from './http';
import type { PageResponse } from './itemApi';

// Contract: docs/api/admin.md mục 3.3 (GET /api/v1/admin/users)
export type AdminAccountRole = 'ADMIN' | 'USER';
export type AdminAccountState = 'ACTIVE' | 'LOCKED' | 'PENDING_VERIFICATION' | 'BANNED';

export interface AdminUserSummary {
  id: number;
  fullName: string;
  email: string;
  phone: string;
  role: AdminAccountRole;
  status: AdminAccountState;
  locked: boolean;
  createdAt: string;
}

export interface AdminUserListParams {
  keyword?: string;
  status?: AdminAccountState;
  role?: AdminAccountRole;
  page: number;
  size: number;
}

export const adminUserApi = {
  async list(params: AdminUserListParams) {
    const { data } = await http.get<PageResponse<AdminUserSummary>>('/api/v1/admin/users', { params });
    return data;
  },
};
