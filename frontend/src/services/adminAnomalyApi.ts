import { http } from './http';

// Contract: docs/api/admin.md mục 7 (GET /api/v1/admin/anomalies)
export interface AnomalyUser {
  id: number;
  fullName: string;
  // Người dùng đã xoá mềm thì backend trả null
  email: string | null;
}

export interface RepeatedPair {
  userA: AnomalyUser;
  userB: AnomalyUser;
  completedCount: number;
  lastCompletedAt: string;
}

export interface SharedIpAccount {
  id: number;
  fullName: string;
  email: string;
  registeredAt: string;
  trustStars: number;
  status: 'ACTIVE' | 'BANNED' | string;
  locked: boolean;
}

export interface SharedIpGroup {
  ipAddress: string;
  accountCount: number;
  accounts: SharedIpAccount[];
}

export interface AnomalyReportResponse {
  generatedAt: string;
  pairWindowDays: number;
  pairThreshold: number;
  repeatedPairs: RepeatedPair[];
  ipWindowDays: number;
  ipMinAccounts: number;
  sharedIpGroups: SharedIpGroup[];
}

export const adminAnomalyApi = {
  async report(): Promise<AnomalyReportResponse> {
    const { data } = await http.get<AnomalyReportResponse>('/api/v1/admin/anomalies');
    return data;
  },
};
