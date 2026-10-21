import { http } from './http';
import type { PageResponse } from './itemApi';
import type { WalletTransactionResponse } from './walletApi';
import type { PaymentOrderStatus, TopUpOrderDetailResponse } from './paymentApi';

export interface FinanceGroup {
  type: string;
  amount: number;
  entryCount: number;
}
export interface FinanceSummary {
  from: string | null;
  to: string | null;
  cashOrderCount: number;
  cashAmountVnd: number;
  creditInTotal: number;
  creditInByType: FinanceGroup[];
  creditOutTotal: number;
  creditOutByType: FinanceGroup[];
  reconciliation: {
    cashCredits: number;
    topUpCredits: number;
    difference: number;
    matched: boolean;
  };
}

export interface AdminDateParams {
  from?: string;
  to?: string;
}

export const adminFinanceApi = {
  async topUpOrders(params: {
    page: number;
    size: number;
    status: PaymentOrderStatus;
    submittedOnly: boolean;
  }) {
    const { data } = await http.get<PageResponse<TopUpOrderDetailResponse>>(
      '/api/v1/admin/finance/topup-orders',
      { params },
    );
    return data;
  },
  async approveTopUp(orderCode: string, note: string) {
    const { data } = await http.post<TopUpOrderDetailResponse>(
      `/api/v1/admin/finance/topup-orders/${encodeURIComponent(orderCode)}/approve`,
      { note },
    );
    return data;
  },
  async rejectTopUp(orderCode: string, reason: string) {
    const { data } = await http.post<TopUpOrderDetailResponse>(
      `/api/v1/admin/finance/topup-orders/${encodeURIComponent(orderCode)}/reject`,
      { reason },
    );
    return data;
  },
  async report(params: AdminDateParams) {
    const { data } = await http.get<FinanceSummary>('/api/v1/admin/finance/report', { params });
    return data;
  },
  async userHistory(userId: number, params: AdminDateParams & { page: number; size: number }) {
    const { data } = await http.get<PageResponse<WalletTransactionResponse>>(
      `/api/v1/admin/finance/users/${userId}/history`,
      { params },
    );
    return data;
  },
  async setLocked(userId: number, locked: boolean, reason: string) {
    await http.post(`/api/v1/admin/users/${userId}/${locked ? 'lock' : 'unlock'}`, { reason });
  },
};
