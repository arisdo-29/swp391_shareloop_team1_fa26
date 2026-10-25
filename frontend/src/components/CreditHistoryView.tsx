import type { ReactNode } from 'react';
import type { CreditHistory, CreditHistoryType } from '../types/domain';
import { Alert, Button, Icon } from './ui';
import {
  HISTORY_PAGE_SIZE,
  itemTitleOf,
  quickRangeDates,
  type HistoryTab,
  type QuickRange,
} from '../utils/creditHistory';

function balanceBefore(entry: CreditHistory) {
  return entry.balance - entry.amount;
}

export function CreditHistorySection({
  itemTitles,
  allHistory,
  tabs,
  activeTab,
  onTabChange,
  onSelect,
  unavailable = false,
  dateFilter = null,
  pagination = null,
}: {
  itemTitles: Map<string, string>;
  allHistory: CreditHistory[];
  tabs: HistoryTab[];
  activeTab: string;
  onTabChange: (tabId: string) => void;
  onSelect: (entry: CreditHistory) => void;
  unavailable?: boolean;
  dateFilter?: ReactNode;
  pagination?: ReactNode;
}) {
  const current = tabs.find((tab) => tab.id === activeTab) ?? tabs[0];
  const history = allHistory.filter(current.matches);
  const countFor = (tab: HistoryTab) => allHistory.filter(tab.matches).length;

  return (
    <section className="mt-10">
      <h2 className="section-title">Lịch sử Credit</h2>
      {dateFilter}
      {unavailable ? (
        <div className="mt-4 rounded-xl bg-white px-5 py-12 text-center text-sm text-text-muted ring-1 ring-border/80">
          Chưa thể tải lịch sử Credit.
        </div>
      ) : (
        <>
          <div className="mt-4 overflow-x-auto border-b border-border">
            <div className="flex min-w-max gap-1">
              {tabs.map((tab) => (
                <button
                  key={tab.id}
                  onClick={() => onTabChange(tab.id)}
                  className={`border-b-2 px-3 py-2.5 text-sm font-semibold ${current.id === tab.id ? 'border-primary text-primary' : 'border-transparent text-text-muted hover:text-text-primary'}`}
                >
                  {tab.label} ({countFor(tab)})
                </button>
              ))}
            </div>
          </div>
          <div className="overflow-hidden rounded-b-xl bg-white ring-1 ring-border/80">
            {history.map((entry) => {
              const itemTitle = itemTitleOf(entry, itemTitles);
              const title = itemTitle
                ? `${historyTitle(entry)} - ${itemTitle}`
                : historyTitle(entry);
              return (
                <HistoryRow
                  key={entry.id}
                  icon={historyIcon(entry)}
                  title={title}
                  date={entry.createdAt}
                  amount={entry.amount}
                  status={historyStatus(entry)}
                  tone={historyTone(entry)}
                  onClick={() => onSelect(entry)}
                />
              );
            })}
            {!history.length ? (
              <div className="px-5 py-12 text-center text-sm text-text-muted">
                Chưa có giao dịch phù hợp.
              </div>
            ) : null}
          </div>
          {pagination}
        </>
      )}
    </section>
  );
}

export function HistoryDateFilter({
  fromDate,
  toDate,
  error,
  onChange,
}: {
  fromDate: string;
  toDate: string;
  error: string;
  onChange: (fromDate: string, toDate: string) => void;
}) {
  const quickButtons: Array<[QuickRange, string]> = [
    ['7days', '7 ngày'],
    ['month', 'Tháng này'],
    ['quarter', 'Quý này'],
    ['all', 'Tất cả'],
  ];
  const dateInputClass = 'rounded-md border border-border bg-white px-2 py-1.5 text-sm';

  return (
    <div className="mt-4 space-y-3">
      <div className="flex flex-wrap gap-2">
        {quickButtons.map(([range, label]) => (
          <Button
            key={range}
            size="sm"
            variant="outline"
            onClick={() => onChange(...quickRangeDates(range))}
          >
            {label}
          </Button>
        ))}
      </div>
      <div className="flex flex-wrap items-center gap-3 text-sm">
        <label className="flex items-center gap-2">
          Từ ngày
          <input
            type="date"
            className={dateInputClass}
            value={fromDate}
            onChange={(event) => onChange(event.target.value, toDate)}
          />
        </label>
        <label className="flex items-center gap-2">
          Đến ngày
          <input
            type="date"
            className={dateInputClass}
            value={toDate}
            onChange={(event) => onChange(fromDate, event.target.value)}
          />
        </label>
      </div>
      {error ? <Alert tone="error">{error}</Alert> : null}
    </div>
  );
}

export function HistoryPagination({
  page,
  total,
  onPageChange,
}: {
  page: number;
  total: number;
  onPageChange: (page: number) => void;
}) {
  const totalPages = Math.ceil(total / HISTORY_PAGE_SIZE);
  if (totalPages <= 1) return null;

  return (
    <div className="mt-4 flex items-center justify-between text-sm">
      <Button
        size="sm"
        variant="outline"
        disabled={page === 0}
        onClick={() => onPageChange(page - 1)}
      >
        Trang trước
      </Button>
      <span className="text-text-muted">
        Trang {page + 1} / {totalPages}
      </span>
      <Button
        size="sm"
        variant="outline"
        disabled={page + 1 >= totalPages}
        onClick={() => onPageChange(page + 1)}
      >
        Trang sau
      </Button>
    </div>
  );
}

function HistoryRow({
  icon,
  title,
  date,
  amount,
  status,
  tone,
  onClick,
}: {
  icon: 'wallet' | 'payments' | 'history' | 'renew';
  title: string;
  date: string;
  amount: number;
  status: string;
  tone: 'positive' | 'negative' | 'hold' | 'pending';
  onClick: () => void;
}) {
  const amountClass =
    tone === 'positive'
      ? 'text-success'
      : tone === 'negative'
        ? 'text-error'
        : tone === 'hold'
          ? 'text-warning'
          : 'text-text-secondary';
  const prefix = amount > 0 ? '+' : '';

  return (
    <button
      type="button"
      onClick={onClick}
      className="grid w-full cursor-pointer grid-cols-[1fr_auto_auto] gap-4 border-b border-border/70 px-4 py-4 text-left transition hover:bg-surface-low last:border-0 sm:px-5"
    >
      <div className="flex min-w-0 gap-3">
        <span className="mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-md bg-surface-low text-primary">
          <Icon name={icon} className="size-5" />
        </span>
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold text-text-primary">{title}</p>
          <p className="mt-1 truncate text-xs text-text-muted">
            {new Date(date).toLocaleString('vi-VN')}
          </p>
        </div>
      </div>
      <div className="self-center text-right">
        <strong className={`block whitespace-nowrap text-sm tabular-nums ${amountClass}`}>
          {prefix}
          {amount} Credit
        </strong>
        <span className="mt-1 block text-xs text-text-muted">{status}</span>
      </div>
      <Icon name="chevronDown" className="-rotate-90 self-center text-text-muted" />
    </button>
  );
}

// Nhãn theo loại giao dịch. Phí và hoàn phí lấy mô tả đã đặt theo loại của API
// (Phí đăng bài, Phí chỉnh sửa bài, Phí gia hạn bài, Phí đẩy tin, Hoàn phí tìm kiếm AI...).
function historyTitle(entry: CreditHistory) {
  if (entry.type === 'RECEIVE_FEE' || entry.type === 'SPEND' || entry.type === 'REFUND') {
    return entry.description;
  }
  const labels: Record<CreditHistoryType, string> = {
    TOPUP: 'Nạp Credit',
    TRANSACTION_FEE: 'Lịch sử cũ',
    SPEND: 'Chi tiêu',
    RECEIVE_FEE: 'Phí nhận đồ',
    AI_SPEND: 'Phí dịch vụ AI',
    HOLD: 'Lịch sử cũ',
    RELEASE_HOLD: 'Hoàn Credit',
    REFUND: 'Hoàn Credit',
    ADMIN_ADJUSTMENT: 'Cập nhật hệ thống',
  };
  return labels[entry.type];
}

function historyStatus(entry: CreditHistory) {
  if (entry.status === 'pending') return 'Đang xác nhận';
  if (entry.status === 'refunded') return 'Đã hoàn';
  return 'Hoàn tất';
}

function historyTone(entry: CreditHistory): 'positive' | 'negative' | 'hold' | 'pending' {
  if (entry.amount > 0) return 'positive';
  return 'negative';
}

function historyIcon(entry: CreditHistory): 'wallet' | 'payments' | 'history' | 'renew' {
  if (entry.type === 'TOPUP') return 'wallet';
  if (entry.type === 'REFUND' || entry.type === 'RELEASE_HOLD') return 'renew';
  return 'payments';
}

export function CreditTransactionDetail({
  entry,
  relatedItemTitle,
  onClose,
}: {
  entry: CreditHistory;
  relatedItemTitle?: string;
  onClose: () => void;
}) {
  return (
    <div
      className="fixed inset-0 z-50 grid place-items-center bg-slate-950/45 p-4"
      onMouseDown={onClose}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="credit-detail-title"
        className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-xl bg-white p-6 shadow-2xl sm:p-7"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-4">
          <h2 id="credit-detail-title" className="text-xl font-bold text-text-primary">
            Chi tiết giao dịch Credit
          </h2>
          <button
            type="button"
            aria-label="Đóng"
            onClick={onClose}
            className="rounded-md p-1 text-text-muted hover:bg-surface-low hover:text-text-primary"
          >
            <Icon name="close" className="size-5" />
          </button>
        </div>
        <dl className="mt-6 divide-y divide-border rounded-lg bg-surface-low px-4">
          <DetailRow label="Loại giao dịch" value={historyTitle(entry)} />
          {relatedItemTitle ? <DetailRow label="Món đồ" value={relatedItemTitle} /> : null}
          <DetailRow
            label="Số Credit"
            value={`${entry.amount > 0 ? '+' : ''}${entry.amount} Credit`}
            valueClass={historyTone(entry) === 'negative' ? 'text-error' : 'text-success'}
          />
          <DetailRow label="Số dư trước" value={`${balanceBefore(entry)} Credit`} />
          <DetailRow label="Số dư sau" value={`${entry.balance} Credit`} />
          <DetailRow label="Thời gian" value={formatDateTime(entry.createdAt)} />
          <DetailRow label="Trạng thái" value={historyStatus(entry)} />
        </dl>
        <div className="mt-5 rounded-lg border border-border p-4 text-sm">
          <p className="font-semibold text-text-primary">Nội dung liên quan</p>
          <p className="mt-2 text-text-secondary">{entry.note || entry.description}</p>
        </div>
      </div>
    </div>
  );
}

export function DetailRow({
  label,
  value,
  valueClass = 'text-text-primary',
}: {
  label: string;
  value: string;
  valueClass?: string;
}) {
  return (
    <div className="flex items-center justify-between gap-4 py-3 text-sm">
      <dt className="text-text-muted">{label}</dt>
      <dd className={`text-right font-semibold ${valueClass}`}>{value}</dd>
    </div>
  );
}

function formatDateTime(value: string) {
  return new Date(value).toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}
