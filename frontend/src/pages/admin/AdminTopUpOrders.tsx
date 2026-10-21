import { useEffect, useRef, useState } from 'react';
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

export function AdminTopUpOrders({ onProcessed }: { onProcessed?: () => void } = {}) {
  const [result, setResult] = useState<PageResponse<TopUpOrderDetailResponse> | null>(null);
  const [status, setStatus] = useState<PaymentOrderStatus>('PENDING');
  const [submittedOnly, setSubmittedOnly] = useState(true);
  const [page, setPage] = useState(0);
  const [reloadKey, setReloadKey] = useState(0);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [note, setNote] = useState('');
  const [selected, setSelected] = useState<{
    order: TopUpOrderDetailResponse;
    action: 'approve' | 'reject';
  } | null>(null);
  const inFlight = useRef(false);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');

    adminFinanceApi
      .topUpOrders({ status, submittedOnly, page, size: PAGE_SIZE })
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

  const openReview = (order: TopUpOrderDetailResponse, action: 'approve' | 'reject') => {
    setSelected({ order, action });
    setNote('');
    setError('');
    setNotice('');
  };

  const processOrder = async () => {
    if (!selected || inFlight.current) return;
    const reviewText = note.trim();
    if (selected.action === 'reject' && !reviewText) {
      setError('Vui lòng nhập lý do từ chối.');
      return;
    }

    inFlight.current = true;
    setBusy(true);
    setError('');
    setNotice('');
    try {
      if (selected.action === 'approve') {
        await adminFinanceApi.approveTopUp(selected.order.orderCode, reviewText);
      } else {
        await adminFinanceApi.rejectTopUp(selected.order.orderCode, reviewText);
      }
      setNotice(
        selected.action === 'approve'
          ? 'Đã phê duyệt đơn nạp. Backend đã cập nhật Credit.'
          : 'Đã từ chối đơn nạp.',
      );
      setSelected(null);
      setNote('');
      onProcessed?.();
      setReloadKey((current) => current + 1);
    } catch (caught) {
      setError(getErrorMessage(caught));
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  };

  const totalPages = result ? Math.max(1, Math.ceil(result.totalElements / result.size)) : 1;
  const reloadButton = (
    <Button
      variant="outline"
      disabled={busy || loading}
      onClick={() => setReloadKey((current) => current + 1)}
    >
      Tải lại
    </Button>
  );

  return (
    <section className={onProcessed ? 'mb-8 space-y-5' : 'space-y-5'}>
      {onProcessed ? (
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="section-title">Đơn nạp Credit</h2>
          {reloadButton}
        </div>
      ) : (
        <PageHeader
          title="Đơn nạp Credit"
          description="Duyệt các đơn chuyển khoản thủ công đã được người dùng xác nhận thanh toán."
          action={reloadButton}
        />
      )}

      <div className="flex flex-wrap items-end gap-4 rounded-lg bg-white p-4 ring-1 ring-border">
        <label className="grid gap-1.5 text-sm font-medium">
          Trạng thái
          <select
            className="rounded-md border border-border bg-white px-3 py-2"
            value={status}
            disabled={busy}
            onChange={(event) => {
              setStatus(event.target.value as PaymentOrderStatus);
              setPage(0);
              setSelected(null);
            }}
          >
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
            disabled={busy}
            onChange={(event) => {
              setSubmittedOnly(event.target.checked);
              setPage(0);
              setSelected(null);
            }}
          />
          Chỉ đơn đã xác nhận thanh toán
        </label>
      </div>

      {error ? <Alert tone="error">{error}</Alert> : null}
      {notice ? <Alert tone="success">{notice}</Alert> : null}

      {loading ? (
        <EmptyState title="Đang tải đơn nạp" text="Vui lòng chờ trong giây lát." />
      ) : result ? (
        <div className="space-y-4">
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Đơn / người dùng</th>
                  <th>Giá trị</th>
                  <th>Nguồn / trạng thái</th>
                  <th>Thời gian</th>
                  <th>Kết quả duyệt</th>
                  <th>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {result.items.map((order) => {
                  const canReview =
                    order.provider === 'MANUAL' &&
                    order.status === 'PENDING' &&
                    order.submittedAt !== null;
                  return (
                    <tr key={order.orderCode}>
                      <td>
                        <p className="break-all font-semibold">{order.orderCode}</p>
                        <p className="text-xs text-text-muted">User #{order.userId}</p>
                      </td>
                      <td>
                        <p className="font-semibold">{formatVnd(order.amountVnd)}</p>
                        <p className="text-xs text-text-muted">
                          {order.credits} Credit · {formatVnd(order.vndPerCredit)}/Credit
                        </p>
                      </td>
                      <td>
                        <p>{order.provider}</p>
                        <p className="text-xs text-text-muted">{statusLabels[order.status]}</p>
                      </td>
                      <td className="text-xs">
                        <p>Tạo: {formatDateTime(order.createdAt)}</p>
                        <p>Hết hạn: {formatDateTime(order.expiresAt)}</p>
                        <p>Đã gửi: {formatDateTime(order.submittedAt)}</p>
                      </td>
                      <td className="text-xs">
                        <p>Admin: {order.reviewedBy ?? '—'}</p>
                        <p>Lúc: {formatDateTime(order.reviewedAt)}</p>
                        <p className="max-w-52 break-words">Ghi chú: {order.reviewNote || '—'}</p>
                      </td>
                      <td>
                        {canReview ? (
                          <div className="flex flex-wrap gap-2">
                            <Button disabled={busy} onClick={() => openReview(order, 'approve')}>
                              Phê duyệt
                            </Button>
                            <Button
                              variant="danger"
                              disabled={busy}
                              onClick={() => openReview(order, 'reject')}
                            >
                              Từ chối
                            </Button>
                          </div>
                        ) : (
                          <span className="text-xs text-text-muted">Không thể duyệt thủ công</span>
                        )}
                      </td>
                    </tr>
                  );
                })}
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

          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-sm text-text-muted">
              {result.totalElements} đơn · Trang {result.page + 1}/{totalPages}
            </p>
            <div className="flex gap-2">
              <Button
                variant="outline"
                disabled={busy || page === 0}
                onClick={() => setPage((current) => Math.max(0, current - 1))}
              >
                Trang trước
              </Button>
              <Button
                variant="outline"
                disabled={busy || (page + 1) * result.size >= result.totalElements}
                onClick={() => setPage((current) => current + 1)}
              >
                Trang sau
              </Button>
            </div>
          </div>
        </div>
      ) : null}

      {selected ? (
        <div
          className="space-y-3 rounded-lg bg-surface-low p-4 ring-1 ring-border"
          role="dialog"
          aria-label="Xác nhận xử lý đơn nạp"
        >
          <p>
            {selected.action === 'approve' ? 'Phê duyệt' : 'Từ chối'} đơn{' '}
            <strong>{selected.order.orderCode}</strong> của người dùng #{selected.order.userId}?
          </p>
          <TextArea
            label={selected.action === 'approve' ? 'Ghi chú (tùy chọn)' : 'Lý do từ chối (bắt buộc)'}
            maxLength={500}
            disabled={busy}
            value={note}
            onChange={(event) => setNote(event.target.value)}
          />
          <div className="flex gap-3">
            <Button
              variant={selected.action === 'reject' ? 'danger' : 'primary'}
              disabled={busy || (selected.action === 'reject' && !note.trim())}
              onClick={() => void processOrder()}
            >
              {busy ? 'Đang xử lý...' : 'Xác nhận'}
            </Button>
            <Button variant="outline" disabled={busy} onClick={() => setSelected(null)}>
              Hủy
            </Button>
          </div>
        </div>
      ) : null}
    </section>
  );
}
