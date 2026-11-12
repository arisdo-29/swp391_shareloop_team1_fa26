import { isSilentSessionExpiryMessage } from '../utils/sessionMessages';

export type ToastTone = 'success' | 'error' | 'warning';

export interface ToastMessage {
  id: number;
  message: string;
  tone: ToastTone;
}

type ToastListener = (toast: ToastMessage) => void;

const listeners = new Set<ToastListener>();
const recentToasts = new Map<string, number>();
let nextToastId = 1;

function emitToast(tone: ToastTone, message: string) {
  const normalizedMessage = message.trim();
  if (!normalizedMessage || isSilentSessionExpiryMessage(normalizedMessage)) return;

  const signature = `${tone}:${normalizedMessage}`;
  const now = Date.now();
  const lastShownAt = recentToasts.get(signature) ?? 0;
  if (now - lastShownAt < 1_500) return;
  recentToasts.set(signature, now);

  for (const [key, shownAt] of recentToasts) {
    if (now - shownAt > 10_000) recentToasts.delete(key);
  }

  const toast = { id: nextToastId++, message: normalizedMessage, tone };
  listeners.forEach((listener) => listener(toast));
}

export const toast = {
  success: (message: string) => emitToast('success', message),
  error: (message: string) => emitToast('error', message),
  warning: (message: string) => emitToast('warning', message),
};

export function subscribeToasts(listener: ToastListener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}
