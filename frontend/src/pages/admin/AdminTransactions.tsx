import { useCallback, useEffect, useRef, useState } from 'react';
import { Dialog } from '../../components/Dialog';
import { Pagination } from '../../components/Pagination';
import { Link } from 'react-router-dom';
import { Alert, Button, Field, PageHeader, Select, SkeletonRows, TextArea } from '../../components/ui';
import {
  adminRequestApi,
  type AdminRequestListParams,
  type AdminRequestPage,
  type AdminRequestRow,
  type DeliveryMethod,
} from '../../services/adminRequestApi';
import type { ApiError } from '../../services/http';
import type { ItemRequestStatus } from '../../types/domain';
import { requestStatusLabel } from '../../utils/request';
import { deliveryMethodLabels } from './adminItemDisplay';

const PAGE_SIZE = 20;
const REASON_MIN = 5;
const REASON_MAX = 500;

// Admin chỉ huỷ được giao dịch đã chọn người và chưa kết thúc (docs/api/admin.md); backend vẫn kiểm lại.
const cancellableStatuses: ItemRequestStatus[] = [
  'RESERVED',
  'AWAITING_LOGISTICS',
  'LOGISTICS_CONFIRMED',
  'AWAITING_HANDOVER',
  'DISPUTED',
];

function statusClass(status: ItemRequestStatus) {
  if (status === 'COMPLETED') return 'bg-success-soft text-success';
  if (status === 'DISPUTED' || status === 'CANCELLED' || status === 'REJECTED') return 'bg-error-soft text-error';
  if (status === 'PENDING') return 'bg-surface-low text-text-muted';
  return 'bg-warning-soft text-warning';
}

function formatDateTime(value: string | null) {
  return value ? new Date(value).toLocaleString('vi-VN') : '—';
}

function errorMessage(error: unknown) {
  const apiError = error as Partial<ApiError>;
  if (apiError.status === 403) return 'Bạn không có quyền quản lý giao dịch.';
  if (apiError.code === 'REQUEST_INVALID_STATE_TRANSITION') {
    return 'Giao dịch không còn ở trạng thái huỷ được. Vui lòng tải lại danh sách.';
  }
  if (apiError.code === 'REQUEST_NOT_FOUND') return 'Không tìm thấy giao dịch này.';
  return apiError.message || 'Không xử lý được yêu cầu. Vui lòng thử lại.';
}

export function AdminTransactions() {
  const [result, setResult] = useState<AdminRequestPage | null>(null);
  const [status, setStatus] = useState<ItemRequestStatus | ''>('');
  const [stuckOnly, setStuckOnly] = useState(false);
  const [type, setType] = useState<'GIVE' | 'SWAP' | ''>('');
  const [deliveryMethod, setDeliveryMethod] = useState<DeliveryMethod | ''>('');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [queryInput, setQueryInput] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [reloadKey, setReloadKey] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [cancelling, setCancelling] = useState<AdminRequestRow | null>(null);
  // Hai bộ lọc của luật mới: có đề nghị huỷ đang chờ, Giao hàng quá hạn gửi
  const [pendingCancelOnly, setPendingCancelOnly] = useState(false);
  const [sendOverdueOnly, setSendOverdueOnly] = useState(false);

  const dateRangeError =
    fromDate && toDate && fromDate > toDate ? '"Từ ngày" không được sau "Đến ngày".' : '';

  useEffect(() => {
    if (dateRangeError) return;
    let active = true;
    setLoading(true);
    setError('');
    const params: AdminRequestListParams = {
      status: status || undefined,
      stuck: stuckOnly ? true : undefined,
      pendingCancel: pendingCancelOnly ? true : undefined,
      sendOverdue: sendOverdueOnly ? true : undefined,
      q: query || undefined,
      type: type || undefined,
      deliveryMethod: deliveryMethod || undefined,
      from: fromDate || undefined,
      to: toDate || undefined,
      page,
      size: PAGE_SIZE,
    };
    adminRequestApi
      .list(params)
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
        setError(errorMessage(caught));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [
    page,
    query,
    reloadKey,
    status,
    stuckOnly,
    pendingCancelOnly,
    sendOverdueOnly,
    type,
    deliveryMethod,
    fromDate,
    toDate,
    dateRangeError,
  ]);

  const closeDialog = useCallback(() => setCancelling(null), []);

  const onCancelled = () => {
    setCancelling(null);
    setNotice('Đã huỷ giao dịch. Cả hai bên đã được thông báo.');
    setReloadKey((current) => current + 1);
  };

  return (
    <section className="space-y-5">
      <PageHeader
        title="Giao dịch"
        description="Theo dõi mọi giao dịch và huỷ giao dịch bị kẹt: quá giờ hẹn mà chưa ai xác nhận bàn giao, hoặc một bên đang bị khoá tài khoản."
        action={
          <Button variant="outline" disabled={loading} onClick={() => setReloadKey((current) => current + 1)}>
            Tải lại
          </Button>
        }
      />

      <form
        className="flex flex-wrap items-end gap-4 rounded-lg bg-white p-4 ring-1 ring-border"
        onSubmit={(event) => {
          event.preventDefault();
          setQuery(queryInput.trim());
          setPage(0);
        }}
      >
        <div className="min-w-48">
          <Select
            label="Trạng thái"
            value={status}
            onChange={(event) => {
              setStatus(event.target.value as ItemRequestStatus | '');
              setPage(0);
            }}
          >
            <option value="">Tất cả</option>
            {Object.entries(requestStatusLabel).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Select>
        </div>
        <div className="min-w-40">
          <Select
            label="Loại giao dịch"
            value={type}
            onChange={(event) => {
              setType(event.target.value as 'GIVE' | 'SWAP' | '');
              setPage(0);
            }}
          >
            <option value="">Tất cả</option>
            <option value="GIVE">Cho tặng</option>
            <option value="SWAP">Trao đổi</option>
          </Select>
        </div>
        <div className="min-w-40">
          <Select
            label="Hình thức giao nhận"
            value={deliveryMethod}
            onChange={(event) => {
              setDeliveryMethod(event.target.value as DeliveryMethod | '');
              setPage(0);
            }}
          >
            <option value="">Tất cả</option>
            <option value="IN_PERSON">Gặp trực tiếp</option>
            <option value="SHIPPING">Gửi hàng</option>
          </Select>
        </div>
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
        <label className="grid gap-1.5 text-sm font-medium">
          Tìm theo tên bài hoặc tên người
          <input
            className="rounded-md border border-border bg-white px-3 py-2"
            value={queryInput}
            maxLength={100}
            onChange={(event) => setQueryInput(event.target.value)}
          />
        </label>
        <Button variant="outline">Tìm</Button>
        <Button
          type="button"
          variant={stuckOnly ? 'primary' : 'outline'}
          aria-pressed={stuckOnly}
          onClick={() => {
            setStuckOnly((current) => !current);
            setPage(0);
          }}
        >
          Quá giờ xác nhận bàn giao
        </Button>
        <Button
          type="button"
          variant={pendingCancelOnly ? 'primary' : 'outline'}
          aria-pressed={pendingCancelOnly}
          onClick={() => {
            setPendingCancelOnly((current) => !current);
            setPage(0);
          }}
        >
          Đang chờ phản hồi đề nghị huỷ
        </Button>
        <Button
          type="button"
          variant={sendOverdueOnly ? 'primary' : 'outline'}
          aria-pressed={sendOverdueOnly}
          onClick={() => {
            setSendOverdueOnly((current) => !current);
            setPage(0);
          }}
        >
          Giao hàng quá hạn gửi
        </Button>
      </form>

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
        <SkeletonRows label="Đang tải giao dịch" />
      ) : result ? (
        <div>
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Bài đăng</th>
                  <th>Chủ bài</th>
                  <th>Người nhận</th>
                  <th>Trạng thái</th>
                  <th>Giao nhận</th>
                  <th>Giờ hẹn</th>
                  <th>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {result.items.map((row) => (
                  <TransactionRow
                    key={row.id}
                    row={row}
                    onCancel={() => {
                      setNotice('');
                      setCancelling(row);
                    }}
                  />
                ))}
                {result.items.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="text-center text-text-muted">
                      Không có giao dịch phù hợp bộ lọc hiện tại.
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

      {cancelling ? <CancelDialog row={cancelling} onClose={closeDialog} onDone={onCancelled} /> : null}
    </section>
  );
}

function TransactionRow({ row, onCancel }: { row: AdminRequestRow; onCancel: () => void }) {
  return (
    <tr>
      <td>
        <Link
          to={`/admin/transactions/${row.id}`}
          className="cell-truncate block font-semibold text-primary hover:underline"
          title={row.itemTitle || undefined}
        >
          {row.itemTitle || 'Bài đã xoá'}
        </Link>
        <p className="text-xs text-text-muted">{row.type === 'SWAP' ? 'Trao đổi' : 'Cho tặng'}</p>
      </td>
      <td>{row.donorName || '—'}</td>
      <td>{row.receiverName || '—'}</td>
      <td>
        <span
          className={`inline-flex whitespace-nowrap rounded-sm px-2 py-1 text-2xs font-semibold ${statusClass(row.status)}`}
        >
          {requestStatusLabel[row.status]}
        </span>
        {row.flagReason ? <p className="mt-1 max-w-56 text-xs font-semibold text-error">Bị kẹt: {row.flagReason}</p> : null}
        {(row.labels ?? []).map((label) => (
          <p key={label} className="mt-1 max-w-56 text-xs font-semibold text-warning">
            {label}
          </p>
        ))}
      </td>
      <td className="text-xs">{row.deliveryMethod ? deliveryMethodLabels[row.deliveryMethod] : '—'}</td>
      <td className="text-xs">{formatDateTime(row.meetingTime)}</td>
      <td>
        {cancellableStatuses.includes(row.status) ? (
          <Button variant="danger" size="sm" onClick={onCancel}>
            Huỷ giao dịch
          </Button>
        ) : (
          <span className="text-text-muted">—</span>
        )}
      </td>
    </tr>
  );
}

export function CancelDialog({
  row,
  onClose,
  onDone,
}: {
  row: Pick<AdminRequestRow, 'id' | 'itemTitle' | 'donorName' | 'receiverName'>;
  onClose: () => void;
  onDone: () => void;
}) {
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const inFlight = useRef(false);
  const trimmed = reason.trim();

  const submit = async () => {
    if (inFlight.current) return;
    if (trimmed.length < REASON_MIN) {
      setError(`Lý do huỷ cần từ ${REASON_MIN} đến ${REASON_MAX} ký tự.`);
      return;
    }
    inFlight.current = true;
    setBusy(true);
    setError('');
    try {
      await adminRequestApi.cancel(row.id, trimmed);
      onDone();
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  };

  return (
    <Dialog title="Huỷ giao dịch" busy={busy} onClose={onClose}>
      <p className="mt-3 text-sm text-text-secondary">
        {row.itemTitle || 'Bài đã xoá'}: {row.donorName || '—'} và {row.receiverName || '—'}. Bài đăng được mở lại, cả
        hai bên nhận thông báo kèm lý do.
      </p>
      <TextArea
        className="mt-4"
        label="Lý do huỷ (bắt buộc)"
        value={reason}
        maxLength={REASON_MAX}
        disabled={busy}
        onChange={(event) => setReason(event.target.value)}
      />
      <p className="mt-1 text-right text-xs text-text-muted">
        {reason.length}/{REASON_MAX}
      </p>
      {error ? (
        <div role="alert" className="mt-3 rounded-md bg-error-soft p-3 text-xs text-error">
          {error}
        </div>
      ) : null}
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="outline" disabled={busy} onClick={onClose}>
          Đóng
        </Button>
        <Button variant="danger" disabled={busy || trimmed.length < REASON_MIN} onClick={() => void submit()}>
          {busy ? 'Đang huỷ...' : 'Huỷ giao dịch'}
        </Button>
      </div>
    </Dialog>
  );
}
