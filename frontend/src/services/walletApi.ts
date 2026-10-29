import { http } from './http';
import type { ItemStatus, PageResponse } from './itemApi';

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

// Số dòng theo tab, BE đếm trên cả khoảng thời gian đang lọc (không theo trang).
export interface WalletHistoryCounts {
  all: number;
  topUp: number;
  spend: number;
}

export type WalletTransactionPage = PageResponse<WalletTransactionResponse> & {
  counts: WalletHistoryCounts;
};

export interface WalletTransactionParams {
  from?: string; // ISO-8601 UTC, gồm
  to?: string; // ISO-8601 UTC, không gồm
  page?: number;
  size?: number; // 1..100
}

// Contract: docs/api/listingfee.md (GET /api/v1/listing-fees/held)
export interface HeldFeeItem {
  itemId: number;
  title: string;
  offerType: 'GIVE' | 'SWAP';
  status: ItemStatus;
  pendingFee: number;
  feeType: 'POST' | 'EDIT'; // POST = phí đăng, EDIT = phí sửa
}

export const walletApi = {
  async listHeldFees() {
    const { data } = await http.get<HeldFeeItem[]>('/api/v1/listing-fees/held');
    return data;
  },
  async get() {
    const { data } = await http.get<WalletResponse>('/api/v1/wallet');
    return data;
  },
  async listTransactions(params: WalletTransactionParams = {}): Promise<WalletTransactionPage> {
    const { data } = await http.get<WalletTransactionPage>('/api/v1/wallet/transactions', {
      params,
    });
    return data;
  },
  /** Tải hết các trang lịch sử trong khoảng thời gian (mỗi trang 100 dòng, tối đa maxPages trang). */
  async listAllTransactions(
    params: Omit<WalletTransactionParams, 'page' | 'size'> = {},
    maxPages = 20,
  ): Promise<WalletTransactionResponse[]> {
    const size = 100;
    const entries: WalletTransactionResponse[] = [];
    for (let page = 0; page < maxPages; page += 1) {
      const response = await walletApi.listTransactions({ ...params, page, size });
      entries.push(...response.items);
      if (entries.length >= response.totalElements || response.items.length < size) break;
    }
    return entries;
  },
};
