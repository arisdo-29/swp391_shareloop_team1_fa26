import { useEffect, useId, useRef, type ReactNode } from 'react';
import { Icon } from './ui';

type DialogSize = 'sm' | 'md' | 'lg' | 'xl';

const sizeClass: Record<DialogSize, string> = {
  sm: 'sm:max-w-md',
  md: 'sm:max-w-lg',
  lg: 'sm:max-w-xl',
  xl: 'sm:max-w-3xl',
};

/**
 * Chỉ vẽ khung hộp thoại (lớp phủ + tấm trắng, role="dialog"), nội dung bên trong do nơi gọi tự dựng.
 * Dùng khi trang đã có tiêu đề/nút riêng và chỉ cần khung thống nhất. Trên mobile hộp thoại trượt từ đáy (bottom-sheet).
 * Truyền onClose thì nhấn Esc hoặc bấm nền sẽ đóng (trừ khi busy); không truyền thì giữ hành vi đóng sẵn của trang.
 */
export function DialogFrame({
  labelledBy,
  size = 'md',
  busy = false,
  onClose,
  className = '',
  children,
}: {
  labelledBy?: string;
  size?: DialogSize;
  busy?: boolean;
  onClose?: () => void;
  className?: string;
  children: ReactNode;
}) {
  const frameRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const previousFocus = document.activeElement as HTMLElement | null;
    const frame = frameRef.current;
    frame?.focus();
    const trapFocus = (event: KeyboardEvent) => {
      if (event.key !== 'Tab' || !frame) return;
      const dialogs = document.querySelectorAll('[role="dialog"]');
      if (dialogs[dialogs.length - 1] !== frame) return;
      const focusable = Array.from(
        frame.querySelectorAll<HTMLElement>(
          'button:not(:disabled), a[href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex="0"]',
        ),
      ).filter((element) => !element.hidden && element.getAttribute('aria-hidden') !== 'true');
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (!first) {
        event.preventDefault();
        frame.focus();
        return;
      }
      if (
        event.shiftKey &&
        (document.activeElement === first || document.activeElement === frame)
      ) {
        event.preventDefault();
        last.focus();
      } else if (
        !event.shiftKey &&
        (document.activeElement === last || document.activeElement === frame)
      ) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener('keydown', trapFocus);
    return () => {
      document.removeEventListener('keydown', trapFocus);
      if (previousFocus?.isConnected) previousFocus.focus();
    };
  }, []);

  useEffect(() => {
    if (!onClose) return;
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !busy) onClose();
    };
    window.addEventListener('keydown', closeOnEscape);
    return () => window.removeEventListener('keydown', closeOnEscape);
  }, [busy, onClose]);

  return (
    <div
      className="fixed inset-0 z-50 grid place-items-end overflow-hidden bg-black/45 sm:place-items-center sm:p-[clamp(1rem,2vw,2rem)]"
      onMouseDown={() => {
        if (onClose && !busy) onClose();
      }}
    >
      <div
        ref={frameRef}
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-labelledby={labelledBy}
        className={`max-h-[92dvh] w-full min-w-0 overscroll-contain overflow-y-auto rounded-t-xl border border-border bg-surface p-5 shadow-lg sm:max-h-[calc(100dvh-2rem)] sm:rounded-xl sm:p-6 ${sizeClass[size]} ${className}`}
        onMouseDown={(event) => event.stopPropagation()}
      >
        {children}
      </div>
    </div>
  );
}

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

  return (
    <DialogFrame labelledBy={titleId} size={wide ? 'xl' : 'md'} busy={busy} onClose={onClose}>
      <div className="flex items-start justify-between gap-4">
        <h2 id={titleId} className="text-lg font-bold text-text-primary">
          {title}
        </h2>
        <button
          type="button"
          aria-label="Đóng"
          disabled={busy}
          onClick={onClose}
          className="inline-flex size-11 shrink-0 items-center justify-center rounded-md text-text-muted hover:bg-surface-low hover:text-text-primary disabled:cursor-not-allowed disabled:opacity-40"
        >
          <Icon name="close" className="size-5" />
        </button>
      </div>
      <div className="mt-4">{children}</div>
    </DialogFrame>
  );
}
