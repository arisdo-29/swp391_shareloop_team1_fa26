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
  if (upper === 'DIAMOND') return 'bg-cyan-50 text-cyan-700 ring-cyan-600/20';
  if (upper === 'GOLD') return 'bg-amber-50 text-amber-800 ring-amber-600/20';
  if (upper === 'SILVER') return 'bg-slate-100 text-slate-700 ring-slate-500/20';
  return 'bg-orange-50 text-orange-800 ring-orange-600/20'; // BRONZE
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
            className={`inline-flex items-center rounded px-1.5 py-0.5 text-[11px] font-semibold ring-1 ring-inset ${rankBadgeColor(
              rank,
            )}`}
          >
            {rankLabel}
          </span>
        ) : null}
        {starsText !== '—' ? (
          <span className="inline-flex items-center gap-0.5 font-medium text-amber-600" title="Sao uy tín">
            <span aria-hidden="true">★</span>
            <span>{starsText}/5</span>
          </span>
        ) : null}
        {rateText !== '—' ? (
          <span className="text-[11px] text-text-muted" title="Tỉ lệ đúng mô tả">
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
          className={`inline-flex items-center rounded px-2 py-0.5 text-xs font-semibold ring-1 ring-inset ${rankBadgeColor(
            rank,
          )}`}
        >
          Hạng {rankLabel}
        </span>
      ) : null}
      {starsText !== '—' ? (
        <span className="inline-flex items-center gap-1 font-semibold text-amber-600" title="Sao uy tín">
          <span aria-hidden="true" className="text-sm leading-none">★</span>
          <span>{starsText}/5 sao</span>
        </span>
      ) : null}
      {rateText !== '—' ? (
        <span
          className="inline-flex items-center rounded bg-emerald-50 px-2 py-0.5 font-medium text-emerald-700 ring-1 ring-inset ring-emerald-600/20"
          title="Tỉ lệ đúng mô tả"
        >
          {rateText} đúng mô tả
        </span>
      ) : null}
    </div>
  );
}
