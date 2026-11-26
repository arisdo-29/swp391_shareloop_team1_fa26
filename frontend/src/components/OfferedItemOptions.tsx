import { useState } from 'react';
import type { Item } from '../types/domain';
import { formatReferencePrice } from '../utils/referencePrice';
import { Button, Icon, ItemImage } from './ui';

interface OfferedItemOptionsProps {
  // Chỉ cần vài trường để vẽ thẻ, nên trang chat có thể truyền món dựng từ ItemResponse.
  items: Array<Pick<Item, 'id' | 'title' | 'images' | 'district' | 'referencePrice'>>;
  similarPriceIds?: ReadonlySet<string>;
  pageSize?: number;
  selectedItemId: string;
  onSelect: (itemId: string) => void;
}

// Danh sách thẻ để chọn món của mình mang đi đổi; dùng chung cho form gửi yêu cầu và đề nghị đổi món trong chat.
export function OfferedItemOptions({
  items,
  similarPriceIds,
  pageSize,
  selectedItemId,
  onSelect,
}: OfferedItemOptionsProps) {
  const [page, setPage] = useState(0);
  const totalPages = pageSize ? Math.max(1, Math.ceil(items.length / pageSize)) : 1;
  const currentPage = Math.min(page, totalPages - 1);
  const visibleItems = pageSize
    ? items.slice(currentPage * pageSize, (currentPage + 1) * pageSize)
    : items;
  const selectedItemOnAnotherPage =
    totalPages > 1 && !visibleItems.some((entry) => entry.id === selectedItemId)
      ? items.find((entry) => entry.id === selectedItemId)
      : undefined;

  return (
    <>
      {visibleItems.map((entry) => {
        const selected = selectedItemId === entry.id;
        const similar = similarPriceIds?.has(entry.id);
        let cardStyle = 'border-border hover:border-primary/40';
        if (similar)
          cardStyle = 'border-warning bg-warning-soft hover:border-warning hover:shadow-sm';
        else if (selected) cardStyle = 'border-primary bg-primary-faint';

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
              className="h-11 w-14 shrink-0 rounded-md object-cover"
            />
            <span className="min-w-0 max-w-full flex-1 overflow-hidden">
              <strong className="block whitespace-normal break-words text-sm leading-5">
                {entry.title}
              </strong>
              <span className="block break-words text-xs text-text-muted">{entry.district}</span>
              {similarPriceIds ? (
                <span className="block text-xs text-text-secondary">
                  {formatReferencePrice(entry.referencePrice)}
                </span>
              ) : null}
              {similar ? (
                <span className="mt-1 inline-block rounded-sm bg-surface px-2 py-0.5 text-xs font-semibold text-warning">
                  Giá tương đương
                </span>
              ) : null}
            </span>
            {similar && selected ? (
              <Icon name="completed" className="h-5 w-5 shrink-0 text-primary" weight="fill" />
            ) : null}
          </button>
        );
      })}
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
