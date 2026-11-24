import { http } from './http';
import type { PageResponse } from './itemApi';
import type { WalletTransactionResponse } from './walletApi';
import type { PaymentOrderStatus } from './paymentApi';

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
    unlinkedEntryCount?: number;
    unlinkedCredits?: number;
    unlinkedEntries?: Array<{ id: number; userId: number; amount: number; createdAt: string }>;
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
  return financeFileName(contentDisposition, 'finance-report.csv');
}

function financeFileName(contentDisposition: string | undefined, fallback: string) {
  const utf8Name = contentDisposition?.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
  if (utf8Name) {
    try {
      return decodeURIComponent(utf8Name.replace(/^"|"$/g, ''));
    } catch {
      // Dùng tên filename thông thường hoặc tên mặc định nếu header mã hóa sai.
    }
  }
  return contentDisposition?.match(/filename="?([^";]+)"?/i)?.[1] ?? fallback;
}

export type AdminTopUpAction = 'GRANT_CREDIT' | 'REFUNDED_OUTSIDE';

/** Một đơn nạp trong trang Giao dịch nạp tiền của Admin (docs/api/payment.md). */
export interface AdminTopUpOrder {
  orderCode: string;
  userId: number;
  userName: string | null;
  userEmail: string | null;
  amountVnd: number;
  credits: number;
  provider: 'VNPAY' | 'MOCK' | 'MANUAL';
  status: PaymentOrderStatus;
  providerTxnId: string | null;
  createdAt: string;
  paidAt: string | null;
  needsReconcile: boolean;
  reviewedAt: string | null;
  reviewedByName: string | null;
  reviewNote: string | null;
}

export interface AdminTopUpOrderDetail {
  order: AdminTopUpOrder;
  callbackData: Record<string, string>;
  ledgerLines: { id: number; amount: number; balanceAfter: number; createdAt: string }[];
}

export interface AdminTopUpSummary {
  byStatus: { status: PaymentOrderStatus; orderCount: number; amountVnd: number }[];
  needsReconcileCount: number;
}

interface AdminTopUpDateParams extends AdminDateParams {
  paidDate?: boolean;
}

export interface AdminTopUpFilters extends AdminTopUpDateParams {
  status?: PaymentOrderStatus;
  q?: string;
  needsReconcile?: boolean;
}

/** Một dòng sổ cái Credit của toàn hệ thống (docs/api/admin.md mục 2.4). */
export type LedgerTxType =
  | 'TOP_UP'
  | 'POST_FEE'
  | 'EDIT_FEE'
  | 'RENEW_FEE'
  | 'BOOST_FEE'
  | 'AI_SEARCH_FEE'
  | 'ADMIN_ADJUST';

export interface AdminLedgerRow {
  id: number;
  createdAt: string;
  userId: number;
  userName: string | null;
  userEmail: string | null;
  type: LedgerTxType;
  amount: number;
  balanceAfter: number;
  itemId: number | null;
  itemTitle: string | null;
  orderCode: string | null;
  note: string | null;
  createdByName: string | null;
}

export interface AdminLedgerPage {
  items: AdminLedgerRow[];
  page: number;
  size: number;
  totalElements: number;
  totalIn: number;
  totalOut: number;
}

export interface AdminLedgerFilters {
  type?: LedgerTxType;
  userId?: number;
  q?: string;
  from?: string;
  to?: string;
}

export const adminFinanceApi = {
  // Bỏ trống bộ lọc nào là không lọc theo tiêu chí đó (docs/api/payment.md).
  async topUpOrders(params: AdminTopUpFilters & { page: number; size: number }) {
    const { data } = await http.get<PageResponse<AdminTopUpOrder>>(
      '/api/v1/admin/finance/topup-orders',
      { params },
    );
    return data;
  },
  async topUpSummary(params: AdminTopUpDateParams) {
    const { data } = await http.get<AdminTopUpSummary>(
      '/api/v1/admin/finance/topup-orders/summary',
      { params },
    );
    return data;
  },
  async topUpOrderDetail(orderCode: string) {
    const { data } = await http.get<AdminTopUpOrderDetail>(
      `/api/v1/admin/finance/topup-orders/${orderCode}`,
    );
    return data;
  },
  async reconcileTopUp(orderCode: string, body: { action: AdminTopUpAction; note: string }) {
    const { data } = await http.post<AdminTopUpOrderDetail>(
      `/api/v1/admin/finance/topup-orders/${orderCode}/reconcile`,
      body,
    );
    return data;
  },
  async ledger(params: AdminLedgerFilters & { page: number; size: number }) {
    const { data } = await http.get<AdminLedgerPage>('/api/v1/admin/finance/ledger', { params });
    return data;
  },
  async ledgerCsv(params: AdminLedgerFilters): Promise<FinanceCsvDownload> {
    const response = await http.get<Blob>('/api/v1/admin/finance/ledger.csv', {
      params,
      responseType: 'blob',
    });
    return {
      blob: response.data,
      fileName: financeFileName(
        response.headers['content-disposition'],
        'lich-su-giao-dich-credit.csv',
      ),
    };
  },
  async topUpOrdersCsv(params: AdminTopUpFilters): Promise<FinanceCsvDownload> {
    const response = await http.get<Blob>('/api/v1/admin/finance/topup-orders/export.csv', {
      params,
      responseType: 'blob',
    });
    return {
      blob: response.data,
      fileName: financeFileName(response.headers['content-disposition'], 'giao-dich-nap-tien.csv'),
    };
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
  async reportExcel(params: AdminDateParams) {
    const response = await http.get<Blob>('/api/v1/admin/finance/report.xlsx', {
      params,
      responseType: 'blob',
    });
    const format = (date: string | undefined, fallback: string) =>
      date ? date.split('-').reverse().join('-') : fallback;
    const fileName = `bao-cao-tai-chinh_${format(params.from, 'bat-dau')}_${format(params.to, 'hien-tai')}.xlsx`;
    return {
      blob: response.data,
      fileName: financeFileName(response.headers['content-disposition'], fileName),
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
