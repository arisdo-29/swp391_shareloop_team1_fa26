import { useState } from 'react';
import { Button } from '../../components/ui';
import { formatVnd } from '../../utils/formatting';

const LOW_PRICE = 500_000;
const MID_PRICE = 5_000_000;
const DEFAULT_PRICE_CEILING = 250_000_000;
const LOW_POSITION = 50;
const MID_POSITION = 95;
const MAX_PRICE_POSITION = 144;
const OPEN_END_POSITION = MAX_PRICE_POSITION + 1;

// Dành phần lớn chiều dài cho giá thấp để dễ chọn khoảng hẹp khi kéo.
function priceAt(position: number, ceiling: number) {
  if (position <= LOW_POSITION) return position * 10_000;
  if (position <= MID_POSITION) return LOW_PRICE + (position - LOW_POSITION) * 100_000;
  const share = (Math.min(position, MAX_PRICE_POSITION) - MID_POSITION) / (MAX_PRICE_POSITION - MID_POSITION);
  return MID_PRICE + Math.round(share * (ceiling - MID_PRICE));
}

function positionAt(price: number, ceiling: number) {
  if (price <= LOW_PRICE) return Math.round(price / 10_000);
  if (price <= MID_PRICE) return LOW_POSITION + Math.round((price - LOW_PRICE) / 100_000);
  return MID_POSITION + Math.round(((price - MID_PRICE) / (ceiling - MID_PRICE)) * (MAX_PRICE_POSITION - MID_POSITION));
}

export function BrowsePriceFilter({
  minPrice,
  maxPrice,
  onApply,
}: {
  minPrice?: number;
  maxPrice?: number;
  onApply: (min: string, max: string) => void;
}) {
  const [min, setMin] = useState(minPrice);
  const [max, setMax] = useState(maxPrice);
  const ceiling = Math.max(DEFAULT_PRICE_CEILING, minPrice ?? 0, maxPrice ?? 0);
  const hasInvalidBounds = min !== undefined && max !== undefined && min > max;
  const lowerPosition = positionAt(hasInvalidBounds ? 0 : (min ?? 0), ceiling);
  const upperPosition = max === undefined ? OPEN_END_POSITION : positionAt(max, ceiling);
  const minLabel = min === undefined ? 'Bất kỳ' : formatVnd(min);
  const maxLabel = max === undefined ? 'Bất kỳ' : formatVnd(max);
  const start = (lowerPosition / OPEN_END_POSITION) * 100;
  const end = (upperPosition / OPEN_END_POSITION) * 100;

  return (
    <form onSubmit={(event) => { event.preventDefault(); onApply(min?.toString() ?? '', max?.toString() ?? ''); }}>
      <fieldset className="min-w-0">
        <legend className="label">Khoảng giá</legend>
        <div className="grid grid-cols-2 gap-2 text-sm tabular-nums">
          <div>
            <p className="text-xs text-text-muted">Giá từ</p>
            <p className="break-words font-semibold text-text-primary">{minLabel}</p>
          </div>
          <div className="text-right">
            <p className="text-xs text-text-muted">Giá đến</p>
            <p className="break-words font-semibold text-text-primary">{maxLabel}</p>
          </div>
        </div>
        <div className="browse-price-slider">
          <div
            aria-hidden="true"
            className="browse-price-track"
            style={{ background: `linear-gradient(to right, var(--color-border) ${start}%, var(--color-primary-hover) ${start}% ${end}%, var(--color-border) ${end}%)` }}
          />
          <input
            type="range"
            className={`browse-price-handle ${lowerPosition === upperPosition && upperPosition === MAX_PRICE_POSITION ? 'browse-price-handle-front' : ''}`}
            min={0}
            max={OPEN_END_POSITION}
            value={lowerPosition}
            aria-label="Kéo giá từ"
            aria-valuetext={min === undefined ? 'Không giới hạn từ' : minLabel}
            onChange={(event) => {
              const price = Math.min(priceAt(event.currentTarget.valueAsNumber, ceiling), max ?? ceiling);
              setMin(price || undefined);
            }}
          />
          <input
            type="range"
            className="browse-price-handle"
            min={0}
            max={OPEN_END_POSITION}
            value={upperPosition}
            aria-label="Kéo giá đến"
            aria-valuetext={max === undefined ? 'Không giới hạn đến' : maxLabel}
            onChange={(event) => {
              const position = event.currentTarget.valueAsNumber;
              setMax(position === OPEN_END_POSITION ? undefined : Math.max(1, min ?? 0, priceAt(position, ceiling)));
            }}
          />
        </div>
        <Button variant="outline" type="submit" className="w-full" disabled={hasInvalidBounds}>Áp dụng giá</Button>
      </fieldset>
    </form>
  );
}
