import { Button } from './ui';

/** Thanh phân trang dùng chung cho các bảng của admin. `page` đếm từ 0 như API. */
export function Pagination({
  page,
  size,
  totalElements,
  disabled = false,
  onPageChange,
}: {
  page: number;
  size: number;
  totalElements: number;
  disabled?: boolean;
  onPageChange: (page: number) => void;
}) {
  const totalPages = Math.max(1, Math.ceil(totalElements / size));
  return (
    <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
      <p className="text-sm text-text-muted">
        {totalElements} kết quả, trang {page + 1}/{totalPages}
      </p>
      <div className="flex gap-2">
        <Button
          variant="outline"
          size="sm"
          disabled={disabled || page === 0}
          onClick={() => onPageChange(page - 1)}
        >
          Trang trước
        </Button>
        <Button
          variant="outline"
          size="sm"
          disabled={disabled || page + 1 >= totalPages}
          onClick={() => onPageChange(page + 1)}
        >
          Trang sau
        </Button>
      </div>
    </div>
  );
}
