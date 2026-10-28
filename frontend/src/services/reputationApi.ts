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

/** Tải hết lịch sử điểm (mỗi trang 50 dòng, tối đa maxPages trang) để hiện tổng cộng. */
export async function listAllPointHistory(maxPages = 20): Promise<PointHistory[]> {
  const entries: PointHistory[] = [];
  for (let page = 0; page < maxPages; page += 1) {
    const { history } = await getMyReputation(page, 50);
    entries.push(...history.content);
    if (page + 1 >= history.totalPages || history.content.length === 0) break;
  }
  return entries;
}

export interface PointHistorySummary {
  gained: number; // tổng điểm được cộng
  lost: number; // tổng điểm bị trừ (số dương)
  notCounted: number; // số lần giao dịch không được cộng điểm (delta = 0)
  changes: number; // tổng số dòng lịch sử
}

export function summarizePointHistory(entries: PointHistory[]): PointHistorySummary {
  return entries.reduce<PointHistorySummary>(
    (summary, entry) => {
      if (entry.delta > 0) summary.gained += entry.delta;
      else if (entry.delta < 0) summary.lost += -entry.delta;
      else summary.notCounted += 1;
      summary.changes += 1;
      return summary;
    },
    { gained: 0, lost: 0, notCounted: 0, changes: 0 },
  );
}

export const reputationApi = {
  getMyReputation,
  listAllPointHistory,
};

