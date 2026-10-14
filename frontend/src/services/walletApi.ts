import { http } from './http';
import type { PageResponse } from './itemApi';

export interface WalletResponse {
  creditBalance: number;
  heldCredit: number;
  available: number;
  hasToppedUp: boolean;
}

export type CreditTxType =
  | 'TOP_UP'
  | 'POST_FEE'
  | 'EDIT_FEE'
  | 'RENEW_FEE'
  | 'BOOST_FEE'
  | 'AI_SEARCH_FEE'
  | 'ADMIN_ADJUST';

// Contract: docs/api/wallet.md (GET /api/v1/wallet/transactions)
export interface WalletTransactionResponse {
  id: number;
  type: CreditTxType;
  amount: number; // dương là cộng, âm là trừ
  balanceAfter: number;
  itemId: number | null;
  paymentOrderId: number | null;
  note: string | null;
  createdAt: string; // ISO-8601 UTC
}

export interface WalletTransactionParams {
  from?: string; // ISO-8601 UTC, gồm
  to?: string; // ISO-8601 UTC, không gồm
  page?: number;
  size?: number; // 1..100
}

export const walletApi = {
  async get(currentUserId: number) {
    const { data } = await http.get<WalletResponse>('/api/v1/wallet', {
      params: { currentUserId },
    });
    return data;
  },
  async listTransactions(params: WalletTransactionParams = {}): Promise<PageResponse<WalletTransactionResponse>> {
    const { data } = await http.get<PageResponse<WalletTransactionResponse>>('/api/v1/wallet/transactions', {
      params,
    });
    return data;
  },
};
