import { useEffect, useId, type ReactNode } from 'react';
import { Icon } from './ui';

/** Hộp thoại xác nhận dùng chung cho các thao tác cần nhập lý do. Nhấn Esc hoặc bấm nền để đóng khi không bận. */
export function Dialog({
  title,
  busy = false,
  wide = false,
  onClose,
  children,
}: {
  title: string;
  busy?: boolean;
  wide?: boolean;
  onClose: () => void;
  children: ReactNode;
}) {
  const titleId = useId();

  useEffect(() => {
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !busy) onClose();
    };
    window.addEventListener('keydown', closeOnEscape);
    return () => window.removeEventListener('keydown', closeOnEscape);
  }, [busy, onClose]);

  return (
    <div
      className="fixed inset-0 z-50 grid place-items-center bg-black/45 p-4"
      onMouseDown={() => {
        if (!busy) onClose();
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className={`max-h-[90dvh] w-full ${wide ? 'max-w-3xl' : 'max-w-lg'} overflow-y-auto rounded-xl border border-border bg-white p-5 shadow-xl sm:p-6`}
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-4">
          <h2 id={titleId} className="text-lg font-bold text-text-primary">
            {title}
          </h2>
          <button
            type="button"
            aria-label="Đóng"
            disabled={busy}
            onClick={onClose}
            className="rounded-md p-1 text-text-muted hover:bg-surface-low hover:text-text-primary disabled:opacity-40"
          >
            <Icon name="close" className="size-5" />
          </button>
        </div>
        <div className="mt-4">{children}</div>
      </div>
    </div>
  );
}
