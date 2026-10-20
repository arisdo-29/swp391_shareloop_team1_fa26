import { http } from './http';
import type { WalletResponse } from './walletApi';

// Contract: docs/api/payment.md
export const paymentApiEndpoints = {
  createTopUpOrder: '/api/v1/wallet/topup-orders',
  mockConfirm: (orderCode: string) =>
    `/api/v1/wallet/topup-orders/${encodeURIComponent(orderCode)}/mock-confirm`,
} as const;

export type PaymentOrderStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'EXPIRED';
export type PaymentProvider = 'MOCK' | 'VNPAY' | 'MANUAL';

export interface TopUpOrderDetailResponse extends Omit<TopUpOrderResponse, 'redirectUrl'> {
  userId: number;
  vndPerCredit: number;
  createdAt: string;
  expiresAt: string;
  submittedAt: string | null;
  reviewedBy: number | null;
  reviewedAt: string | null;
  reviewNote: string | null;
}

export interface CreateTopUpOrderRequest {
  amountVnd: number;
}

export interface TopUpOrderResponse {
  orderCode: string;
  amountVnd: number;
  credits: number;
  status: PaymentOrderStatus;
  provider: PaymentProvider;
  redirectUrl: string;
}

export interface TopUpConfirmResponse {
  orderCode: string;
  status: PaymentOrderStatus;
  credits: number;
  wallet: WalletResponse;
}

export const paymentApi = {
  async getOrder(orderCode: string): Promise<TopUpOrderDetailResponse> {
    const { data } = await http.get<TopUpOrderDetailResponse>(
      `${paymentApiEndpoints.createTopUpOrder}/${encodeURIComponent(orderCode)}`,
    );
    return data;
  },
  async markPaid(orderCode: string): Promise<TopUpOrderDetailResponse> {
    const { data } = await http.post<TopUpOrderDetailResponse>(
      `${paymentApiEndpoints.createTopUpOrder}/${encodeURIComponent(orderCode)}/mark-paid`,
    );
    return data;
  },
  async createTopUpOrder(input: CreateTopUpOrderRequest): Promise<TopUpOrderResponse> {
    const { data } = await http.post<TopUpOrderResponse>(
      paymentApiEndpoints.createTopUpOrder,
      input,
    );
    return data;
  },
  // Chỉ hoạt động khi Backend bật app.payment.mock.enabled; nếu tắt, endpoint trả 404.
  async mockConfirm(orderCode: string): Promise<TopUpConfirmResponse> {
    const { data } = await http.post<TopUpConfirmResponse>(
      paymentApiEndpoints.mockConfirm(orderCode),
    );
    return data;
  },
};
