import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Pagination } from '../../components/Pagination';
import { Alert, Button, Field, PageHeader, SearchField, Select, SkeletonRows } from '../../components/ui';
import {
  adminFinanceApi,
  type AdminLedgerFilters,
  type AdminLedgerPage,
  type LedgerTxType,
} from '../../services/adminFinanceApi';
import type { AdminUserSummary } from '../../services/adminUserApi';
import { useDebouncedValue } from '../../utils/useDebouncedValue';
import { adminErrorMessage } from './adminErrors';
import { AdminUserPicker } from './AdminUserPicker';

const PAGE_SIZE = 20;

const typeLabels: Record<LedgerTxType, string> = {
  TOP_UP: 'Nạp Credit',
  POST_FEE: 'Phí đăng bài',
  EDIT_FEE: 'Phí sửa bài',
  RENEW_FEE: 'Phí gia hạn',
  BOOST_FEE: 'Phí đẩy bài',
  AI_SEARCH_FEE: 'Phí trợ lý AI',
  ADMIN_ADJUST: 'Admin điều chỉnh',
};

function formatDateTime(value: string) {
  return new Date(value).toLocaleString('vi-VN');
}

function signed(amount: number) {
  return amount > 0 ? `+${amount.toLocaleString('vi-VN')}` : amount.toLocaleString('vi-VN');
}

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

export function AdminLedger() {
  const [result, setResult] = useState<AdminLedgerPage | null>(null);
  const [type, setType] = useState<LedgerTxType | ''>('');
  const [user, setUser] = useState<AdminUserSummary | null>(null);
  const [keywordInput, setKeywordInput] = useState('');
  const keyword = useDebouncedValue(keywordInput.trim());
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [page, setPage] = useState(0);
  const [reloadKey, setReloadKey] = useState(0);
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState('');

  const dateRangeError =
    fromDate && toDate && fromDate > toDate ? '"Từ ngày" không được sau "Đến ngày".' : '';

  const buildFilters = useCallback((): AdminLedgerFilters => {
    const filters: AdminLedgerFilters = {};
    if (type) filters.type = type;
    if (user) filters.userId = user.id;
    if (keyword) filters.q = keyword;
    if (fromDate) filters.from = fromDate;
    if (toDate) filters.to = toDate;
    return filters;
  }, [type, user, keyword, fromDate, toDate]);

  // Đổi bộ lọc nào cũng quay về trang đầu.
  useEffect(() => {
    setPage(0);
  }, [type, user, keyword, fromDate, toDate]);

  useEffect(() => {
    if (dateRangeError) return;
    let active = true;
    setLoading(true);
    setError('');
    adminFinanceApi
      .ledger({ ...buildFilters(), page, size: PAGE_SIZE })
      .then((response) => {
        if (active) setResult(response);
      })
      .catch((caught) => {
        if (!active) return;
        setResult(null);
        setError(adminErrorMessage(caught, 'Không tải được lịch sử giao dịch Credit. Vui lòng thử lại.'));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [buildFilters, dateRangeError, page, reloadKey]);

  const exportCsv = async () => {
    setExporting(true);
    setError('');
    try {
      const download = await adminFinanceApi.ledgerCsv(buildFilters());
      downloadBlob(download.blob, download.fileName);
    } catch (caught) {
      setError(adminErrorMessage(caught, 'Không xuất được file. Vui lòng thử lại.'));
    } finally {
      setExporting(false);
    }
  };

  return (
    <section className="space-y-5">
      <PageHeader
        title="Lịch sử giao dịch Credit"
        description="Mọi biến động Credit của toàn hệ thống, dùng để đối soát."
        action={
          <div className="flex gap-2">
            <Button variant="outline" disabled={exporting} onClick={() => void exportCsv()}>
              {exporting ? 'Đang xuất...' : 'Xuất CSV'}
            </Button>
            <Button variant="outline" disabled={loading} onClick={() => setReloadKey((current) => current + 1)}>
              Tải lại
            </Button>
          </div>
        }
      />

      <div className="grid gap-4 rounded-lg bg-white p-4 ring-1 ring-border md:grid-cols-2 lg:grid-cols-4">
        <div className="md:col-span-2 lg:col-span-4">
          <SearchField
            aria-label="Tìm giao dịch Credit"
            placeholder="Tìm theo tên, email, ghi chú hoặc mã đơn nạp..."
            value={keywordInput}
            onChange={(event) => setKeywordInput(event.target.value)}
          />
        </div>
        <Select
          label="Loại giao dịch"
          value={type}
          onChange={(event) => setType(event.target.value as LedgerTxType | '')}
        >
          <option value="">Tất cả</option>
          {Object.entries(typeLabels).map(([value, label]) => (
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
          onChange={(event) => setFromDate(event.target.value)}
        />
        <Field
          label="Đến ngày"
          type="date"
          value={toDate}
          onChange={(event) => setToDate(event.target.value)}
        />
        <div className="md:col-span-2 lg:col-span-4">
          <p className="label">Người dùng</p>
          <AdminUserPicker selected={user} disabled={loading} onSelect={setUser} />
        </div>
      </div>

      {result ? (
        <div className="grid gap-3 sm:grid-cols-3">
          <div className="rounded-lg bg-white p-3 ring-1 ring-border">
            <p className="text-xs text-text-muted">Tổng vào trong khoảng lọc</p>
            <p className="text-xl font-bold tabular-nums text-success">{signed(result.totalIn)} Credit</p>
          </div>
          <div className="rounded-lg bg-white p-3 ring-1 ring-border">
            <p className="text-xs text-text-muted">Tổng ra trong khoảng lọc</p>
            <p className="text-xl font-bold tabular-nums text-error">{signed(result.totalOut)} Credit</p>
          </div>
          <div className="rounded-lg bg-white p-3 ring-1 ring-border">
            <p className="text-xs text-text-muted">Số dòng</p>
            <p className="text-xl font-bold tabular-nums text-text-primary">{result.totalElements}</p>
          </div>
        </div>
      ) : null}

      {error ? (
        <div className="space-y-3">
          <Alert tone="error">{error}</Alert>
          <Button variant="outline" onClick={() => setReloadKey((current) => current + 1)}>
            Thử lại
          </Button>
        </div>
      ) : null}
      {loading && !result ? (
        <SkeletonRows label="Đang tải lịch sử giao dịch Credit" />
      ) : result ? (
        <div>
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Thời gian</th>
                  <th>Người dùng</th>
                  <th>Loại</th>
                  <th className="text-right">Số Credit</th>
                  <th className="text-right">Số dư sau</th>
                  <th>Liên quan</th>
                  <th>Ghi chú</th>
                </tr>
              </thead>
              <tbody>
                {result.items.map((row) => (
                  <tr key={row.id}>
                    <td className="text-xs">{formatDateTime(row.createdAt)}</td>
                    <td>
                      <p className="font-semibold text-text-primary">{row.userName || 'Người dùng đã xóa'}</p>
                      {row.userEmail ? <p className="text-xs text-text-muted">{row.userEmail}</p> : null}
                    </td>
                    <td>{typeLabels[row.type]}</td>
                    <td
                      className={`text-right font-semibold tabular-nums ${row.amount >= 0 ? 'text-success' : 'text-error'}`}
                    >
                      {signed(row.amount)}
                    </td>
                    <td className="text-right tabular-nums">{row.balanceAfter.toLocaleString('vi-VN')}</td>
                    <td className="text-xs">
                      {row.itemId ? (
                        <Link className="font-semibold text-primary hover:underline" to={`/admin/items/${row.itemId}`}>
                          {row.itemTitle || 'Bài đã xóa'}
                        </Link>
                      ) : null}
                      {row.orderCode ? (
                        <Link
                          className="font-mono font-semibold text-primary hover:underline"
                          to={`/admin/finance/topup-orders?q=${encodeURIComponent(row.orderCode)}`}
                        >
                          {shortCode(row.orderCode)}
                        </Link>
                      ) : null}
                      {!row.itemId && !row.orderCode ? '—' : null}
                    </td>
                    <td className="max-w-56 text-xs text-text-muted">
                      {row.note || '—'}
                      {row.createdByName ? <p>Thực hiện bởi {row.createdByName}</p> : null}
                    </td>
                  </tr>
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
    </section>
  );
}
