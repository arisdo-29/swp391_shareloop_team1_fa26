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

export interface AnomalyReportResponse {
  generatedAt: string;
  pairWindowDays: number;
  pairThreshold: number;
  repeatedPairs: RepeatedPair[];
}

export const adminAnomalyApi = {
  async report(): Promise<AnomalyReportResponse> {
    const { data } = await http.get<AnomalyReportResponse>('/api/v1/admin/anomalies');
    return data;
  },
};
