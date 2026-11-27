import { useState } from 'react';
import type { Item } from '../types/domain';
import { formatReferencePrice } from '../utils/referencePrice';
import { Button, Icon, ItemImage } from './ui';

interface OfferedItemOptionsProps {
  // Chỉ cần vài trường để vẽ thẻ, nên trang chat có thể truyền món dựng từ ItemResponse.
  items: Array<Pick<Item, 'id' | 'title' | 'images' | 'district' | 'referencePrice'>>;
  similarPriceIds?: ReadonlySet<string>;
  pageSize?: number;
  compactScrollable?: boolean;
  selectedItemId: string;
  onSelect: (itemId: string) => void;
}

// Danh sách thẻ để chọn món của mình mang đi đổi; dùng chung cho form gửi yêu cầu và đề nghị đổi món trong chat.
export function OfferedItemOptions({
  items,
  similarPriceIds,
  pageSize,
  compactScrollable = false,
  selectedItemId,
  onSelect,
}: OfferedItemOptionsProps) {
  const [page, setPage] = useState(0);
  const effectivePageSize = compactScrollable ? undefined : pageSize;
  const totalPages = effectivePageSize
    ? Math.max(1, Math.ceil(items.length / effectivePageSize))
    : 1;
  const currentPage = Math.min(page, totalPages - 1);
  const visibleItems = effectivePageSize
    ? items.slice(currentPage * effectivePageSize, (currentPage + 1) * effectivePageSize)
    : items;
  const selectedItemOnAnotherPage =
    totalPages > 1 && !visibleItems.some((entry) => entry.id === selectedItemId)
      ? items.find((entry) => entry.id === selectedItemId)
      : undefined;

  const itemCards = visibleItems.map((entry) => {
    const selected = selectedItemId === entry.id;
    const similar = similarPriceIds?.has(entry.id);
    let cardStyle = 'border-border hover:border-primary/40';
    if (compactScrollable) {
      cardStyle = selected
        ? 'border-exchange bg-detail-selected'
        : 'border-detail-border bg-white hover:border-exchange/50 hover:bg-detail-note';
    } else if (similar) {
      cardStyle = 'border-warning bg-warning-soft hover:border-warning hover:shadow-sm';
    } else if (selected) {
      cardStyle = 'border-primary bg-primary-faint';
    }

    return (
      <button
        key={entry.id}
        type="button"
        aria-pressed={selected}
        onClick={() => onSelect(entry.id)}
        className={`box-border flex w-full min-w-0 max-w-full items-center gap-3 overflow-hidden rounded-md border p-2.5 text-left transition focus-visible:outline-primary ${cardStyle}`}
      >
        <ItemImage
          src={entry.images[0]}
          alt={entry.title}
          className={`${compactScrollable ? 'h-14 w-14' : 'h-11 w-14'} shrink-0 rounded-md object-cover`}
        />
        <span className="min-w-0 max-w-full flex-1 overflow-hidden">
          <strong
            className={`block whitespace-normal break-words font-semibold leading-5 ${compactScrollable ? 'text-[15px]' : 'text-sm'}`}
          >
            {entry.title}
          </strong>
          <span className="block break-words text-xs text-text-muted">{entry.district}</span>
          {compactScrollable || similarPriceIds ? (
            <span
              className={`block text-xs font-medium ${entry.referencePrice == null ? 'text-text-muted' : 'text-primary'}`}
            >
              {formatReferencePrice(entry.referencePrice)}
            </span>
          ) : null}
          {similar ? (
            <span className="mt-1 inline-block rounded-sm bg-surface px-2 py-0.5 text-xs font-semibold text-warning">
              Giá tương đương
            </span>
          ) : null}
        </span>
        {compactScrollable ? (
          <span
            aria-hidden="true"
            className={`flex size-5 shrink-0 items-center justify-center rounded-full border ${selected ? 'border-exchange bg-exchange text-white' : 'border-detail-exchange-border bg-white'}`}
          >
            {selected ? <Icon name="check" className="size-3.5" weight="bold" /> : null}
          </span>
        ) : similar && selected ? (
          <Icon name="completed" className="h-5 w-5 shrink-0 text-primary" weight="fill" />
        ) : null}
      </button>
    );
  });

  return (
    <>
      <div
        data-testid={compactScrollable ? 'offered-item-scroll' : undefined}
        className={`space-y-2 ${
          compactScrollable
            ? 'max-h-[300px] overflow-y-auto overscroll-contain pr-1 [scrollbar-color:theme(colors.detail.exchange-border)_transparent] [scrollbar-width:thin] [&::-webkit-scrollbar-thumb]:rounded-full [&::-webkit-scrollbar-thumb]:bg-detail-exchange-border [&::-webkit-scrollbar-track]:bg-transparent [&::-webkit-scrollbar]:w-1.5'
            : ''
        }`}
      >
        {itemCards}
      </div>
      {totalPages > 1 ? (
        <nav
          aria-label="Phân trang món mang đổi"
          className="flex flex-wrap items-center justify-between gap-2"
        >
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={currentPage === 0}
            onClick={() => setPage(currentPage - 1)}
          >
            Trang trước
          </Button>
          <span aria-live="polite" className="text-xs tabular-nums text-text-secondary">
            Trang {currentPage + 1}/{totalPages}
          </span>
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={currentPage === totalPages - 1}
            onClick={() => setPage(currentPage + 1)}
          >
            Trang sau
          </Button>
        </nav>
      ) : null}
      {selectedItemOnAnotherPage ? (
        <p className="break-words text-xs text-text-secondary">
          Đã chọn “{selectedItemOnAnotherPage.title}”
        </p>
      ) : null}
    </>
  );
}
