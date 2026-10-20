import { http } from './http';
import type { PageResponse } from './itemApi';
import type { WalletTransactionResponse } from './walletApi';

export interface FinanceGroup { type: string; amount: number; entryCount: number }
export interface FinanceSummary {
  from: string | null;
  to: string | null;
  cashOrderCount: number;
  cashAmountVnd: number;
  creditInTotal: number;
  creditInByType: FinanceGroup[];
  creditOutTotal: number;
  creditOutByType: FinanceGroup[];
  reconciliation: { cashCredits: number; topUpCredits: number; difference: number; matched: boolean };
}

export interface AdminDateParams { from?: string; to?: string }

export const adminFinanceApi = {
  async report(params: AdminDateParams) {
    const { data } = await http.get<FinanceSummary>('/api/v1/admin/finance/report', { params });
    return data;
  },
  async userHistory(userId: number, params: AdminDateParams & { page: number; size: number }) {
    const { data } = await http.get<PageResponse<WalletTransactionResponse>>(`/api/v1/admin/finance/users/${userId}/history`, { params });
    return data;
  },
  async setLocked(userId: number, locked: boolean, reason: string) {
    await http.post(`/api/v1/admin/users/${userId}/${locked ? 'lock' : 'unlock'}`, { reason });
  },
};
