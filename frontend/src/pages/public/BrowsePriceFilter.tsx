import { useId, useState } from 'react';
import { Button } from '../../components/ui';
import { positiveIntegerParam } from '../../utils/browseFilters';
import { formatVnd } from '../../utils/formatting';

const LOW_PRICE = 500_000;
const MID_PRICE = 5_000_000;
const DEFAULT_PRICE_CEILING = 250_000_000;
const LOW_POSITION = 50;
const MID_POSITION = 95;
const MAX_PRICE_POSITION = 144;
const OPEN_END_POSITION = MAX_PRICE_POSITION + 1;
type PriceBound = 'min' | 'max';

function parsePrice(value: string) {
  const text = value.trim();
  return positiveIntegerParam(/^\d{1,3}(?:\.\d{3})+$/.test(text) ? text.replaceAll('.', '') : text);
}

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
  const [minInput, setMinInput] = useState(minPrice?.toString() ?? '');
  const [maxInput, setMaxInput] = useState(maxPrice?.toString() ?? '');
  const [ceiling, setCeiling] = useState(() => Math.max(DEFAULT_PRICE_CEILING, minPrice ?? 0, maxPrice ?? 0));
  const [editing, setEditing] = useState<PriceBound>();
  const [submitted, setSubmitted] = useState(false);
  const errorId = useId();
  const min = parsePrice(minInput);
  const max = parsePrice(maxInput);
  const invalidMin = Boolean(minInput.trim()) && min === undefined;
  const invalidMax = Boolean(maxInput.trim()) && max === undefined;
  const crossed = min !== undefined && max !== undefined && min > max;
  const error = invalidMin || invalidMax
    ? 'Giá phải là số nguyên dương.'
    : crossed ? 'Giá từ phải nhỏ hơn hoặc bằng giá đến.' : '';
  const lowerPosition = positionAt(Math.min(min ?? 0, max ?? ceiling), ceiling);
  const upperPosition = max === undefined ? OPEN_END_POSITION : positionAt(max, ceiling);
  const start = (lowerPosition / OPEN_END_POSITION) * 100;
  const end = (upperPosition / OPEN_END_POSITION) * 100;

  function updatePrice(bound: PriceBound, value: string) {
    (bound === 'min' ? setMinInput : setMaxInput)(value);
    const price = parsePrice(value);
    if (price !== undefined && price > ceiling) setCeiling(price);
  }

  return (
    <form onSubmit={(event) => {
      event.preventDefault();
      setSubmitted(true);
      if (!error) onApply(min?.toString() ?? '', max?.toString() ?? '');
    }}>
      <fieldset className="min-w-0">
        <legend className="label">Khoảng giá</legend>
        <div className="browse-price-fields">
          {([
            { bound: 'min', label: 'Giá từ', price: min, input: minInput, invalid: invalidMin },
            { bound: 'max', label: 'Giá đến', price: max, input: maxInput, invalid: invalidMax },
          ] as const).map(({ bound, label, price, input, invalid }) => (
            <label className="browse-price-field" key={bound}>
              <span className="text-xs text-text-secondary">{label}</span>
              <span className="flex min-w-0 items-center gap-1">
                <input
                  type="text"
                  inputMode="numeric"
                  aria-label={`${label} (đồng)`}
                  aria-invalid={submitted && (invalid || crossed) ? true : undefined}
                  aria-describedby={submitted && error ? errorId : undefined}
                  className="min-w-0 w-full bg-transparent text-sm font-semibold tabular-nums text-text-primary outline-none placeholder:font-normal placeholder:text-text-muted"
                  placeholder="Bất kỳ"
                  value={editing !== bound && price !== undefined ? price.toLocaleString('vi-VN') : input}
                  onFocus={() => setEditing(bound)}
                  onBlur={() => setEditing(undefined)}
                  onChange={(event) => updatePrice(bound, event.target.value)}
                />
                <span aria-hidden="true" className="text-xs text-text-muted">₫</span>
              </span>
            </label>
          ))}
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
            aria-valuetext={min === undefined ? 'Không giới hạn từ' : formatVnd(min)}
            onChange={(event) => {
              const price = Math.min(priceAt(event.currentTarget.valueAsNumber, ceiling), max ?? ceiling);
              setMinInput(price ? String(price) : '');
            }}
          />
          <input
            type="range"
            className="browse-price-handle"
            min={0}
            max={OPEN_END_POSITION}
            value={upperPosition}
            aria-label="Kéo giá đến"
            aria-valuetext={max === undefined ? 'Không giới hạn đến' : formatVnd(max)}
            onChange={(event) => {
              const position = event.currentTarget.valueAsNumber;
              setMaxInput(position === OPEN_END_POSITION ? '' : String(Math.max(1, min ?? 0, priceAt(position, ceiling))));
            }}
          />
        </div>
        {submitted && error ? <p role="alert" id={errorId} className="mb-2 text-xs text-error">{error}</p> : null}
        <Button variant="outline" type="submit" className="w-full">Áp dụng giá</Button>
      </fieldset>
    </form>
  );
}
