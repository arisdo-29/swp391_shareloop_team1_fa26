import { useEffect, useRef, useState } from 'react';
import { Alert, Button, TextArea } from '../../components/ui';
import { adminFinanceApi } from '../../services/adminFinanceApi';
import type { ApiError } from '../../services/http';
import type { PageResponse } from '../../services/itemApi';
import type { PaymentOrderStatus, TopUpOrderDetailResponse } from '../../services/paymentApi';
import { formatVnd } from '../../utils/formatting';

const labels: Record<PaymentOrderStatus, string> = {
  PENDING: 'Chờ admin duyệt',
  SUCCESS: 'Đã cộng Credit',
  FAILED: 'Yêu cầu bị từ chối',
  EXPIRED: 'Đơn đã hết hạn',
};

export function AdminTopUpOrders({ onProcessed }: { onProcessed: () => void }) {
  const [result, setResult] = useState<PageResponse<TopUpOrderDetailResponse> | null>(null);
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState<PaymentOrderStatus>('PENDING');
  const [reload, setReload] = useState(0);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [note, setNote] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [selected, setSelected] = useState<{
    order: TopUpOrderDetailResponse;
    approve: boolean;
  } | null>(null);
  const inFlight = useRef(false);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    adminFinanceApi
      .topUpOrders({ page, size: 10, status, submittedOnly: true })
      .then((response) => {
        if (!active) return;
        if (response.items.length === 0 && page > 0) {
          setPage(page - 1);
          return;
        }
        setResult(response);
      })
      .catch((caught: ApiError) => {
        if (active) {
          setResult(null);
          setError(caught.message || 'Không tải được yêu cầu nạp.');
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [page, status, reload]);

  const process = async () => {
    if (!selected || inFlight.current) return;
    if (!selected.approve && !note.trim()) {
      setError('Vui lòng nhập lý do từ chối.');
      return;
    }
    inFlight.current = true;
    setBusy(true);
    setError('');
    setNotice('');
    try {
      if (selected.approve)
        await adminFinanceApi.approveTopUp(selected.order.orderCode, note.trim() || undefined);
      else await adminFinanceApi.rejectTopUp(selected.order.orderCode, note.trim());
      setNotice(selected.approve ? 'Đã duyệt và cộng Credit.' : 'Đã từ chối yêu cầu.');
      setSelected(null);
      setNote('');
      onProcessed();
      setReload((value) => value + 1);
    } catch (caught) {
      setError((caught as ApiError).message || 'Không xử lý được yêu cầu.');
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  };

  return (
    <section className="mb-8 space-y-4 rounded-lg bg-white p-5 ring-1 ring-border">
      <h2 className="section-title">Yêu cầu nạp Credit</h2>
      <div className="flex flex-wrap items-center gap-3">
        <label>
          Trạng thái{' '}
          <select
            className="rounded-md border border-border p-2"
            value={status}
            disabled={busy}
            onChange={(event) => {
              setStatus(event.target.value as PaymentOrderStatus);
              setPage(0);
              setSelected(null);
            }}
          >
            {Object.entries(labels).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <Button
          variant="outline"
          disabled={busy || loading}
          onClick={() => setReload((value) => value + 1)}
        >
          Tải lại danh sách
        </Button>
      </div>
      {error ? <Alert tone="error">{error}</Alert> : null}
      {notice ? <Alert tone="success">{notice}</Alert> : null}
      {loading ? (
        <p>Đang tải yêu cầu...</p>
      ) : result ? (
        <>
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Mã đơn</th>
                  <th>User ID</th>
                  <th>Số tiền minh họa</th>
                  <th>Credit</th>
                  <th>Thời điểm gửi</th>
                  <th>Trạng thái</th>
                  <th>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {result.items.length ? (
                  result.items.map((order) => (
                    <tr key={order.orderCode}>
                      <td className="break-all">{order.orderCode}</td>
                      <td>{order.userId}</td>
                      <td>{formatVnd(order.amountVnd)}</td>
                      <td>{order.credits}</td>
                      <td>
                        {order.submittedAt
                          ? new Date(order.submittedAt).toLocaleString('vi-VN')
                          : '—'}
                      </td>
                      <td>{labels[order.status]}</td>
                      <td>
                        {order.status === 'PENDING' ? (
                          <div className="flex gap-2">
                            <Button
                              disabled={busy}
                              onClick={() => {
                                setSelected({ order, approve: true });
                                setNote('');
                              }}
                            >
                              Duyệt và cộng Credit
                            </Button>
                            <Button
                              variant="danger"
                              disabled={busy}
                              onClick={() => {
                                setSelected({ order, approve: false });
                                setNote('');
                              }}
                            >
                              Từ chối
                            </Button>
                          </div>
                        ) : (
                          order.reviewNote || '—'
                        )}
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={7}>Chưa có yêu cầu ở trạng thái này.</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
          <div className="flex items-center justify-center gap-3">
            <Button
              variant="outline"
              disabled={busy || page === 0}
              onClick={() => setPage(page - 1)}
            >
              Trang trước
            </Button>
            <span>
              Trang {page + 1}/{Math.max(1, Math.ceil(result.totalElements / result.size))}
            </span>
            <Button
              variant="outline"
              disabled={busy || (page + 1) * result.size >= result.totalElements}
              onClick={() => setPage(page + 1)}
            >
              Trang sau
            </Button>
          </div>
        </>
      ) : null}
      {selected ? (
        <div
          className="space-y-3 rounded-lg bg-surface-low p-4"
          role="dialog"
          aria-label="Xác nhận xử lý đơn nạp"
        >
          <p>
            {selected.approve ? 'Xác nhận duyệt và cộng' : 'Từ chối'}{' '}
            <strong>{selected.order.credits} Credit</strong> cho người dùng{' '}
            <strong>#{selected.order.userId}</strong>, đơn {selected.order.orderCode}?
          </p>
          <TextArea
            label={selected.approve ? 'Ghi chú (tùy chọn)' : 'Lý do từ chối (bắt buộc)'}
            maxLength={500}
            disabled={busy}
            value={note}
            onChange={(event) => setNote(event.target.value)}
          />
          <div className="flex gap-3">
            <Button
              disabled={busy || (!selected.approve && !note.trim())}
              onClick={() => void process()}
            >
              {busy
                ? 'Đang xử lý...'
                : selected.approve
                  ? 'Xác nhận duyệt và cộng Credit'
                  : 'Xác nhận từ chối'}
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
