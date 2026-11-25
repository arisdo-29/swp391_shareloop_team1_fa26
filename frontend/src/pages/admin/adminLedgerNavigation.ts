import type { LedgerTxType } from '../../services/adminFinanceApi';
import type { AdminUserSummary } from '../../services/adminUserApi';

export interface AdminLedgerViewState {
  type: LedgerTxType | '';
  user: AdminUserSummary | null;
  keywordInput: string;
  fromDate: string;
  toDate: string;
  page: number;
}

export interface AdminLedgerRouteState {
  ledgerReturn?: AdminLedgerViewState;
  restoreLedger?: AdminLedgerViewState;
  openTopUpOrder?: string;
}
