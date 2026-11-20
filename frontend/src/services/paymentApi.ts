import { http, tokenStorage, type ApiError } from './http';
import { userApi } from './userApi';
import type { WalletResponse } from './walletApi';

// Contract: docs/api/payment.md
export const paymentApiEndpoints = {
  createTopUpOrder: '/api/v1/wallet/topup-orders',
  mockConfirm: (orderCode: string) =>
    `/api/v1/wallet/topup-orders/${encodeURIComponent(orderCode)}/mock-confirm`,
} as const;

export type PaymentOrderStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'EXPIRED';
export type PaymentProvider = 'MOCK' | 'VNPAY';

export interface TopUpOrderDetailResponse extends Omit<TopUpOrderResponse, 'redirectUrl'> {
  userId: number;
  vndPerCredit: number;
  createdAt: string;
  expiresAt: string;
  submittedAt: string | null;
  reviewedBy: number | null;
  reviewedAt: string | null;
  reviewNote: string | null;
  // Chỉ có trong API của Admin (docs/api/payment.md), API của người dùng để null.
  userName?: string | null;
  userEmail?: string | null;
  reviewedByName?: string | null;
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
  /** Hạn thanh toán do Backend tính (created_at + payment_order_expire_minutes); FE chỉ đếm ngược, không tự tính. */
  expiresAt?: string | null;
}

export interface TopUpConfirmResponse {
  orderCode: string;
  status: PaymentOrderStatus;
  credits: number;
  wallet: WalletResponse;
}

export const paymentApi = {
  async getOrder(orderCode: string): Promise<TopUpOrderDetailResponse> {
    try {
      const { data } = await http.get<TopUpOrderDetailResponse>(
        `${paymentApiEndpoints.createTopUpOrder}/${encodeURIComponent(orderCode)}`,
      );
      return data;
    } catch (caught) {
      const error = caught as ApiError;
      if (error.status === 401 && tokenStorage.get()) {
        // Không tin 401 của riêng API đơn thanh toán để kết thúc phiên. /me là nguồn xác
        // thực phiên; interceptor sẽ logout nếu chính /me cũng xác nhận JWT không hợp lệ.
        await userApi.getMe().catch(() => undefined);
      }
      throw caught;
    }
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
