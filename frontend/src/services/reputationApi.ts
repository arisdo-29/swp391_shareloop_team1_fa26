import { http } from './http';

export interface PointHistory {
  delta: number;
  reason: string;
  createdAt: string;
}

export interface MyReputation {
  totalPoints: number;
  rank: 'BRONZE' | 'SILVER' | 'GOLD' | 'DIAMOND';
  trustStars: number;
  accuracyRate: number | null;
  history: {
    content: PointHistory[];
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
  };
}

export async function getMyReputation(page = 0, size = 20): Promise<MyReputation> {
  const { data } = await http.get<MyReputation>('/api/v1/me/reputation', {
    params: { page, size },
  });
  return data;
}

export const reputationApi = {
  getMyReputation,
};

