import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Dialog } from '../../components/Dialog';
import { Pagination } from '../../components/Pagination';
import {
  Alert,
  Button,
  Field,
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

// Tên dễ đọc cho các khoá callback VNPay mà Backend cho phép trả về.
const callbackLabels: Record<string, string> = {
  vnp_ResponseCode: 'Mã phản hồi',
  vnp_TransactionStatus: 'Trạng thái giao dịch',
  vnp_TransactionNo: 'Mã giao dịch VNPay',
  vnp_BankCode: 'Ngân hàng',
  vnp_PayDate: 'Ngày thanh toán',
  vnp_Amount: 'Số tiền (nhân 100)',
};

function statusClass(status: PaymentOrderStatus) {
  if (status === 'SUCCESS') return 'bg-success-soft text-success';
  if (status === 'PENDING') return 'bg-warning-soft text-warning';
  if (status === 'FAILED') return 'bg-error-soft text-error';
  return 'bg-surface-low text-text-muted';
}

function formatDateTime(value: string | null) {
  return value ? new Date(value).toLocaleString('vi-VN') : '—';
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

const initialKeyword = () => new URLSearchParams(window.location.search).get('q') ?? '';

export function AdminTopUpOrders() {
  const location = useLocation();
  const routeState = location.state as AdminLedgerRouteState | null;
  const ledgerReturn = routeState?.ledgerReturn;
  const [result, setResult] = useState<PageResponse<AdminTopUpOrder> | null>(null);
  const [summary, setSummary] = useState<AdminTopUpSummary | null>(null);
  // '' là tất cả trạng thái: mở trang là thấy toàn bộ đơn, đơn mới nhất ở trên.
  const [status, setStatus] = useState<PaymentOrderStatus | ''>('');
  // Trang Lịch sử giao dịch Credit mở sang đây kèm ?q=mã đơn, nên lấy từ khoá ban đầu từ địa chỉ.
  const [keywordInput, setKeywordInput] = useState(initialKeyword);
  const [keyword, setKeyword] = useState(initialKeyword);
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [needsReconcile, setNeedsReconcile] = useState(false);
  const [page, setPage] = useState(0);
  const [reloadKey, setReloadKey] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [exporting, setExporting] = useState(false);
  const [openedCode, setOpenedCode] = useState<string | null>(routeState?.openTopUpOrder ?? null);

  const dateRangeError =
    fromDate && toDate && fromDate > toDate ? '"Từ ngày" không được sau "Đến ngày".' : '';

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
    if (needsReconcile) filters.needsReconcile = true;
    return filters;
  }, [status, keyword, fromDate, toDate, needsReconcile]);

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
      .topUpSummary({ from: fromDate || undefined, to: toDate || undefined })
      .then((response) => {
        if (active) setSummary(response);
      })
      .catch(() => {
        if (active) setSummary(null);
      });
    return () => {
      active = false;
    };
  }, [dateRangeError, fromDate, toDate, reloadKey]);

  const reload = () => setReloadKey((current) => current + 1);

  const exportCsv = async () => {
    setExporting(true);
    setError('');
    try {
      const download = await adminFinanceApi.topUpOrdersCsv(buildFilters());
      downloadBlob(download.blob, download.fileName);
    } catch (caught) {
      setError(adminErrorMessage(caught, 'Không xuất được file. Vui lòng thử lại.'));
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
    <section className="space-y-5">
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
            <Button variant="outline" disabled={exporting} onClick={() => void exportCsv()}>
              {exporting ? 'Đang xuất...' : 'Xuất CSV'}
            </Button>
            <Button variant="outline" disabled={loading} onClick={reload}>
              Tải lại
            </Button>
          </div>
        }
      />

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

      <div className="grid gap-4 rounded-lg bg-white p-4 ring-1 ring-border md:grid-cols-2 lg:grid-cols-4">
        <div className="md:col-span-2 lg:col-span-4">
          <SearchField
            aria-label="Tìm đơn nạp"
            placeholder="Tìm theo mã đơn, tên hoặc email người nạp..."
            value={keywordInput}
            onChange={(event) => setKeywordInput(event.target.value)}
          />
        </div>
        <Select
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
        <Field
          label="Từ ngày"
          type="date"
          value={fromDate}
          error={dateRangeError || undefined}
          onChange={(event) => {
            setFromDate(event.target.value);
            setPage(0);
          }}
        />
        <Field
          label="Đến ngày"
          type="date"
          value={toDate}
          onChange={(event) => {
            setToDate(event.target.value);
            setPage(0);
          }}
        />
        <label className="flex items-center gap-2 self-end pb-2 text-sm font-medium text-text-primary">
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
        <div>
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Mã đơn</th>
                  <th>Người nạp</th>
                  <th>Giá trị</th>
                  <th>Mã giao dịch VNPay</th>
                  <th>Trạng thái</th>
                  <th>Thời gian</th>
                  <th>Ghi chú</th>
                </tr>
              </thead>
              <tbody>
                {result.items.map((order) => (
                  <TopUpRow key={order.orderCode} order={order} onOpen={setOpenedCode} />
                ))}
                {result.items.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="text-center text-text-muted">
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
  const cardClass = (active: boolean) =>
    `rounded-lg bg-white p-3 text-left ring-1 transition hover:ring-primary ${active ? 'ring-2 ring-primary' : 'ring-border'}`;
  return (
    <div className="grid gap-3 sm:grid-cols-3 lg:grid-cols-6">
      <button type="button" className={cardClass(reconcileOnly)} onClick={onReconcile}>
        <p className="text-xs text-text-muted">Cần đối soát</p>
        <p
          className={`text-xl font-bold tabular-nums ${summary.needsReconcileCount > 0 ? 'text-error' : 'text-text-primary'}`}
        >
          {summary.needsReconcileCount}
        </p>
        <p className="text-xs text-text-muted">đơn</p>
      </button>
      {summary.byStatus.map((total) => (
        <button
          key={total.status}
          type="button"
          className={cardClass(activeStatus === total.status)}
          onClick={() => onStatus(total.status)}
        >
          <p className="text-xs text-text-muted">{statusLabels[total.status]}</p>
          <p className="text-xl font-bold tabular-nums text-text-primary">{total.orderCount}</p>
          <p className="text-xs text-text-muted tabular-nums">{formatVnd(total.amountVnd)}</p>
        </button>
      ))}
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
      aria-label="Sao chép mã đơn"
      className="text-xs font-semibold text-primary hover:underline"
      onClick={(event) => {
        event.stopPropagation();
        void copy();
      }}
    >
      {copied ? 'Đã chép' : 'Chép'}
    </button>
  );
}

function StatusPill({ order }: { order: AdminTopUpOrder }) {
  return (
    <div className="flex flex-col items-start gap-1">
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
    <tr className="cursor-pointer hover:bg-surface-low" onClick={() => onOpen(order.orderCode)}>
      <td>
        <div className="flex items-center gap-2">
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
      <td>
        <p className="font-semibold text-text-primary">{order.userName || 'Người dùng đã xóa'}</p>
        {order.userEmail ? <p className="text-xs text-text-muted">{order.userEmail}</p> : null}
      </td>
      <td>
        <p className="font-semibold tabular-nums">{formatVnd(order.amountVnd)}</p>
        <p className="text-xs text-text-muted">{order.credits} Credit</p>
      </td>
      <td className="text-xs">{order.providerTxnId || '—'}</td>
      <td>
        <StatusPill order={order} />
      </td>
      <td className="text-xs">
        <p>Tạo lúc: {formatDateTime(order.createdAt)}</p>
        <p>Thanh toán lúc: {formatDateTime(order.paidAt)}</p>
      </td>
      <td className="max-w-56 text-xs text-text-muted">{order.reviewNote || '—'}</td>
    </tr>
  );
}

function DetailRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex flex-wrap justify-between gap-2 py-2 text-sm">
      <dt className="text-text-muted">{label}</dt>
      <dd className="text-right font-medium text-text-primary">{children}</dd>
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
        <div className="space-y-4">
          <dl className="divide-y divide-border/60">
            <DetailRow label="Mã đơn">
              <span className="inline-flex items-center gap-2">
                <span className="break-all font-mono text-xs">{order.orderCode}</span>
                <CopyCodeButton orderCode={order.orderCode} />
              </span>
            </DetailRow>
            <DetailRow label="Người nạp">
              {order.userName || 'Người dùng đã xóa'}
              {order.userEmail ? ` (${order.userEmail})` : ''}
            </DetailRow>
            <DetailRow label="Giá trị">
              {formatVnd(order.amountVnd)} cho {order.credits} Credit
            </DetailRow>
            <DetailRow label="Cổng thanh toán">{providerLabels[order.provider]}</DetailRow>
            <DetailRow label="Trạng thái">
              <StatusPill order={order} />
            </DetailRow>
            <DetailRow label="Tạo lúc">{formatDateTime(order.createdAt)}</DetailRow>
            <DetailRow label="VNPay báo thu tiền lúc">{formatDateTime(order.paidAt)}</DetailRow>
            {order.reviewedAt ? (
              <DetailRow label="Đã đối soát">
                {formatDateTime(order.reviewedAt)} bởi {order.reviewedByName || 'quản trị viên'}
              </DetailRow>
            ) : null}
            {order.reviewNote ? <DetailRow label="Ghi chú">{order.reviewNote}</DetailRow> : null}
          </dl>

          {Object.keys(detail.callbackData).length > 0 ? (
            <div>
              <h3 className="text-sm font-bold text-text-primary">Phản hồi từ VNPay</h3>
              <dl className="mt-1 divide-y divide-border/60">
                {Object.entries(detail.callbackData).map(([key, value]) => (
                  <DetailRow key={key} label={callbackLabels[key] ?? key}>
                    {value}
                  </DetailRow>
                ))}
              </dl>
            </div>
          ) : null}

          {detail.ledgerLines.length > 0 ? (
            <div>
              <h3 className="text-sm font-bold text-text-primary">Credit đã cộng</h3>
              <ul className="mt-1 space-y-1 text-sm">
                {detail.ledgerLines.map((line) => (
                  <li key={line.id}>
                    +{line.amount} Credit lúc {formatDateTime(line.createdAt)}, số dư sau đó {line.balanceAfter}
                  </li>
                ))}
              </ul>
            </div>
          ) : null}

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
                <Button disabled={busy || trimmed.length < REASON_MIN} onClick={() => void submit()}>
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
