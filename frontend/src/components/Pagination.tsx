import { PAGE_SIZE_OPTIONS, pageSlots } from '../utils/pagination';
import { Button } from './ui';

/**
 * Thanh phân trang dùng chung. `page` đếm từ 0 như API.
 * Có `onSizeChange` thì hiện thêm ô chọn số dòng mỗi trang.
 */
export function Pagination({
  page,
  size,
  totalElements,
  disabled = false,
  onPageChange,
  onSizeChange,
}: {
  page: number;
  size: number;
  totalElements: number;
  disabled?: boolean;
  onPageChange: (page: number) => void;
  onSizeChange?: (size: number) => void;
}) {
  // size <= 0 hoặc tổng âm là dữ liệu lỗi; coi như không có kết quả để tránh chia cho 0.
  const safeSize = size > 0 ? size : 0;
  const safeTotal = totalElements > 0 && safeSize > 0 ? totalElements : 0;
  if (safeTotal === 0) return null;

  const totalPages = Math.ceil(safeTotal / safeSize);
  const currentPage = Math.min(Math.max(page, 0), totalPages - 1);
  const start = currentPage * safeSize + 1;
  const end = Math.min(safeTotal, (currentPage + 1) * safeSize);
  const isFirst = currentPage === 0;
  const isLast = currentPage === totalPages - 1;

  return (
    <nav
      aria-label="Phân trang"
      className="mt-4 flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between"
    >
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
        <p className="text-sm text-text-muted" aria-live="polite">
          Hiển thị {start}–{end} trên {safeTotal} kết quả
        </p>
        {onSizeChange ? (
          <label className="flex items-center gap-2 text-sm text-text-muted">
            Mỗi trang
            <select
              className="min-h-9 rounded-md border border-border bg-white px-2 text-sm text-text-primary focus:border-primary focus:outline-none disabled:opacity-40"
              value={safeSize}
              disabled={disabled}
              onChange={(event) => onSizeChange(Number(event.target.value))}
            >
              {/* Giữ cả cỡ đang dùng nếu nó không nằm trong danh sách, để ô chọn không hiện sai. */}
              {PAGE_SIZE_OPTIONS.includes(safeSize as (typeof PAGE_SIZE_OPTIONS)[number]) ? null : (
                <option value={safeSize}>{safeSize}</option>
              )}
              {PAGE_SIZE_OPTIONS.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </label>
        ) : null}
      </div>

      {totalPages > 1 ? (
        <div className="flex flex-wrap items-center gap-1.5">
          <Button
            variant="outline"
            size="sm"
            aria-label="Trang đầu"
            disabled={disabled || isFirst}
            onClick={() => onPageChange(0)}
          >
            «
          </Button>
          <Button
            variant="outline"
            size="sm"
            disabled={disabled || isFirst}
            onClick={() => onPageChange(currentPage - 1)}
          >
            Trang trước
          </Button>
          {pageSlots(currentPage, totalPages).map((slot) =>
            typeof slot === 'number' ? (
              <Button
                key={slot}
                variant={slot === currentPage ? 'primary' : 'outline'}
                size="sm"
                className="min-w-11"
                aria-label={`Trang ${slot + 1}`}
                aria-current={slot === currentPage ? 'page' : undefined}
                disabled={disabled}
                onClick={() => {
                  if (slot !== currentPage) onPageChange(slot);
                }}
              >
                {slot + 1}
              </Button>
            ) : (
              <span key={slot} aria-hidden className="px-1 text-sm text-text-muted">
                …
              </span>
            ),
          )}
          <Button
            variant="outline"
            size="sm"
            disabled={disabled || isLast}
            onClick={() => onPageChange(currentPage + 1)}
          >
            Trang sau
          </Button>
          <Button
            variant="outline"
            size="sm"
            aria-label="Trang cuối"
            disabled={disabled || isLast}
            onClick={() => onPageChange(totalPages - 1)}
          >
            »
          </Button>
        </div>
      ) : null}
    </nav>
  );
}
