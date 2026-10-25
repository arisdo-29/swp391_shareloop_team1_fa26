import type { AppStateData, ReputationPointRuleKey, RankRule } from '../types/domain';

export const REPUTATION_RANKS = {
  BRONZE: 'Đồng',
  SILVER: 'Bạc',
  GOLD: 'Vàng',
  DIAMOND: 'Kim cương',
} as const;

export type ReputationRank = keyof typeof REPUTATION_RANKS;

/**
 * Trả về nhãn tiếng Việt tương ứng với hạng thành viên (FR-65).
 * BRONZE -> 'Đồng', SILVER -> 'Bạc', GOLD -> 'Vàng', DIAMOND -> 'Kim cương'.
 */
export function getRankLabel(rank?: string | null): string {
  if (!rank) return '';
  const normalized = rank.toUpperCase().trim() as ReputationRank;
  return REPUTATION_RANKS[normalized] ?? rank;
}

/**
 * Định dạng tỉ lệ đúng mô tả (asDescribedRate / accuracyRate) dạng 0–1 thành phần trăm (FR-65).
 * Ví dụ 0.95 -> '95%'. Nếu null hoặc không hợp lệ -> '—'.
 */
export function formatAsDescribedRate(rate?: number | null): string {
  if (rate == null || Number.isNaN(rate)) {
    return '—';
  }
  return `${Math.round(rate * 100)}%`;
}

/**
 * Định dạng số sao uy tín (trustStars) 1–5 sao (FR-65).
 */
export function formatTrustStars(stars?: number | null): string {
  if (stars == null || Number.isNaN(stars)) {
    return '—';
  }
  return Number.isInteger(stars) ? `${stars}` : stars.toFixed(1);
}

export function rankFor(points: number, ranks: RankRule[]) {
  return (
    [...ranks]
      .filter((rank) => rank.status !== 'inactive')
      .sort((a, b) => b.minPoints - a.minPoints)
      .find((rank) => points >= rank.minPoints && (rank.maxPoints === undefined || points <= rank.maxPoints))
      ?.name ?? 'Thành viên mới'
  );
}

export function recalculateUserRank(data: AppStateData, userId: string) {
  const user = data.users.find((entry) => entry.id === userId);
  if (user && user.role !== 'admin') user.rank = rankFor(user.rewardPoints, data.ranks);
}

export function applyReputationPointEvent(
  data: AppStateData,
  input: {
    userId: string;
    key: ReputationPointRuleKey;
    ref: string;
    createdAt?: string;
  },
) {
  const user = data.users.find((entry) => entry.id === input.userId && entry.role !== 'admin');
  const rule = data.pointRules.find((entry) => entry.key === input.key && entry.status === 'active');
  if (!user || !rule || data.pointHistory.some((entry) => entry.ref === input.ref && entry.userId === user.id))
    return false;

  const change = rule.type === 'plus' ? rule.points : -rule.points;
  user.rewardPoints = Math.max(0, user.rewardPoints + change);
  recalculateUserRank(data, user.id);
  data.pointHistory.unshift({
    id: `ph_${Date.now()}_${data.pointHistory.length + 1}`,
    userId: user.id,
    ruleId: rule.id,
    event: rule.behavior,
    change,
    pointsAfter: user.rewardPoints,
    ref: input.ref,
    createdAt: input.createdAt ?? new Date().toISOString(),
  });
  return true;
}

