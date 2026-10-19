import type { TopUpOrderResponse } from './paymentApi';

const STORAGE_KEY = 'shareloop:pendingTopUp';

export interface PendingTopUpSession {
  order: TopUpOrderResponse;
  createdAt: string;
  walletBalanceBefore: number | null;
}

function isTopUpOrder(value: unknown): value is TopUpOrderResponse {
  if (!value || typeof value !== 'object') return false;
  const order = value as Partial<TopUpOrderResponse>;
  return typeof order.orderCode === 'string' && order.orderCode.length > 0
    && Number.isSafeInteger(order.amountVnd) && Number(order.amountVnd) > 0
    && Number.isSafeInteger(order.credits) && Number(order.credits) > 0
    && ['PENDING', 'SUCCESS', 'FAILED', 'EXPIRED'].includes(String(order.status))
    && ['MOCK', 'VNPAY'].includes(String(order.provider))
    && typeof order.redirectUrl === 'string';
}

export function savePendingTopUp(order: TopUpOrderResponse, walletBalanceBefore: number | null) {
  const value: PendingTopUpSession = {
    order,
    createdAt: new Date().toISOString(),
    walletBalanceBefore,
  };
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(value));
  } catch {
    // Trình duyệt có thể chặn storage; vẫn cho phép chuyển đến cổng thanh toán.
  }
}

export function loadPendingTopUp(): PendingTopUpSession | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const value = JSON.parse(raw) as Partial<PendingTopUpSession>;
    if (!isTopUpOrder(value.order) || typeof value.createdAt !== 'string') return null;
    if (value.walletBalanceBefore !== null && !Number.isSafeInteger(value.walletBalanceBefore)) return null;
    return value as PendingTopUpSession;
  } catch {
    return null;
  }
}

export function clearPendingTopUp() {
  try {
    sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    // Không có dữ liệu số dư nào được lưu tại đây; bỏ qua khi storage bị chặn.
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
