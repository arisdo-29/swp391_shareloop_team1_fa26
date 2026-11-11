import { useCallback, useEffect, useRef, useState } from 'react';
import { Icon } from './ui';
import { subscribeToasts, type ToastMessage, type ToastTone } from './toast';

const AUTO_CLOSE_MS = 3_800;

const toneClass: Record<ToastTone, string> = {
  success: 'border-success/25 bg-surface text-text-primary',
  error: 'border-error/25 bg-surface text-text-primary',
  warning: 'border-warning/25 bg-surface text-text-primary',
};

const iconClass: Record<ToastTone, string> = {
  success: 'bg-success-soft text-success',
  error: 'bg-error-soft text-error',
  warning: 'bg-warning-soft text-warning',
};

export function ToastHost() {
  const [toasts, setToasts] = useState<ToastMessage[]>([]);
  const timers = useRef(new Map<number, number>());

  const dismiss = useCallback((id: number) => {
    const timer = timers.current.get(id);
    if (timer !== undefined) window.clearTimeout(timer);
    timers.current.delete(id);
    setToasts((current) => current.filter((entry) => entry.id !== id));
  }, []);

  useEffect(() => {
    const unsubscribe = subscribeToasts((nextToast) => {
      setToasts((current) => [...current.slice(-3), nextToast]);
      const timer = window.setTimeout(() => dismiss(nextToast.id), AUTO_CLOSE_MS);
      timers.current.set(nextToast.id, timer);
    });
    const activeTimers = timers.current;
    return () => {
      unsubscribe();
      activeTimers.forEach((timer) => window.clearTimeout(timer));
      activeTimers.clear();
    };
  }, [dismiss]);

  return (
    <div
      aria-live="polite"
      aria-label="Thông báo"
      className="pointer-events-none fixed right-3 top-[76px] z-[60] flex w-[calc(100%-1.5rem)] max-w-sm flex-col gap-2 sm:right-4 sm:top-[84px] sm:w-full"
    >
      {toasts.map((entry) => (
        <div
          key={entry.id}
          role={entry.tone === 'error' ? 'alert' : 'status'}
          className={`pointer-events-auto flex items-start gap-3 rounded-lg border p-3 shadow-md ${toneClass[entry.tone]}`}
        >
          <span
            className={`mt-0.5 grid size-7 shrink-0 place-items-center rounded-full ${iconClass[entry.tone]}`}
          >
            <Icon
              name={entry.tone === 'success' ? 'check' : 'warning'}
              className="size-4"
              weight="bold"
            />
          </span>
          <p className="min-w-0 flex-1 break-words text-sm leading-5">{entry.message}</p>
          <button
            type="button"
            aria-label="Đóng thông báo"
            onClick={() => dismiss(entry.id)}
            className="grid size-8 shrink-0 place-items-center rounded-md text-text-muted transition hover:bg-surface-low hover:text-text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            <Icon name="close" className="size-4" />
          </button>
        </div>
      ))}
    </div>
  );
}
