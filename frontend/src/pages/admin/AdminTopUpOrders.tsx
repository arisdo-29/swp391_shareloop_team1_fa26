import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Dialog } from '../../components/Dialog';
import { Pagination } from '../../components/Pagination';
import {
  Alert,
  Button,
  Field,
  Icon,
  PageHeader,
  SearchField,
  Select,
  SkeletonRows,
  TextArea,
} from '../../components/ui';
import {
  adminFinanceApi,
  type AdminTopUpAction,
  type AdminTopUpFilters,
  type AdminTopUpOrder,
  type AdminTopUpOrderDetail,
  type AdminTopUpSummary,
} from '../../services/adminFinanceApi';
import type { PageResponse } from '../../services/itemApi';
import type { PaymentOrderStatus } from '../../services/paymentApi';
import { formatVnd } from '../../utils/formatting';
import { adminErrorMessage } from './adminErrors';
import type { AdminLedgerRouteState, AdminLedgerViewState } from './adminLedgerNavigation';
import { DashboardGrid, DashboardStatCard, type DashboardAccent } from './AdminDashboardCards';
import './admin-topup-orders.css';

const PAGE_SIZE = 10;
const REASON_MIN = 5;
const REASON_MAX = 500;

const statusLabels: Record<PaymentOrderStatus, string> = {
  PENDING: 'Chờ thanh toán',
  SUCCESS: 'Thành công',
  FAILED: 'Thất bại',
  EXPIRED: 'Hết hạn',
  CANCELLED: 'Đã hủy',
};

const providerLabels: Record<AdminTopUpOrder['provider'], string> = {
  VNPAY: 'VNPay',
  MOCK: 'Giả lập',
  MANUAL: 'Thủ công',
};

function statusClass(status: PaymentOrderStatus) {
  if (status === 'SUCCESS') return 'bg-success-soft text-success';
  if (status === 'PENDING') return 'bg-warning-soft text-warning';
  if (status === 'FAILED') return 'bg-error-soft text-error';
  if (status === 'EXPIRED') return 'bg-info-soft text-info';
  return 'admin-topup-status--cancelled';
}

function formatDateTime(value: string | null) {
  return value ? new Date(value).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' }) : '—';
}

function displayProviderTxnId(value: string | null) {
  const normalized = value?.trim();
  return normalized && normalized !== '0' ? normalized : '—';
}

// Mã đơn dài 32 ký tự nên chỉ hiện đầu và cuối, nút sao chép giữ đủ mã.
function shortCode(orderCode: string) {
  if (orderCode.length <= 10) return `#${orderCode.toUpperCase()}`;
  return `#${orderCode.slice(0, 4).toUpperCase()}...${orderCode.slice(-4).toUpperCase()}`;
}

function downloadBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = fileName;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

export function AdminTopUpOrders() {
  const location = useLocation();
  return <TopUpOrdersList key={location.search} location={location} />;
}

function TopUpOrdersList({ location }: { location: ReturnType<typeof useLocation> }) {
  const query = new URLSearchParams(location.search);
  const paidDate = query.get('paidDate') === 'true';
  const routeState = location.state as AdminLedgerRouteState | null;
  const ledgerReturn = routeState?.ledgerReturn;
  const [result, setResult] = useState<PageResponse<AdminTopUpOrder> | null>(null);
  const [summary, setSummary] = useState<AdminTopUpSummary | null>(null);
  const [status, setStatus] = useState<PaymentOrderStatus | ''>(() =>
    query.get('status') === 'SUCCESS' ? 'SUCCESS' : '',
  );
  const [keywordInput, setKeywordInput] = useState(() => query.get('q') ?? '');
  const [keyword, setKeyword] = useState(keywordInput);
  const [fromDate, setFromDate] = useState(() => query.get('from') ?? '');
  const [toDate, setToDate] = useState(() => query.get('to') ?? '');
  const [needsReconcile, setNeedsReconcile] = useState(
    () => query.get('needsReconcile') === 'true',
  );
  const [page, setPage] = useState(0);
  const [reloadKey, setReloadKey] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [exporting, setExporting] = useState(false);
  const [openedCode, setOpenedCode] = useState<string | null>(routeState?.openTopUpOrder ?? null);
  const fromLabel = paidDate ? 'Từ ngày thanh toán' : 'Từ ngày';
  const toLabel = paidDate ? 'Đến ngày thanh toán' : 'Đến ngày';

  const dateRangeError =
    fromDate && toDate && fromDate > toDate ? `"${fromLabel}" không được sau "${toLabel}".` : '';

  // Chờ người dùng ngừng gõ rồi mới tìm, để mỗi phím không gọi API một lần.
  useEffect(() => {
    const timer = setTimeout(() => {
      setKeyword(keywordInput.trim());
      setPage(0);
    }, 300);
    return () => clearTimeout(timer);
  }, [keywordInput]);

  const buildFilters = useCallback((): AdminTopUpFilters => {
    const filters: AdminTopUpFilters = {};
    if (status) filters.status = status;
    if (keyword) filters.q = keyword;
    if (fromDate) filters.from = fromDate;
    if (toDate) filters.to = toDate;
    if (paidDate) filters.paidDate = true;
    if (needsReconcile) filters.needsReconcile = true;
    return filters;
  }, [status, keyword, fromDate, toDate, paidDate, needsReconcile]);

  useEffect(() => {
    if (dateRangeError) return;
    let active = true;
    setLoading(true);
    setError('');

    adminFinanceApi
      .topUpOrders({ ...buildFilters(), page, size: PAGE_SIZE })
      .then((response) => {
        if (!active) return;
        if (response.items.length === 0 && page > 0) {
          setPage((current) => Math.max(0, current - 1));
          return;
        }
        setResult(response);
      })
      .catch((caught) => {
        if (!active) return;
        setResult(null);
        setError(adminErrorMessage(caught, 'Không tải được danh sách đơn nạp. Vui lòng thử lại.'));
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [buildFilters, dateRangeError, page, reloadKey]);

  // Khối tổng hợp chỉ theo khoảng ngày, không theo trạng thái, để bấm vào từng ô vẫn thấy số của các ô còn lại.
  useEffect(() => {
    if (dateRangeError) return;
    let active = true;
    adminFinanceApi
      .topUpSummary({
        from: fromDate || undefined,
        to: toDate || undefined,
        ...(paidDate && { paidDate }),
      })
      .then((response) => {
        if (active) setSummary(response);
      })
      .catch(() => {
        if (active) setSummary(null);
      });
    return () => {
      active = false;
    };
  }, [dateRangeError, fromDate, toDate, paidDate, reloadKey]);

  const reload = () => setReloadKey((current) => current + 1);

  const exportExcel = async () => {
    setExporting(true);
    setError('');
    try {
      const download = await adminFinanceApi.topUpOrdersExcel(buildFilters());
      downloadBlob(download.blob, download.fileName);
    } catch (caught) {
      setError(adminErrorMessage(caught, 'Không xuất được tệp Excel. Vui lòng thử lại.'));
    } finally {
      setExporting(false);
    }
  };

  const chooseStatus = (next: PaymentOrderStatus | '') => {
    setStatus(next);
    setNeedsReconcile(false);
    setPage(0);
  };

  return (
    <section className="admin-topup-page">
      <div className="admin-topup-header">
        <PageHeader
          title="Giao dịch nạp tiền"
          description="Đối soát từng đơn nạp Credit qua cổng thanh toán. Đơn đã đóng mà VNPay vẫn thu tiền được đánh dấu cần đối soát."
          action={
            <div className="flex gap-2">
              {ledgerReturn ? (
                <Link
                  to="/admin/finance/ledger"
                  state={{ restoreLedger: ledgerReturn }}
                  className="inline-flex min-h-11 items-center rounded-md border border-border px-4 text-sm font-semibold text-primary hover:bg-surface-low"
                >
                  Về lịch sử giao dịch Credit
                </Link>
              ) : null}
              <Button
                variant="outline"
                disabled={exporting || Boolean(dateRangeError)}
                onClick={() => void exportExcel()}
              >
                {exporting ? 'Đang xuất...' : 'Xuất Excel'}
              </Button>
              <Button variant="outline" disabled={loading} onClick={reload}>
                Tải lại
              </Button>
            </div>
          }
        />
      </div>

      <SummaryCards
        summary={summary}
        activeStatus={status}
        reconcileOnly={needsReconcile}
        onStatus={chooseStatus}
        onReconcile={() => {
          setNeedsReconcile((current) => !current);
          setStatus('');
          setPage(0);
        }}
      />

      <div className="admin-topup-filters">
        <div className="admin-topup-filter-search">
          <span className="label">Tìm kiếm đơn nạp</span>
          <SearchField
            aria-label="Tìm đơn nạp"
            placeholder="Tìm theo mã đơn, tên hoặc email người nạp..."
            value={keywordInput}
            onChange={(event) => setKeywordInput(event.target.value)}
          />
        </div>
        <Select
          className="admin-topup-filter-status"
          label="Trạng thái"
          value={status}
          onChange={(event) => chooseStatus(event.target.value as PaymentOrderStatus | '')}
        >
          <option value="">Tất cả</option>
          {Object.entries(statusLabels).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </Select>
        <div className="admin-topup-filter-date">
          <Field
            label={fromLabel}
            type="date"
            value={fromDate}
            error={dateRangeError || undefined}
            onChange={(event) => {
              setFromDate(event.target.value);
              setPage(0);
            }}
          />
        </div>
        <div className="admin-topup-filter-date">
          <Field
            label={toLabel}
            type="date"
            value={toDate}
            onChange={(event) => {
              setToDate(event.target.value);
              setPage(0);
            }}
          />
        </div>
        <label className="admin-topup-filter-reconcile">
          <input
            type="checkbox"
            className="size-4"
            checked={needsReconcile}
            onChange={(event) => {
              setNeedsReconcile(event.target.checked);
              setPage(0);
            }}
          />
          Chỉ đơn cần đối soát
        </label>
      </div>

      {error ? (
        <div className="space-y-3">
          <Alert tone="error">{error}</Alert>
          <Button variant="outline" onClick={reload}>
            Thử lại
          </Button>
        </div>
      ) : null}
      {loading && !result ? (
        <SkeletonRows label="Đang tải đơn nạp" />
      ) : result ? (
        <div className="admin-topup-table-section">
          <div className="table-wrap admin-topup-table-wrap">
            <table className="data-table admin-topup-table">
              <thead>
                <tr>
                  <th>Mã đơn nạp</th>
                  <th>Người nạp</th>
                  <th>Số tiền / Credit</th>
                  <th>Mã giao dịch VNPay</th>
                  <th>Trạng thái thanh toán</th>
                  <th>Thời gian giao dịch</th>
                  <th>Ghi chú</th>
                  <th className="text-center">Xem chi tiết</th>
                </tr>
              </thead>
              <tbody>
                {result.items.map((order) => (
                  <TopUpRow key={order.orderCode} order={order} onOpen={setOpenedCode} />
                ))}
                {result.items.length === 0 ? (
                  <tr>
                    <td colSpan={8} className="text-center text-text-muted">
                      Không có đơn nạp phù hợp bộ lọc hiện tại.
                    </td>
                  </tr>
                ) : null}
              </tbody>
            </table>
          </div>
          <Pagination
            page={result.page}
            size={result.size}
            totalElements={result.totalElements}
            disabled={loading}
            onPageChange={setPage}
          />
        </div>
      ) : null}

      {openedCode ? (
        <OrderDialog
          orderCode={openedCode}
          ledgerReturn={ledgerReturn}
          onClose={() => setOpenedCode(null)}
          onChanged={reload}
        />
      ) : null}
    </section>
  );
}

function SummaryCards({
  summary,
  activeStatus,
  reconcileOnly,
  onStatus,
  onReconcile,
}: {
  summary: AdminTopUpSummary | null;
  activeStatus: PaymentOrderStatus | '';
  reconcileOnly: boolean;
  onStatus: (status: PaymentOrderStatus) => void;
  onReconcile: () => void;
}) {
  if (!summary) return null;
  const appearances: Record<
    PaymentOrderStatus,
    { icon: 'completed' | 'expiring' | 'banned'; accent: DashboardAccent }
  > = {
    SUCCESS: { icon: 'completed', accent: 'green' },
    PENDING: { icon: 'expiring', accent: 'gold' },
    FAILED: { icon: 'banned', accent: 'red' },
    EXPIRED: { icon: 'expiring', accent: 'red' },
    CANCELLED: { icon: 'banned', accent: 'red' },
  };
  return (
    <div className="admin-dash-vars" aria-label="Thống kê đơn nạp">
      <DashboardGrid columns={6}>
        <DashboardStatCard
          variant="finance"
          icon="reconcile"
          accent="red"
          label="Cần đối soát"
          value={`${summary.needsReconcileCount} đơn`}
          note="Cần kiểm tra thủ công"
          active={reconcileOnly}
          onClick={onReconcile}
        />
        {summary.byStatus.map((total) => {
          const context =
            total.status === 'SUCCESS'
              ? 'Tiền thực thu'
              : total.status === 'PENDING'
                ? 'Giá trị chưa thanh toán'
                : total.status === 'FAILED'
                  ? 'Giá trị thanh toán thất bại'
                  : total.status === 'EXPIRED'
                    ? 'Giá trị đơn hết hạn'
                    : 'Giá trị đơn đã hủy';
          return (
            <DashboardStatCard
              key={total.status}
              variant="finance"
              icon={appearances[total.status].icon}
              accent={appearances[total.status].accent}
              label={statusLabels[total.status]}
              value={formatVnd(total.amountVnd)}
              note={`${context} · ${total.orderCount} đơn`}
              active={activeStatus === total.status}
              onClick={() => onStatus(total.status)}
            />
          );
        })}
      </DashboardGrid>
    </div>
  );
}

function CopyCodeButton({ orderCode }: { orderCode: string }) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(orderCode);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  };
  return (
    <button
      type="button"
      aria-label={copied ? 'Đã sao chép mã đơn' : 'Sao chép mã đơn'}
      title={copied ? 'Đã sao chép' : 'Sao chép mã đơn đầy đủ'}
      className="admin-topup-copy"
      onClick={(event) => {
        event.stopPropagation();
        void copy();
      }}
    >
      <Icon name="copy" className="size-4" weight={copied ? 'fill' : 'regular'} />
    </button>
  );
}

function StatusPill({ order }: { order: AdminTopUpOrder }) {
  return (
    <div className="admin-topup-status-stack">
      <span
        className={`inline-flex whitespace-nowrap rounded-sm px-2 py-1 text-2xs font-semibold ${statusClass(order.status)}`}
      >
        {statusLabels[order.status]}
      </span>
      {order.needsReconcile ? (
        <span className="inline-flex whitespace-nowrap rounded-sm bg-error px-2 py-1 text-2xs font-semibold text-white">
          Cần đối soát
        </span>
      ) : null}
    </div>
  );
}

function TopUpRow({
  order,
  onOpen,
}: {
  order: AdminTopUpOrder;
  onOpen: (orderCode: string) => void;
}) {
  return (
    <tr className="admin-topup-row" onClick={() => onOpen(order.orderCode)}>
      <td className="admin-topup-code-cell">
        <div className="admin-topup-code-wrap">
          <button
            type="button"
            className="font-mono text-xs font-semibold text-primary hover:underline"
            onClick={(event) => {
              event.stopPropagation();
              onOpen(order.orderCode);
            }}
          >
            {shortCode(order.orderCode)}
          </button>
          <CopyCodeButton orderCode={order.orderCode} />
        </div>
      </td>
      <td className="admin-topup-user-cell">
        <p className="font-semibold text-text-primary">{order.userName || 'Người dùng đã xóa'}</p>
        {order.userEmail ? <p className="admin-topup-email">{order.userEmail}</p> : null}
      </td>
      <td className="admin-topup-value-cell">
        <p className="font-semibold tabular-nums">{formatVnd(order.amountVnd)}</p>
        <p className="text-xs text-text-muted">{order.credits} Credit</p>
      </td>
      <td className="admin-topup-provider-code">{displayProviderTxnId(order.providerTxnId)}</td>
      <td>
        <StatusPill order={order} />
      </td>
      <td className="admin-topup-time-cell">
        <p>
          <span>Tạo:</span> {formatDateTime(order.createdAt)}
        </p>
        {order.paidAt ? (
          <p>
            <span>Thanh toán:</span> {formatDateTime(order.paidAt)}
          </p>
        ) : null}
      </td>
      <td className="admin-topup-note-cell" title={order.reviewNote || undefined}>
        {order.reviewNote || '—'}
      </td>
      <td className="text-center">
        <button
          type="button"
          className="admin-topup-view-action"
          onClick={(event) => {
            event.stopPropagation();
            onOpen(order.orderCode);
          }}
        >
          Xem chi tiết
        </button>
      </td>
    </tr>
  );
}

function DetailRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="admin-topup-detail-item">
      <dt>{label}</dt>
      <dd>{children}</dd>
    </div>
  );
}

const actionTexts: Record<AdminTopUpAction, { button: string; title: string; hint: string }> = {
  GRANT_CREDIT: {
    button: 'Xác nhận đã thu tiền và cộng Credit',
    title: 'Cộng Credit cho đơn này',
    hint: 'Đơn chuyển sang thành công và người nạp nhận đủ Credit như nạp thường. Mỗi đơn chỉ xử lý một lần.',
  },
  REFUNDED_OUTSIDE: {
    button: 'Đánh dấu đã hoàn tiền ngoài hệ thống',
    title: 'Ghi nhận đã hoàn tiền',
    hint: 'Đơn giữ nguyên trạng thái và không cộng Credit. Mỗi đơn chỉ xử lý một lần.',
  },
};

function OrderDialog({
  orderCode,
  ledgerReturn,
  onClose,
  onChanged,
}: {
  orderCode: string;
  ledgerReturn?: AdminLedgerViewState;
  onClose: () => void;
  onChanged: () => void;
}) {
  const [detail, setDetail] = useState<AdminTopUpOrderDetail | null>(null);
  const [error, setError] = useState('');
  const [action, setAction] = useState<AdminTopUpAction | null>(null);
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  const inFlight = useRef(false);

  useEffect(() => {
    let active = true;
    adminFinanceApi
      .topUpOrderDetail(orderCode)
      .then((response) => {
        if (active) setDetail(response);
      })
      .catch((caught) => {
        if (active) setError(adminErrorMessage(caught, 'Không tải được chi tiết đơn.'));
      });
    return () => {
      active = false;
    };
  }, [orderCode]);

  const trimmed = note.trim();

  const submit = async () => {
    if (!action || inFlight.current) return;
    if (trimmed.length < REASON_MIN) {
      setError(`Lý do cần từ ${REASON_MIN} đến ${REASON_MAX} ký tự.`);
      return;
    }
    inFlight.current = true;
    setBusy(true);
    setError('');
    try {
      const updated = await adminFinanceApi.reconcileTopUp(orderCode, { action, note: trimmed });
      setDetail(updated);
      setAction(null);
      setNote('');
      onChanged();
    } catch (caught) {
      setError(adminErrorMessage(caught, 'Không xử lý được đơn. Vui lòng thử lại.'));
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  };

  const order = detail?.order;
  return (
    <Dialog title="Chi tiết đơn nạp" wide busy={busy} onClose={onClose}>
      {ledgerReturn ? (
        <div className="mb-4">
          <Link
            to="/admin/finance/ledger"
            state={{ restoreLedger: ledgerReturn }}
            className="inline-flex min-h-10 items-center rounded-md border border-border px-3 text-sm font-semibold text-primary hover:bg-surface-low"
          >
            Về lịch sử giao dịch Credit
          </Link>
        </div>
      ) : null}
      {!order ? (
        error ? (
          <Alert tone="error">{error}</Alert>
        ) : (
          <SkeletonRows rows={3} label="Đang tải chi tiết đơn" />
        )
      ) : (
        <div className="admin-topup-dialog-content">
          <dl className="admin-topup-detail-grid">
            <DetailRow label="Mã đơn nạp">
              <span className="inline-flex items-center gap-2">
                <span className="break-all font-mono text-xs">{order.orderCode}</span>
                <CopyCodeButton orderCode={order.orderCode} />
              </span>
            </DetailRow>
            <DetailRow label="Thông tin người nạp">
              <p className="font-semibold text-text-primary">
                {order.userName || 'Người dùng đã xóa'}
              </p>
              {order.userEmail ? (
                <p className="break-all text-xs text-text-muted">{order.userEmail}</p>
              ) : null}
            </DetailRow>
            <DetailRow label="Số tiền thanh toán">
              <span className="font-semibold tabular-nums text-text-primary">
                {formatVnd(order.amountVnd)}
              </span>
            </DetailRow>
            <DetailRow label="Số Credit của đơn nạp">
              <span className="font-semibold tabular-nums text-text-primary">
                {order.credits} Credit
              </span>
            </DetailRow>
            {detail.ledgerLines.length > 0 ? (
              <DetailRow label="Số Credit thực tế đã cộng">
                <div className="space-y-1">
                  {detail.ledgerLines.map((line) => (
                    <p key={line.id}>
                      <span className="font-bold tabular-nums text-success">
                        +{line.amount} Credit
                      </span>
                      <span className="block text-xs text-text-muted">
                        {formatDateTime(line.createdAt)}
                      </span>
                      <span className="block text-xs text-text-muted">
                        Số dư sau: {line.balanceAfter} Credit
                      </span>
                    </p>
                  ))}
                </div>
              </DetailRow>
            ) : null}
            <DetailRow label="Phương thức thanh toán">{providerLabels[order.provider]}</DetailRow>
            <DetailRow label="Mã giao dịch VNPay">
              <span className="break-all font-mono text-xs">
                {displayProviderTxnId(order.providerTxnId)}
              </span>
            </DetailRow>
            <DetailRow label="Trạng thái thanh toán">
              <StatusPill order={order} />
            </DetailRow>
            <DetailRow label="Thời gian tạo đơn">{formatDateTime(order.createdAt)}</DetailRow>
            {order.paidAt ? (
              <DetailRow label="Thời gian thanh toán">{formatDateTime(order.paidAt)}</DetailRow>
            ) : null}
            {order.needsReconcile || order.reviewedAt ? (
              <DetailRow label="Trạng thái đối soát">
                {order.needsReconcile ? (
                  <span className="font-semibold text-error">Cần đối soát</span>
                ) : (
                  <>
                    <p className="font-semibold text-success">Đã xử lý đối soát</p>
                    {order.reviewedAt ? <p>{formatDateTime(order.reviewedAt)}</p> : null}
                    {order.reviewedByName ? (
                      <p className="text-xs text-text-muted">Bởi {order.reviewedByName}</p>
                    ) : null}
                  </>
                )}
              </DetailRow>
            ) : null}
            {order.reviewNote ? (
              <DetailRow label="Ghi chú đối soát">{order.reviewNote}</DetailRow>
            ) : null}
          </dl>

          {error ? (
            <div role="alert" className="rounded-md bg-error-soft p-3 text-xs text-error">
              {error}
            </div>
          ) : null}

          {order.needsReconcile && !action ? (
            <div className="flex flex-wrap justify-end gap-2">
              <Button variant="outline" onClick={() => setAction('REFUNDED_OUTSIDE')}>
                {actionTexts.REFUNDED_OUTSIDE.button}
              </Button>
              <Button onClick={() => setAction('GRANT_CREDIT')}>
                {actionTexts.GRANT_CREDIT.button}
              </Button>
            </div>
          ) : null}

          {action ? (
            <div className="rounded-lg bg-surface-low p-4">
              <h3 className="text-sm font-bold text-text-primary">{actionTexts[action].title}</h3>
              <p className="mt-1 text-xs text-text-secondary">{actionTexts[action].hint}</p>
              <TextArea
                className="mt-3"
                label="Lý do hoặc ghi chú (bắt buộc)"
                value={note}
                maxLength={REASON_MAX}
                disabled={busy}
                onChange={(event) => setNote(event.target.value)}
              />
              <p className="mt-1 text-right text-xs text-text-muted">
                {note.length}/{REASON_MAX}
              </p>
              <div className="mt-3 flex justify-end gap-2">
                <Button
                  variant="outline"
                  disabled={busy}
                  onClick={() => {
                    setAction(null);
                    setNote('');
                    setError('');
                  }}
                >
                  Quay lại
                </Button>
                <Button
                  disabled={busy || trimmed.length < REASON_MIN}
                  onClick={() => void submit()}
                >
                  {busy ? 'Đang xử lý...' : 'Xác nhận'}
                </Button>
              </div>
            </div>
          ) : null}
        </div>
      )}
    </Dialog>
  );
}
