import type { ReactNode } from 'react';
import type { CreditHistory, CreditHistoryType } from '../types/domain';
import { Alert, Button, Icon } from './ui';
import { DialogFrame } from './Dialog';
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
  tabCounts,
  activeTab,
  onTabChange,
  onSelect,
  unavailable = false,
  dateFilter = null,
  summary = null,
  pagination = null,
}: {
  itemTitles: Map<string, string>;
  allHistory: CreditHistory[];
  tabs: HistoryTab[];
  // Số trên từng tab (theo id tab) do BE đếm cả khoảng lọc; không truyền thì đếm trên các dòng đang có.
  tabCounts?: Record<string, number>;
  activeTab: string;
  onTabChange: (tabId: string) => void;
  onSelect: (entry: CreditHistory) => void;
  unavailable?: boolean;
  dateFilter?: ReactNode;
  summary?: ReactNode;
  pagination?: ReactNode;
}) {
  const current = tabs.find((tab) => tab.id === activeTab) ?? tabs[0];
  const history = allHistory.filter(current.matches);
  const countFor = (tab: HistoryTab) => tabCounts?.[tab.id] ?? allHistory.filter(tab.matches).length;

  const days = groupByDay(history);

  return (
    <section aria-labelledby="credit-history-title" className="mt-5 space-y-4 rounded-xl bg-white p-5 ring-1 ring-border/80 sm:p-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 id="credit-history-title" className="section-title">Lịch sử Credit</h2>
        {!unavailable ? (
          <div role="tablist" aria-label="Loại giao dịch" className="inline-flex flex-wrap gap-1 rounded-lg bg-surface-low p-1">
            {tabs.map((tab) => (
              <button
                key={tab.id}
                type="button"
                role="tab"
                aria-selected={current.id === tab.id}
                onClick={() => onTabChange(tab.id)}
                className={`min-h-9 rounded-md px-3 text-xs font-semibold transition ${current.id === tab.id ? 'bg-white text-primary shadow-sm' : 'text-text-muted hover:text-text-primary'}`}
              >
                {tab.label} ({countFor(tab)})
              </button>
            ))}
          </div>
        ) : null}
      </div>
      {dateFilter}
      {summary}
      {unavailable ? (
        <p className="py-10 text-center text-sm text-text-muted">Chưa thể tải lịch sử Credit.</p>
      ) : history.length ? (
        <div className="space-y-4">
          {days.map(([day, entries]) => (
            <div key={day}>
              <h3 className="mb-1.5 text-xs font-bold text-text-muted">{day}</h3>
              <div className="overflow-hidden rounded-md ring-1 ring-border/80">
                {entries.map((entry) => {
                  const itemTitle = itemTitleOf(entry, itemTitles);
                  return (
                    <HistoryRow
                      key={entry.id}
                      title={historyTitle(entry)}
                      subtitle={itemTitle}
                      date={entry.createdAt}
                      amount={entry.amount}
                      status={historyStatus(entry)}
                      tone={historyTone(entry)}
                      onClick={() => onSelect(entry)}
                    />
                  );
                })}
              </div>
            </div>
          ))}
        </div>
      ) : (
        <p className="py-10 text-center text-sm text-text-muted">Chưa có giao dịch phù hợp.</p>
      )}
      {!unavailable ? pagination : null}
    </section>
  );
}

// Gom các dòng theo ngày (giờ Việt Nam do trình duyệt hiển thị), giữ thứ tự mới nhất trước như API trả về.
function groupByDay(entries: CreditHistory[]) {
  const groups = new Map<string, CreditHistory[]>();
  entries.forEach((entry) => {
    const day = new Date(entry.createdAt).toLocaleDateString('vi-VN', { weekday: 'long', day: '2-digit', month: '2-digit', year: 'numeric' });
    groups.set(day, [...(groups.get(day) ?? []), entry]);
  });
  return [...groups.entries()];
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
  const dateInputClass = 'min-h-11 rounded-md border border-border bg-white px-3 py-2 text-sm';

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-2" role="group" aria-label="Khoảng thời gian">
        {quickButtons.map(([range, label]) => {
          const [rangeFrom, rangeTo] = quickRangeDates(range);
          const active = rangeFrom === fromDate && rangeTo === toDate;
          return (
            <button
              key={range}
              type="button"
              aria-pressed={active}
              onClick={() => onChange(rangeFrom, rangeTo)}
              className={`min-h-9 rounded-full border px-3 text-xs font-semibold transition ${active ? 'border-primary bg-primary-soft text-primary' : 'border-border bg-white text-text-secondary hover:border-primary/50'}`}
            >
              {label}
            </button>
          );
        })}
      </div>
      <div className="flex flex-wrap items-center gap-3 text-sm text-text-secondary">
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
  title,
  subtitle,
  date,
  amount,
  status,
  tone,
  onClick,
}: {
  title: string;
  subtitle?: string;
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
      className="grid w-full cursor-pointer grid-cols-[1fr_auto] gap-4 border-b border-border/70 px-4 py-3 text-left transition hover:bg-surface-low last:border-0"
    >
      <div className="min-w-0">
        <p className="truncate text-sm font-semibold text-text-primary">{subtitle ? `${title} - ${subtitle}` : title}</p>
        <p className="mt-0.5 truncate text-xs text-text-muted">
          {new Date(date).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}
        </p>
      </div>
      <div className="self-center text-right">
        <strong className={`block whitespace-nowrap text-sm tabular-nums ${amountClass}`}>
          {prefix}
          {amount} Credit
        </strong>
        <span className="mt-1 block text-xs text-text-muted">{status}</span>
      </div>
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
    <DialogFrame labelledBy="credit-detail-title" onClose={onClose}>
      <div className="flex items-start justify-between gap-4">
        <h2 id="credit-detail-title" className="text-xl font-bold text-text-primary">
          Chi tiết giao dịch Credit
        </h2>
        <button
          type="button"
          aria-label="Đóng"
          onClick={onClose}
          className="grid size-11 shrink-0 place-items-center rounded-md text-text-muted transition hover:bg-surface-low hover:text-text-primary"
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
    </DialogFrame>
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
