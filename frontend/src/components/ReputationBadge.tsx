import { getRankLabel } from '../utils/reputation';
import { StarRating } from './StarRating';

export interface ReputationBadgeProps {
  trustStars?: number | null;
  rank?: string | null;
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
 * Component hiển thị thông tin uy tín người dùng: Hạng và Sao uy tín (YC-06).
 */
export function ReputationBadge({
  trustStars,
  rank,
  className = '',
  compact = false,
}: ReputationBadgeProps) {
  const rankLabel = getRankLabel(rank);
  const hasStars = trustStars != null && Number.isFinite(trustStars);

  if (!rankLabel && !hasStars) {
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
        <StarRating value={trustStars} />
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
      <StarRating value={trustStars} className="text-sm" />
    </div>
  );
}
