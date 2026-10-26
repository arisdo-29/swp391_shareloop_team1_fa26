import { formatAsDescribedRate, formatTrustStars, getRankLabel } from '../utils/reputation';

export interface ReputationBadgeProps {
  trustStars?: number | null;
  rank?: string | null;
  asDescribedRate?: number | null;
  className?: string;
  compact?: boolean;
}

function rankBadgeColor(rank?: string | null) {
  const upper = rank?.toUpperCase().trim();
  if (upper === 'DIAMOND') return 'bg-info-soft text-info ring-info/20';
  if (upper === 'GOLD') return 'bg-warning-soft text-warning ring-warning/20';
  if (upper === 'SILVER') return 'bg-surface-low text-text-secondary ring-text-muted/20';
  return 'bg-secondary-soft text-secondary-hover ring-secondary/20'; // BRONZE
}

/**
 * Component hiển thị thông tin uy tín người dùng: Hạng, Sao uy tín và Tỉ lệ đúng mô tả (FR-65).
 */
export function ReputationBadge({
  trustStars,
  rank,
  asDescribedRate,
  className = '',
  compact = false,
}: ReputationBadgeProps) {
  const rankLabel = getRankLabel(rank);
  const starsText = formatTrustStars(trustStars);
  const rateText = formatAsDescribedRate(asDescribedRate);

  if (!rankLabel && starsText === '—' && rateText === '—') {
    return null;
  }

  if (compact) {
    return (
      <span className={`inline-flex flex-wrap items-center gap-1.5 text-xs ${className}`}>
        {rankLabel ? (
          <span
            className={`inline-flex items-center rounded-sm px-1.5 py-0.5 text-2xs font-semibold ring-1 ring-inset ${rankBadgeColor(
              rank,
            )}`}
          >
            {rankLabel}
          </span>
        ) : null}
        {starsText !== '—' ? (
          <span className="inline-flex items-center gap-0.5 font-medium text-warning" title="Sao uy tín">
            <span aria-hidden="true">★</span>
            <span>{starsText}/5</span>
          </span>
        ) : null}
        {rateText !== '—' ? (
          <span className="text-2xs text-text-muted" title="Tỉ lệ đúng mô tả">
            ({rateText} đúng mô tả)
          </span>
        ) : null}
      </span>
    );
  }

  return (
    <div className={`flex flex-wrap items-center gap-2 text-xs ${className}`}>
      {rankLabel ? (
        <span
          className={`inline-flex items-center rounded-sm px-2 py-0.5 text-xs font-semibold ring-1 ring-inset ${rankBadgeColor(
            rank,
          )}`}
        >
          Hạng {rankLabel}
        </span>
      ) : null}
      {starsText !== '—' ? (
        <span className="inline-flex items-center gap-1 font-semibold text-warning" title="Sao uy tín">
          <span aria-hidden="true" className="text-sm leading-none">★</span>
          <span>{starsText}/5 sao</span>
        </span>
      ) : null}
      {rateText !== '—' ? (
        <span
          className="inline-flex items-center rounded-sm bg-success-soft px-2 py-0.5 font-medium text-success ring-1 ring-inset ring-success/20"
          title="Tỉ lệ đúng mô tả"
        >
          {rateText} đúng mô tả
        </span>
      ) : null}
    </div>
  );
}
