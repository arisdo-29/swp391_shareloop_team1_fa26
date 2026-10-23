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
  // Tiền thu theo ngày giờ Việt Nam, ngày cũ trước, chỉ gồm ngày có đơn thanh toán.
  cashByDay: DailyCash[];
}

export interface DailyCash {
  date: string; // yyyy-MM-dd
  amountVnd: number;
  orderCount: number;
}

// Contract: docs/api/admin.md mục 3.1 và 3.2
export interface AdminFinanceUserSummary {
  userId: number;
  fullName: string;
  email: string;
  phone: string;
  availableCredit: number;
  heldCredit: number;
  totalCredit: number;
  totalToppedUpCredit: number;
}

export interface AdminDateParams {
  from?: string;
  to?: string;
}

export interface FinanceCsvDownload {
  blob: Blob;
  fileName: string;
}

export function financeCsvFileName(contentDisposition: string | undefined) {
  const utf8Name = contentDisposition?.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
  if (utf8Name) {
    try {
      return decodeURIComponent(utf8Name.replace(/^"|"$/g, ''));
    } catch {
      // Dùng tên filename thông thường hoặc tên mặc định nếu header mã hóa sai.
    }
  }
  return contentDisposition?.match(/filename="?([^";]+)"?/i)?.[1] ?? 'finance-report.csv';
}

export const adminFinanceApi = {
  // Bỏ trống status là mọi trạng thái, bỏ trống submittedOnly là false (docs/api/payment.md).
  async topUpOrders(params: {
    page: number;
    size: number;
    status?: PaymentOrderStatus;
    submittedOnly?: boolean;
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
  async reportCsv(params: AdminDateParams): Promise<FinanceCsvDownload> {
    const response = await http.get<Blob>('/api/v1/admin/finance/report.csv', {
      params,
      responseType: 'blob',
    });
    return {
      blob: response.data,
      fileName: financeCsvFileName(response.headers['content-disposition']),
    };
  },
  async users(params: { keyword?: string; page: number; size: number }) {
    const { data } = await http.get<PageResponse<AdminFinanceUserSummary>>(
      '/api/v1/admin/finance/users',
      { params },
    );
    return data;
  },
  async userWallet(userId: number) {
    const { data } = await http.get<AdminFinanceUserSummary>(
      `/api/v1/admin/finance/users/${userId}/wallet`,
    );
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
