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
} from '../../components/ui';
import {
  adminFinanceApi,
  type AdminLedgerFilters,
  type AdminLedgerPage,
  type AdminLedgerRow,
  type LedgerTxType,
} from '../../services/adminFinanceApi';
import type { AdminUserSummary } from '../../services/adminUserApi';
import { useDebouncedValue } from '../../utils/useDebouncedValue';
import { adminErrorMessage } from './adminErrors';
import { ledgerTypeLabels } from './adminItemDisplay';
import { AdminUserPicker } from './AdminUserPicker';
import type { AdminLedgerRouteState, AdminLedgerViewState } from './adminLedgerNavigation';
import { DashboardGrid, DashboardStatCard } from './AdminDashboardCards';
import './admin-ledger.css';

const PAGE_SIZE = 20;

function formatDateTime(value: string) {
  return new Date(value).toLocaleString('vi-VN');
}

function signed(amount: number) {
  return amount > 0 ? `+${amount.toLocaleString('vi-VN')}` : amount.toLocaleString('vi-VN');
}

function ledgerTypeClass(type: LedgerTxType) {
  if (type === 'TOP_UP') return 'admin-ledger-type--in';
  if (type === 'ADMIN_ADJUST') return 'admin-ledger-type--adjust';
  if (type === 'AI_SEARCH_FEE') return 'admin-ledger-type--ai';
  return 'admin-ledger-type--fee';
}

interface TransactionContent {
  prefix: string;
  detail?: string;
  separator?: ': ' | ' – ';
  to?: string;
  code?: boolean;
}

function transactionContent(row: AdminLedgerRow): TransactionContent {
  const itemContent = (prefix: string): TransactionContent => ({
    prefix,
    ...(row.itemTitle ? { detail: row.itemTitle, separator: ': ' as const } : {}),
    ...(row.itemId && row.itemTitle ? { to: `/admin/items/${row.itemId}` } : {}),
  });

  switch (row.type) {
    case 'TOP_UP':
      return {
        prefix: 'Nạp Credit thành công',
        ...(row.orderCode
          ? {
              detail: `Mã đơn: ${row.orderCode}`,
              separator: ' – ' as const,
              to: `/admin/finance/topup-orders?q=${encodeURIComponent(row.orderCode)}`,
              code: true,
            }
          : {}),
      };
    case 'POST_FEE':
      return itemContent('Trừ Credit đăng bài');
    case 'EDIT_FEE':
      return itemContent('Trừ Credit sửa bài');
    case 'RENEW_FEE':
      return itemContent('Trừ Credit gia hạn bài');
    case 'BOOST_FEE':
      return itemContent('Trừ Credit đẩy bài');
    case 'AI_SEARCH_FEE': {
      const note = row.note?.trim();
      return note?.startsWith('Hoàn phí AI #')
        ? { prefix: 'Hoàn Credit', detail: note, separator: ' – ' }
        : { prefix: 'Trừ Credit sử dụng Trợ lý AI' };
    }
    case 'ADMIN_ADJUST': {
      const note = row.note?.trim();
      return {
        prefix: row.amount > 0 ? 'Admin cộng Credit' : 'Admin trừ Credit',
        ...(note ? { detail: note, separator: ' – ' as const } : {}),
      };
    }
  }
}

function TransactionContentCell({
  row,
  returnState,
}: {
  row: AdminLedgerRow;
  returnState: AdminLedgerViewState;
}) {
  const content = transactionContent(row);
  const fullText = `${content.prefix}${content.detail ? `${content.separator}${content.detail}` : ''}`;
  const detail = content.detail ? (
    content.to ? (
      <Link
        className={`${content.code ? 'font-mono' : ''} font-semibold text-primary hover:underline`}
        to={content.to}
        state={{
          ledgerReturn: returnState,
          ...(row.orderCode ? { openTopUpOrder: row.orderCode } : {}),
        }}
      >
        {content.detail}
      </Link>
    ) : (
      <span>{content.detail}</span>
    )
  ) : null;

  return (
    <td className="admin-ledger-content" title={fullText}>
      <div className="admin-ledger-content-clamp">
        <span>{content.prefix}</span>
        {detail ? (
          <>
            {content.separator}
            {detail}
          </>
        ) : null}
      </div>
    </td>
  );
}

function DetailItem({
  label,
  children,
  wide = false,
}: {
  label: string;
  children: ReactNode;
  wide?: boolean;
}) {
  return (
    <div
      className={
        wide
          ? 'admin-ledger-detail-item admin-ledger-detail-item--wide'
          : 'admin-ledger-detail-item'
      }
    >
      <dt>{label}</dt>
      <dd>{children}</dd>
    </div>
  );
}

function LedgerDetailDialog({
  row,
  returnState,
  onClose,
}: {
  row: AdminLedgerRow;
  returnState: AdminLedgerViewState;
  onClose: () => void;
}) {
  const content = transactionContent(row);
  const contentText = `${content.prefix}${content.detail ? `${content.separator}${content.detail}` : ''}`;
  const hasUser = Boolean(row.userName || row.userEmail);
  const hasItem = Boolean(row.itemTitle);
  const hasRelated = Boolean(row.itemId || row.itemTitle || row.orderCode);

  return (
    <Dialog title="Chi tiết giao dịch Credit" wide onClose={onClose}>
      <dl className="admin-ledger-detail-grid">
        <DetailItem label="Mã giao dịch">
          <span className="font-mono font-semibold text-text-primary">#{row.id}</span>
        </DetailItem>
        <DetailItem label="Thời gian giao dịch">{formatDateTime(row.createdAt)}</DetailItem>
        {hasUser ? (
          <DetailItem label="Thông tin người dùng">
            {row.userName ? (
              <p className="font-semibold text-text-primary">{row.userName}</p>
            ) : null}
            {row.userEmail ? (
              <p className="break-all text-xs text-text-muted">{row.userEmail}</p>
            ) : null}
          </DetailItem>
        ) : null}
        <DetailItem label="Loại giao dịch">
          <span className={`admin-ledger-type ${ledgerTypeClass(row.type)}`}>
            {ledgerTypeLabels[row.type]}
          </span>
        </DetailItem>
        <DetailItem label="Số Credit cộng hoặc trừ">
          <span
            className={`font-bold tabular-nums ${row.amount >= 0 ? 'text-success' : 'text-error'}`}
          >
            {signed(row.amount)} Credit
          </span>
        </DetailItem>
        <DetailItem label="Số dư sau giao dịch">
          <span className="font-semibold tabular-nums text-text-primary">
            {row.balanceAfter.toLocaleString('vi-VN')} Credit
          </span>
        </DetailItem>
        <DetailItem label="Nội dung giao dịch" wide>
          {contentText}
        </DetailItem>
        {hasRelated ? (
          <DetailItem label="Bài đăng hoặc đơn nạp liên quan" wide>
            <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
              {hasItem ? (
                row.itemId ? (
                  <Link
                    className="font-semibold text-primary hover:underline"
                    to={`/admin/items/${row.itemId}`}
                    state={{ ledgerReturn: returnState }}
                  >
                    {row.itemTitle}
                  </Link>
                ) : (
                  <span>{row.itemTitle}</span>
                )
              ) : null}
              {row.orderCode ? (
                <Link
                  className="break-all font-mono font-semibold text-primary hover:underline"
                  to={`/admin/finance/topup-orders?q=${encodeURIComponent(row.orderCode)}`}
                  state={{ ledgerReturn: returnState, openTopUpOrder: row.orderCode }}
                >
                  Mã đơn: {row.orderCode}
                </Link>
              ) : null}
              {row.itemId ? (
                <Link
                  className="admin-ledger-related-action"
                  to={`/admin/items/${row.itemId}`}
                  state={{ ledgerReturn: returnState }}
                >
                  Xem chi tiết bài đăng
                </Link>
              ) : null}
              {row.orderCode ? (
                <Link
                  className="admin-ledger-related-action"
                  to={`/admin/finance/topup-orders?q=${encodeURIComponent(row.orderCode)}`}
                  state={{ ledgerReturn: returnState, openTopUpOrder: row.orderCode }}
                >
                  Xem chi tiết đơn nạp
                </Link>
              ) : null}
            </div>
          </DetailItem>
        ) : null}
        {row.note ? (
          <DetailItem label="Ghi chú và lý do giao dịch" wide>
            <span className="whitespace-pre-wrap">{row.note}</span>
          </DetailItem>
        ) : null}
        {row.createdByName ? (
          <DetailItem label="Người thực hiện" wide>
            {row.createdByName}
          </DetailItem>
        ) : null}
      </dl>
    </Dialog>
  );
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
  const location = useLocation();
  const restoredState = (location.state as AdminLedgerRouteState | null)?.restoreLedger;
  const [result, setResult] = useState<AdminLedgerPage | null>(null);
  const [type, setType] = useState<LedgerTxType | ''>(restoredState?.type ?? '');
  const [user, setUser] = useState<AdminUserSummary | null>(restoredState?.user ?? null);
  const [keywordInput, setKeywordInput] = useState(restoredState?.keywordInput ?? '');
  const keyword = useDebouncedValue(keywordInput.trim());
  const [fromDate, setFromDate] = useState(restoredState?.fromDate ?? '');
  const [toDate, setToDate] = useState(restoredState?.toDate ?? '');
  const [page, setPage] = useState(restoredState?.page ?? 0);
  const [reloadKey, setReloadKey] = useState(0);
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState('');
  const [selectedRow, setSelectedRow] = useState<AdminLedgerRow | null>(null);
  const firstFilterEffect = useRef(true);

  const ledgerViewState: AdminLedgerViewState = {
    type,
    user,
    keywordInput,
    fromDate,
    toDate,
    page,
  };

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
    if (firstFilterEffect.current) {
      firstFilterEffect.current = false;
      return;
    }
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
        setError(
          adminErrorMessage(caught, 'Không tải được lịch sử giao dịch Credit. Vui lòng thử lại.'),
        );
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
    <section className="admin-ledger-page">
      <div className="admin-ledger-header">
        <PageHeader
          title="Lịch sử giao dịch Credit"
          description="Mọi biến động Credit của toàn hệ thống, dùng để đối soát."
          action={
            <div className="flex gap-2">
              <Button variant="outline" disabled={exporting} onClick={() => void exportCsv()}>
                {exporting ? 'Đang xuất...' : 'Xuất CSV'}
              </Button>
              <Button
                variant="outline"
                disabled={loading}
                onClick={() => setReloadKey((current) => current + 1)}
              >
                Tải lại
              </Button>
            </div>
          }
        />
      </div>

      <div className="admin-ledger-filters">
        <div className="admin-ledger-filter-search">
          <span className="label">Tìm kiếm giao dịch</span>
          <SearchField
            aria-label="Tìm giao dịch Credit"
            placeholder="Tìm theo tên, email, ghi chú hoặc mã đơn nạp..."
            value={keywordInput}
            onChange={(event) => setKeywordInput(event.target.value)}
          />
        </div>
        <Select
          className="admin-ledger-filter-type"
          label="Loại giao dịch"
          value={type}
          onChange={(event) => setType(event.target.value as LedgerTxType | '')}
        >
          <option value="">Tất cả</option>
          {Object.entries(ledgerTypeLabels).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </Select>
        <div className="admin-ledger-filter-from">
          <Field
            label="Từ ngày"
            type="date"
            value={fromDate}
            error={dateRangeError || undefined}
            onChange={(event) => setFromDate(event.target.value)}
          />
        </div>
        <div className="admin-ledger-filter-to">
          <Field
            label="Đến ngày"
            type="date"
            value={toDate}
            onChange={(event) => setToDate(event.target.value)}
          />
        </div>
        <div className="admin-ledger-filter-user">
          <p className="label">Người dùng</p>
          <AdminUserPicker selected={user} disabled={loading} onSelect={setUser} />
        </div>
      </div>

      {result ? (
        <div className="admin-dash-vars" aria-label="Thống kê sổ Credit">
          <DashboardGrid columns={3}>
            <DashboardStatCard
              variant="finance"
              icon="topup"
              accent="green"
              label="Tổng Credit vào"
              value={`${signed(result.totalIn)} Credit`}
              note="Trong khoảng lọc hiện tại"
            />
            <DashboardStatCard
              variant="finance"
              icon="ledger"
              accent="red"
              label="Tổng Credit ra"
              value={`${signed(result.totalOut)} Credit`}
              note="Trong khoảng lọc hiện tại"
            />
            <DashboardStatCard
              variant="finance"
              icon="transactions"
              accent="green"
              label="Tổng giao dịch"
              value={result.totalElements.toLocaleString('vi-VN')}
              note="Trong khoảng lọc hiện tại"
            />
          </DashboardGrid>
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
        <div className="admin-ledger-table-section">
          <div className="table-wrap admin-ledger-table-wrap">
            <table className="data-table admin-ledger-table">
              <thead>
                <tr>
                  <th>Thời gian giao dịch</th>
                  <th>Người dùng</th>
                  <th>Loại giao dịch</th>
                  <th className="text-right">Biến động Credit</th>
                  <th className="text-right">Số dư sau giao dịch</th>
                  <th>Nội dung giao dịch</th>
                  <th>Ghi chú</th>
                  <th className="text-center whitespace-nowrap">Xem chi tiết</th>
                </tr>
              </thead>
              <tbody>
                {result.items.map((row) => (
                  <tr key={row.id} className="admin-ledger-row">
                    <td className="admin-ledger-time">{formatDateTime(row.createdAt)}</td>
                    <td>
                      <p className="truncate font-semibold text-text-primary">
                        {row.userName || 'Người dùng đã xóa'}
                      </p>
                      {row.userEmail ? (
                        <p className="truncate text-xs text-text-muted">{row.userEmail}</p>
                      ) : null}
                    </td>
                    <td>
                      <span className={`admin-ledger-type ${ledgerTypeClass(row.type)}`}>
                        {ledgerTypeLabels[row.type]}
                      </span>
                    </td>
                    <td
                      className={`text-right font-semibold tabular-nums ${row.amount >= 0 ? 'text-success' : 'text-error'}`}
                    >
                      {signed(row.amount)}
                    </td>
                    <td className="admin-ledger-balance">
                      {row.balanceAfter.toLocaleString('vi-VN')}
                    </td>
                    <TransactionContentCell row={row} returnState={ledgerViewState} />
                    <td className="admin-ledger-note">
                      <div className="admin-ledger-note-content">
                        {row.note || '—'}
                        {row.createdByName ? <p>Thực hiện bởi {row.createdByName}</p> : null}
                      </div>
                    </td>
                    <td className="text-center">
                      <button
                        type="button"
                        className="admin-ledger-view-action"
                        aria-label="Xem chi tiết"
                        title="Xem chi tiết"
                        onClick={() => setSelectedRow(row)}
                      >
                        Xem chi tiết
                      </button>
                    </td>
                  </tr>
                ))}
                {result.items.length === 0 ? (
                  <tr>
                    <td colSpan={8} className="text-center text-text-muted">
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

      {selectedRow ? (
        <LedgerDetailDialog
          row={selectedRow}
          returnState={ledgerViewState}
          onClose={() => setSelectedRow(null)}
        />
      ) : null}
    </section>
  );
}
