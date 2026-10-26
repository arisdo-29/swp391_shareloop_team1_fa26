import type { ApiError } from './http';
import { paymentApi, type TopUpConfirmResponse, type TopUpOrderResponse } from './paymentApi';
import { paymentRedirectUrl } from './topUpSession';

const isNonProductionPaymentEnvironment = import.meta.env.DEV || import.meta.env.MODE === 'staging';

export const demoPaymentEnabled =
  isNonProductionPaymentEnvironment && import.meta.env.VITE_DEMO_PAYMENT === 'true';
export const demoPaymentUnavailableMessage =
  'Thanh toán thử nghiệm chưa được bật trong môi trường này.';
export const backendMockProviderMessage =
  'Cổng thanh toán chưa sẵn sàng nên chưa thể nạp Credit lúc này. Vui lòng thử lại sau hoặc liên hệ quản trị viên.';

const confirmationAttempts = new Map<string, Promise<TopUpConfirmResponse>>();

export function demoPaymentErrorMessage(error: unknown) {
  const apiError = error as Partial<ApiError>;
  if ([403, 404, 500].includes(apiError.status ?? 0)) return demoPaymentUnavailableMessage;
  return apiError.message || 'Không thể hoàn tất thanh toán. Vui lòng thử lại.';
}

export function confirmDemoPayment(order: TopUpOrderResponse) {
  if (!demoPaymentEnabled || order.provider !== 'MOCK') {
    return Promise.reject(new Error(demoPaymentUnavailableMessage));
  }
  const existing = confirmationAttempts.get(order.orderCode);
  if (existing) return existing;

  const confirmation = paymentApi.mockConfirm(order.orderCode);
  confirmationAttempts.set(order.orderCode, confirmation);
  return confirmation;
}

export async function startPayment(order: TopUpOrderResponse): Promise<void> {
  if (order.provider === 'MANUAL') {
    window.location.assign(
      '/wallet/topup/manual-checkout?orderCode=' + encodeURIComponent(order.orderCode),
    );
    return;
  }
  if (order.provider !== 'VNPAY') throw new Error(backendMockProviderMessage);

  const redirectUrl = paymentRedirectUrl(order.redirectUrl);
  if (!redirectUrl) {
    throw new Error(
      'Không thể mở cổng thanh toán vì địa chỉ chuyển hướng không hợp lệ. Vui lòng thử lại sau.',
    );
  }
  window.location.assign(redirectUrl);
}
