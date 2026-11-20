import { formatTrustStars } from '../utils/reputation';
import { Icon } from './ui';

const MAX_STARS = 5;

export function StarRating({
  value,
  className = '',
}: {
  value?: number | null;
  className?: string;
}) {
  if (value == null || !Number.isFinite(value)) return null;

  const stars = Number(formatTrustStars(Math.min(MAX_STARS, Math.max(0, value))));
  const label = `${stars.toLocaleString('vi-VN')} trên ${MAX_STARS} sao`;

  return (
    <span role="img" aria-label={label} title={label} className={`inline-flex gap-0.5 align-middle ${className}`}>
      {Array.from({ length: MAX_STARS }, (_, index) => {
        const fillPercent = Math.round(Math.min(1, Math.max(0, stars - index)) * 100);

        return (
          <span key={index} className="relative block size-[1em] shrink-0">
            {fillPercent > 0 ? (
              <span className="absolute inset-y-0 left-0 overflow-hidden" style={{ width: `${fillPercent}%` }}>
                <Icon name="star" weight="fill" className="size-[1em] max-w-none text-highlight" />
              </span>
            ) : null}
            <Icon name="star" className="relative size-[1em] text-warning" />
          </span>
        );
      })}
    </span>
  );
}
