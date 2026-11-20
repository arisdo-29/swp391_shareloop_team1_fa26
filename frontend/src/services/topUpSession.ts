import type { PaymentOrderStatus, TopUpOrderResponse } from './paymentApi';

const STORAGE_KEY = 'shareloop:pendingTopUp';
export interface PendingTopUpSession {
  orderCode: string;
  redirectUrl?: string;
  expiresAt?: string;
}

export type VnPayReturnStatus = 'SUCCESS' | 'FAILED' | 'INVALID_SIGNATURE';
export type TopUpReturnResult =
  | 'success'
  | 'cancelled'
  | 'failed'
  | 'expired'
  | 'pending-after-failed-return'
  | 'pending';

export function savePendingTopUp(order: TopUpOrderResponse) {
  try {
    const redirectUrl = order.redirectUrl ? paymentRedirectUrl(order.redirectUrl) : null;
    const expiresAt =
      order.expiresAt && Number.isFinite(Date.parse(order.expiresAt)) ? order.expiresAt : undefined;
    sessionStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        orderCode: order.orderCode,
        ...(redirectUrl ? { redirectUrl } : {}),
        ...(expiresAt ? { expiresAt } : {}),
      }),
    );
  } catch {
    /* Storage may be blocked by the browser. */
  }
}

export function loadPendingTopUp(): PendingTopUpSession | null {
  try {
    const value = JSON.parse(
      sessionStorage.getItem(STORAGE_KEY) ?? 'null',
    ) as PendingTopUpSession | null;
    if (
      !value ||
      typeof value.orderCode !== 'string' ||
      !/^[a-zA-Z0-9-]{1,40}$/.test(value.orderCode)
    ) {
      return null;
    }
    const redirectUrl =
      typeof value.redirectUrl === 'string' ? paymentRedirectUrl(value.redirectUrl) : null;
    const expiresAt =
      typeof value.expiresAt === 'string' && Number.isFinite(Date.parse(value.expiresAt))
        ? value.expiresAt
        : undefined;
    return {
      orderCode: value.orderCode,
      ...(redirectUrl ? { redirectUrl } : {}),
      ...(expiresAt ? { expiresAt } : {}),
    };
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

export function vnpayReturnStatus(params: URLSearchParams): VnPayReturnStatus | null {
  const status = params.get('status');
  return status === 'SUCCESS' || status === 'FAILED' || status === 'INVALID_SIGNATURE'
    ? status
    : null;
}

/** Query trả về chỉ là tín hiệu từ cổng; trạng thái đơn do Backend trả về luôn được ưu tiên. */
export function resolveTopUpReturnResult(
  orderStatus: PaymentOrderStatus,
  returnStatus: VnPayReturnStatus | null,
): TopUpReturnResult {
  if (orderStatus === 'SUCCESS') return 'success';
  if (orderStatus === 'EXPIRED') return 'expired';
  if (orderStatus === 'FAILED') return returnStatus === 'FAILED' ? 'cancelled' : 'failed';
  if (returnStatus === 'FAILED') return 'pending-after-failed-return';
  return 'pending';
}
