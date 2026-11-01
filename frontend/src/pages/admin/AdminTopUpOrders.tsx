import { useEffect, useState } from 'react';
import { Pagination } from '../../components/Pagination';
import { Alert, Button, PageHeader, SkeletonRows } from '../../components/ui';
import { adminFinanceApi } from '../../services/adminFinanceApi';
import type { ApiError } from '../../services/http';
import type { PageResponse } from '../../services/itemApi';
import type { PaymentOrderStatus, TopUpOrderDetailResponse } from '../../services/paymentApi';
import { formatVnd } from '../../utils/formatting';

const PAGE_SIZE = 10;

const statusLabels: Record<PaymentOrderStatus, string> = {
  PENDING: 'Chờ thanh toán',
  SUCCESS: 'Thành công',
  FAILED: 'Thất bại',
  EXPIRED: 'Hết hạn',
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

function getErrorMessage(error: unknown) {
  const apiError = error as Partial<ApiError>;
  if (apiError.status === 403) return 'Bạn không có quyền quản lý đơn nạp Credit.';
  return apiError.message || 'Không tải được danh sách đơn nạp. Vui lòng thử lại.';
}

export function AdminTopUpOrders() {
  const [result, setResult] = useState<PageResponse<TopUpOrderDetailResponse> | null>(null);
  // '' là tất cả trạng thái: mở trang là thấy toàn bộ đơn, đơn mới nhất ở trên.
  const [status, setStatus] = useState<PaymentOrderStatus | ''>('');
  const [page, setPage] = useState(0);
  const [reloadKey, setReloadKey] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');

    adminFinanceApi
      .topUpOrders({
        status: status || undefined,
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
  }, [page, reloadKey, status]);

  return (
    <section className="space-y-5">
      <PageHeader
        title="Đơn nạp Credit"
        description="Theo dõi trạng thái các đơn nạp Credit qua cổng thanh toán."
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
      </div>

      {error ? (
        <div className="space-y-3">
          <Alert tone="error">{error}</Alert>
          <Button variant="outline" onClick={() => setReloadKey((current) => current + 1)}>
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
                  <th>Người dùng</th>
                  <th>Giá trị</th>
                  <th>Trạng thái</th>
                  <th>Thời gian</th>
                </tr>
              </thead>
              <tbody>
                {result.items.map((order) => (
                  <TopUpRow key={order.orderCode} order={order} />
                ))}
                {result.items.length === 0 ? (
                  <tr>
                    <td colSpan={4} className="text-center text-text-muted">
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
    </section>
  );
}

function TopUpRow({ order }: { order: TopUpOrderDetailResponse }) {
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
          {statusLabels[order.status]}
        </span>
      </td>
      <td className="text-xs">
        <p>Tạo lúc: {formatDateTime(order.createdAt)}</p>
        {order.status === 'PENDING' ? <p>Hết hạn: {formatDateTime(order.expiresAt)}</p> : null}
      </td>
    </tr>
  );
}
