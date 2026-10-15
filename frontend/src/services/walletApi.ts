import { http } from './http';

export interface WalletResponse {
  creditBalance: number;
  heldCredit: number;
  available: number;
  hasToppedUp: boolean;
}

export type WalletTransactionType =
  'TOP_UP' | 'POST_FEE' | 'EDIT_FEE' | 'RENEW_FEE' | 'BOOST_FEE' | 'AI_SEARCH_FEE' | 'ADMIN_ADJUST';

export interface WalletTransactionResponse {
  id: number;
  type: WalletTransactionType;
  amount: number;
  balanceAfter: number;
  itemId?: number | null;
  paymentOrderId?: number | null;
  note?: string | null;
  createdAt: string;
}

export interface WalletTransactionParams {
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
}

export interface CreateTopUpOrderRequest {
  amountVnd: number;
}

export type TopUpOrderStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'EXPIRED';
export type TopUpProvider = 'VNPAY' | 'MOCK';

export interface TopUpOrderResponse {
  orderCode: string;
  amountVnd: number;
  credits: number;
  status: TopUpOrderStatus;
  provider: TopUpProvider;
  redirectUrl?: string | null;
}

export const walletApi = {
  async get() {
    const { data } = await http.get<WalletResponse>('/api/v1/wallet');
    return data;
  },
  async transactions(params: WalletTransactionParams = {}) {
    const { data } = await http.get<PageResponse<WalletTransactionResponse>>(
      '/api/v1/wallet/transactions',
      { params },
    );
    return data;
  },
  async createTopUpOrder(input: CreateTopUpOrderRequest) {
    const { data } = await http.post<TopUpOrderResponse>('/api/v1/wallet/topup-orders', input);
    return data;
  },
};
