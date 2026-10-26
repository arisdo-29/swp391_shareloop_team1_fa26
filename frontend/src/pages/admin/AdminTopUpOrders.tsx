import { useCallback, useEffect, useRef, useState } from 'react';
import { Dialog } from '../../components/Dialog';
import { Pagination } from '../../components/Pagination';
import { Alert, Button, EmptyState, PageHeader, TextArea } from '../../components/ui';
import { adminFinanceApi } from '../../services/adminFinanceApi';
import type { ApiError } from '../../services/http';
import type { PageResponse } from '../../services/itemApi';
import type { PaymentOrderStatus, TopUpOrderDetailResponse } from '../../services/paymentApi';
import { formatVnd } from '../../utils/formatting';

const PAGE_SIZE = 10;

const statusLabels: Record<PaymentOrderStatus, string> = {
  PENDING: 'Chờ duyệt',
  SUCCESS: 'Thành công',
  FAILED: 'Thất bại',
  EXPIRED: 'Hết hạn',
};

function statusLabel(order: TopUpOrderDetailResponse) {
  // Đơn FAILED đã có người duyệt nghĩa là admin từ chối, đơn FAILED không có người duyệt là thanh toán lỗi.
  if (order.status === 'FAILED' && order.reviewedBy !== null) return 'Từ chối';
  return statusLabels[order.status];
}

function statusClass(status: PaymentOrderStatus) {
  if (status === 'SUCCESS') return 'bg-success-soft text-success';
  if (status === 'PENDING') return 'bg-warning-soft text-warning';
  if (status === 'FAILED') return 'bg-error-soft text-error';
  return 'bg-surface-low text-text-muted';
}

function formatDateTime(value: string | null) {
  return value ? new Date(value).toLocaleString('vi-VN') : '—';
}

function getErrorMessage(error: unknown) {
  const apiError = error as Partial<ApiError>;
  if (apiError.status === 403) return 'Bạn không có quyền quản lý đơn nạp Credit.';
  if (apiError.code === 'WALLET_PAYMENT_ORDER_NOT_PAYABLE') {
    return 'Đơn nạp không còn đủ điều kiện xử lý. Vui lòng tải lại danh sách.';
  }
  return apiError.message || 'Không xử lý được yêu cầu. Vui lòng thử lại.';
}

/** Chỉ đơn chuyển khoản thủ công đã được người dùng bấm "đã thanh toán" mới duyệt được. */
function canReview(order: TopUpOrderDetailResponse) {
  return order.provider === 'MANUAL' && order.status === 'PENDING' && order.submittedAt !== null;
}

export function AdminTopUpOrders() {
  const [result, setResult] = useState<PageResponse<TopUpOrderDetailResponse> | null>(null);
  // '' là tất cả trạng thái: mở trang là thấy toàn bộ đơn, đơn mới nhất ở trên.
  const [status, setStatus] = useState<PaymentOrderStatus | ''>('');
  const [submittedOnly, setSubmittedOnly] = useState(false);
  const [page, setPage] = useState(0);
  const [reloadKey, setReloadKey] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [selected, setSelected] = useState<{
    order: TopUpOrderDetailResponse;
    action: 'approve' | 'reject';
  } | null>(null);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');

    adminFinanceApi
      .topUpOrders({
        status: status || undefined,
        submittedOnly: submittedOnly || undefined,
        page,
        size: PAGE_SIZE,
      })
      .then((response) => {
        if (!active) return;
        if (response.items.length === 0 && page > 0) {
          setPage((current) => Math.max(0, current - 1));
          return;
        }
        setResult(response);
      })
      .catch((caught: ApiError) => {
        if (!active) return;
        setResult(null);
        setError(getErrorMessage(caught));
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [page, reloadKey, status, submittedOnly]);

  const closeDialog = useCallback(() => setSelected(null), []);

  const onProcessed = (message: string) => {
    setSelected(null);
    setNotice(message);
    setReloadKey((current) => current + 1);
  };

  return (
    <section className="space-y-5">
      <PageHeader
        title="Đơn nạp Credit"
        description="Theo dõi mọi đơn nạp và duyệt các đơn chuyển khoản thủ công đã được người dùng xác nhận thanh toán."
        action={
          <Button
            variant="outline"
            disabled={loading}
            onClick={() => setReloadKey((current) => current + 1)}
          >
            Tải lại
          </Button>
        }
      />

      <div className="flex flex-wrap items-end gap-4 rounded-lg bg-white p-4 ring-1 ring-border">
        <label className="grid gap-1.5 text-sm font-medium">
          Trạng thái
          <select
            className="rounded-md border border-border bg-white px-3 py-2"
            value={status}
            onChange={(event) => {
              setStatus(event.target.value as PaymentOrderStatus | '');
              setPage(0);
            }}
          >
            <option value="">Tất cả</option>
            {Object.entries(statusLabels).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <label className="flex items-center gap-2 pb-2 text-sm font-medium">
          <input
            type="checkbox"
            checked={submittedOnly}
            onChange={(event) => {
              setSubmittedOnly(event.target.checked);
              setPage(0);
            }}
          />
          Chỉ đơn đã xác nhận thanh toán
        </label>
      </div>

      {error ? (
        <div className="space-y-3">
          <Alert tone="error">{error}</Alert>
          <Button variant="outline" onClick={() => setReloadKey((current) => current + 1)}>
            Thử lại
          </Button>
        </div>
      ) : null}
      {notice ? (
        <div role="status">
          <Alert tone="success">{notice}</Alert>
        </div>
      ) : null}

      {loading && !result ? (
        <EmptyState title="Đang tải đơn nạp" text="Vui lòng chờ trong giây lát." />
      ) : result ? (
        <div>
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Người dùng</th>
                  <th>Giá trị</th>
                  <th>Trạng thái</th>
                  <th>Thời gian</th>
                  <th>Kết quả duyệt</th>
                  <th>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {result.items.map((order) => (
                  <TopUpRow
                    key={order.orderCode}
                    order={order}
                    onAction={(action) => {
                      setNotice('');
                      setError('');
                      setSelected({ order, action });
                    }}
                  />
                ))}
                {result.items.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="text-center text-text-muted">
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

      {selected ? (
        <ReviewDialog
          order={selected.order}
          action={selected.action}
          onClose={closeDialog}
          onDone={onProcessed}
        />
      ) : null}
    </section>
  );
}

function TopUpRow({
  order,
  onAction,
}: {
  order: TopUpOrderDetailResponse;
  onAction: (action: 'approve' | 'reject') => void;
}) {
  const reviewed = order.reviewedAt !== null;
  return (
    <tr>
      <td>
        <p className="font-semibold text-text-primary">{order.userName || 'Người dùng đã xóa'}</p>
        {order.userEmail ? <p className="text-xs text-text-muted">{order.userEmail}</p> : null}
      </td>
      <td>
        <p className="font-semibold tabular-nums">{formatVnd(order.amountVnd)}</p>
        <p className="text-xs text-text-muted">{order.credits} Credit</p>
      </td>
      <td>
        <span
          className={`inline-flex whitespace-nowrap rounded-sm px-2 py-1 text-2xs font-semibold ${statusClass(order.status)}`}
        >
          {statusLabel(order)}
        </span>
      </td>
      <td className="text-xs">
        <p>Tạo lúc: {formatDateTime(order.createdAt)}</p>
        {order.submittedAt ? <p>Xác nhận thanh toán: {formatDateTime(order.submittedAt)}</p> : null}
        {order.status === 'PENDING' ? <p>Hết hạn: {formatDateTime(order.expiresAt)}</p> : null}
      </td>
      <td className="text-xs">
        {reviewed ? (
          <>
            <p>Người duyệt: {order.reviewedByName || 'Quản trị viên đã xóa'}</p>
            <p>Lúc: {formatDateTime(order.reviewedAt)}</p>
            {order.reviewNote ? <p className="max-w-52 break-words">Ghi chú: {order.reviewNote}</p> : null}
          </>
        ) : (
          '—'
        )}
      </td>
      <td>
        {canReview(order) ? (
          <div className="flex flex-wrap gap-2">
            <Button onClick={() => onAction('approve')}>
              Phê duyệt
            </Button>
            <Button variant="danger" onClick={() => onAction('reject')}>
              Từ chối
            </Button>
          </div>
        ) : (
          <span className="text-text-muted">—</span>
        )}
      </td>
    </tr>
  );
}

function ReviewDialog({
  order,
  action,
  onClose,
  onDone,
}: {
  order: TopUpOrderDetailResponse;
  action: 'approve' | 'reject';
  onClose: () => void;
  onDone: (message: string) => void;
}) {
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const inFlight = useRef(false);
  const approving = action === 'approve';
  const reviewText = note.trim();

  const submit = async () => {
    if (inFlight.current) return;
    if (!approving && !reviewText) {
      setError('Vui lòng nhập lý do từ chối.');
      return;
    }
    inFlight.current = true;
    setBusy(true);
    setError('');
    try {
      if (approving) {
        await adminFinanceApi.approveTopUp(order.orderCode, reviewText);
      } else {
        await adminFinanceApi.rejectTopUp(order.orderCode, reviewText);
      }
      onDone(approving ? 'Đã phê duyệt đơn nạp và cộng Credit cho người dùng.' : 'Đã từ chối đơn nạp.');
    } catch (caught) {
      setError(getErrorMessage(caught));
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  };

  return (
    <Dialog title="Xác nhận xử lý đơn nạp" busy={busy} onClose={onClose}>
      <p className="text-sm leading-6 text-text-secondary">
        {approving ? 'Phê duyệt' : 'Từ chối'} đơn nạp <strong>{formatVnd(order.amountVnd)}</strong> (
        {order.credits} Credit) của <strong>{order.userName || 'người dùng này'}</strong>?
      </p>
      <div className="mt-4">
        <TextArea
          label={approving ? 'Ghi chú (tùy chọn)' : 'Lý do từ chối (bắt buộc)'}
          maxLength={500}
          disabled={busy}
          value={note}
          onChange={(event) => setNote(event.target.value)}
        />
      </div>
      {error ? (
        <div className="mt-3" role="alert">
          <Alert tone="error">{error}</Alert>
        </div>
      ) : null}
      <div className="mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        <Button variant="outline" disabled={busy} onClick={onClose}>
          Hủy
        </Button>
        <Button
          variant={approving ? 'primary' : 'danger'}
          disabled={busy || (!approving && !reviewText)}
          onClick={() => void submit()}
        >
          {busy ? 'Đang xử lý...' : 'Xác nhận'}
        </Button>
      </div>
    </Dialog>
  );
}
