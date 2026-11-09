
export const REPUTATION_RANKS = {
  BRONZE: 'Đồng',
  SILVER: 'Bạc',
  GOLD: 'Vàng',
  DIAMOND: 'Kim cương',
} as const;

export type ReputationRank = keyof typeof REPUTATION_RANKS;

/**
 * Trả về nhãn tiếng Việt tương ứng với hạng thành viên (YC-06).
 * BRONZE -> 'Đồng', SILVER -> 'Bạc', GOLD -> 'Vàng', DIAMOND -> 'Kim cương'.
 */
export function getRankLabel(rank?: string | null): string {
  if (!rank) return '';
  const normalized = rank.toUpperCase().trim() as ReputationRank;
  return REPUTATION_RANKS[normalized] ?? rank;
}

/**
 * Định dạng tỉ lệ đúng mô tả (asDescribedRate / accuracyRate) dạng 0–1 thành phần trăm (YC-06).
 * Ví dụ 0.95 -> '95%'. Nếu null hoặc không hợp lệ -> '—'.
 */
export function formatAsDescribedRate(rate?: number | null): string {
  if (rate == null || Number.isNaN(rate)) {
    return '—';
  }
  return `${Math.round(rate * 100)}%`;
}

/**
 * Định dạng số sao uy tín (trustStars) 1–5 sao (YC-06).
 */
export function formatTrustStars(stars?: number | null): string {
  if (stars == null || Number.isNaN(stars)) {
    return '—';
  }
  return Number.isInteger(stars) ? `${stars}` : stars.toFixed(1);
}
