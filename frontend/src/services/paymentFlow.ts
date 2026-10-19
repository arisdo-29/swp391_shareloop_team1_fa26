import type { ApiError } from './http';
import { paymentApi, type TopUpConfirmResponse, type TopUpOrderResponse } from './paymentApi';
import { paymentRedirectUrl } from './topUpSession';

export const demoPaymentEnabled = import.meta.env.VITE_DEMO_PAYMENT === 'true';
export const demoPaymentUnavailableMessage =
  'Chức năng thanh toán này chưa khả dụng. Vui lòng liên hệ quản trị viên.';

const confirmationAttempts = new Map<string, Promise<TopUpConfirmResponse>>();

export function shouldAutoConfirmPayment(order: TopUpOrderResponse) {
  return demoPaymentEnabled && order.provider === 'MOCK';
}

export function demoPaymentErrorMessage(error: unknown) {
  const apiError = error as Partial<ApiError>;
  if ([403, 404, 500].includes(apiError.status ?? 0)) return demoPaymentUnavailableMessage;
  return 'Không thể hoàn tất thanh toán. Vui lòng thử lại.';
}

export function confirmDemoPayment(order: TopUpOrderResponse) {
  const existing = confirmationAttempts.get(order.orderCode);
  if (existing) return existing;

  const confirmation = paymentApi.mockConfirm(order.orderCode);
  confirmationAttempts.set(order.orderCode, confirmation);
  return confirmation;
}

export async function startPayment(order: TopUpOrderResponse): Promise<TopUpConfirmResponse | null> {
  if (shouldAutoConfirmPayment(order)) {
    return confirmDemoPayment(order);
  }

  const redirectUrl = paymentRedirectUrl(order.redirectUrl);
  if (!redirectUrl) {
    throw new Error(
      'Không thể mở cổng thanh toán vì địa chỉ chuyển hướng không hợp lệ. Vui lòng thử lại sau.',
    );
  }
  window.location.assign(redirectUrl);
  return null;
}
