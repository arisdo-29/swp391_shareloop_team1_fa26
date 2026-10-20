import type { TopUpOrderResponse } from './paymentApi';

const STORAGE_KEY = 'shareloop:pendingTopUp';
export interface PendingTopUpSession {
  orderCode: string;
}

export function savePendingTopUp(order: TopUpOrderResponse) {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ orderCode: order.orderCode }));
  } catch {
    /* Storage may be blocked by the browser. */
  }
}

export function loadPendingTopUp(): PendingTopUpSession | null {
  try {
    const value = JSON.parse(
      sessionStorage.getItem(STORAGE_KEY) ?? 'null',
    ) as PendingTopUpSession | null;
    return value &&
      typeof value.orderCode === 'string' &&
      /^[a-zA-Z0-9-]{1,40}$/.test(value.orderCode)
      ? { orderCode: value.orderCode }
      : null;
  } catch {
    return null;
  }
}

export function clearPendingTopUp() {
  try {
    sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    /* Storage may be blocked. */
  }
}

export function paymentRedirectUrl(redirectUrl: string): string | null {
  const value = redirectUrl.trim();
  if (!value) return null;
  try {
    const url = new URL(value, window.location.origin);
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : null;
  } catch {
    return null;
  }
}

export function returnOrderCode(params: URLSearchParams): string | null {
  return params.get('orderCode') ?? params.get('vnp_TxnRef');
}
