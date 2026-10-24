import { http } from './http';

export interface AnomalyUser {
  id: number;
  fullName: string;
  email: string | null;
}

export interface RepeatedPairAnomaly {
  userA: AnomalyUser;
  userB: AnomalyUser;
  completedCount: number;
  lastCompletedAt: string;
}

export interface AnomalyReportResponse {
  generatedAt: string;
  pairWindowDays: number;
  pairThreshold: number;
  repeatedPairs: RepeatedPairAnomaly[];
}

export const adminAnomalyApi = {
  async getReport() {
    const { data } = await http.get<AnomalyReportResponse>('/api/v1/admin/anomalies');
    return data;
  },
};
