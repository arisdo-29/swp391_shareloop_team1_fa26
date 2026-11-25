import { http, tokenStorage, type ApiError } from './http';
import { userApi } from './userApi';
import type { WalletResponse } from './walletApi';

// Contract: docs/api/payment.md
export const paymentApiEndpoints = {
  createTopUpOrder: '/api/v1/wallet/topup-orders',
  mockConfirm: (orderCode: string) =>
    `/api/v1/wallet/topup-orders/${encodeURIComponent(orderCode)}/mock-confirm`,
  cancel: (orderCode: string) =>
    `/api/v1/wallet/topup-orders/${encodeURIComponent(orderCode)}/cancel`,
} as const;

export type PaymentOrderStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'EXPIRED' | 'CANCELLED';

/** Mã lỗi khi tạo đơn mà còn đơn đang chờ thanh toán; details có orderCode, amountVnd, credits, expiresAt. */
export const TOPUP_PENDING_EXISTS = 'WALLET_TOPUP_PENDING_EXISTS';
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
  /** Lý do đơn FAILED / CANCELLED (tự hủy, hủy tại VNPay, mã lỗi VNPay); null với trạng thái khác. */
  failureReason?: string | null;
  /** URL thanh toán đã dựng lúc tạo đơn; chỉ có khi đơn còn PENDING trong hạn. "Tiếp tục thanh toán" mở URL này. */
  paymentUrl?: string | null;
}

export interface CreateTopUpOrderRequest {
  amountVnd: number;
  /** true: còn đơn đang chờ thì Backend hủy đơn đó rồi tạo đơn mới ("Hủy đơn cũ & tạo đơn mới"). */
  replacePending?: boolean;
}

export interface TopUpOrderListParams {
  /** PENDING: chỉ đơn còn trong hạn thanh toán; bỏ trống: toàn bộ lịch sử. */
  status?: PaymentOrderStatus;
  page?: number;
  size?: number;
}

export interface TopUpOrderPage {
  items: TopUpOrderDetailResponse[];
  page: number;
  size: number;
  totalElements: number;
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
  /** Chỉ có khi đọc lại đơn (GET, hủy đơn); xem TopUpOrderDetailResponse.failureReason. */
  failureReason?: string | null;
  /** Giờ server lúc trả response (ISO); FE lấy serverTime - giờ máy để bù lệch đồng hồ khi đếm ngược. */
  serverTime?: string | null;
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
  /** Đơn nạp của chính mình, mới nhất trước (GET /api/v1/wallet/topup-orders). */
  async listOrders(params: TopUpOrderListParams = {}): Promise<TopUpOrderPage> {
    const { data } = await http.get<TopUpOrderPage>(paymentApiEndpoints.createTopUpOrder, { params });
    return data;
  },
  /** Hủy đơn đang chờ thanh toán; gọi lại không đổi gì. Luôn trả trạng thái hiện tại của đơn. */
  async cancelOrder(orderCode: string): Promise<TopUpOrderDetailResponse> {
    const { data } = await http.post<TopUpOrderDetailResponse>(paymentApiEndpoints.cancel(orderCode));
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
