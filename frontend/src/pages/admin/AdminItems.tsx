import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Pagination } from '../../components/Pagination';
import {
  Alert,
  Button,
  Field,
  ItemImage,
  PageHeader,
  SearchField,
  Select,
  SkeletonRows,
} from '../../components/ui';
import { adminItemApi, type AdminItemFilters, type AdminItemRow } from '../../services/adminItemApi';
import { catalogApi, type CategoryResponse } from '../../services/catalogApi';
import type { ItemStatus, OfferType, PageResponse } from '../../services/itemApi';
import { adminErrorMessage } from './adminErrors';
import { formatDateTime, hiddenSourceLabels, itemStatusLabels } from './adminItemDisplay';
import { AdminItemVisibilityDialog } from './AdminItemVisibilityDialog';
import { offerTypeLabels } from './moderationDisplay';
import { useDebouncedValue } from '../../utils/useDebouncedValue';

const PAGE_SIZE = 20;

type VisibilityFilter = '' | 'visible' | 'hidden';

function statusClass(status: ItemStatus) {
  if (status === 'APPROVED' || status === 'TRADED') return 'bg-success-soft text-success';
  if (status === 'REJECTED') return 'bg-error-soft text-error';
  if (status === 'PENDING_REVIEW' || status === 'RESERVED') return 'bg-warning-soft text-warning';
  return 'bg-surface-low text-text-muted';
}

export function AdminItems() {
  const [result, setResult] = useState<PageResponse<AdminItemRow> | null>(null);
  const [categories, setCategories] = useState<CategoryResponse[]>([]);
  const [status, setStatus] = useState<ItemStatus | ''>('');
  const [visibility, setVisibility] = useState<VisibilityFilter>('');
  const [offerType, setOfferType] = useState<OfferType | ''>('');
  const [categoryId, setCategoryId] = useState('');
  const [keywordInput, setKeywordInput] = useState('');
  const keyword = useDebouncedValue(keywordInput.trim());
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [boostedOnly, setBoostedOnly] = useState(false);
  const [page, setPage] = useState(0);
  const [reloadKey, setReloadKey] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [dialog, setDialog] = useState<{ row: AdminItemRow; mode: 'hide' | 'unhide' } | null>(null);

  const dateRangeError =
    fromDate && toDate && fromDate > toDate ? '"Từ ngày" không được sau "Đến ngày".' : '';

  useEffect(() => {
    let active = true;
    catalogApi
      .categories()
      .then((response) => {
        if (active) setCategories(response);
      })
      .catch(() => {
        // Bộ lọc danh mục chỉ là tiện ích, thiếu thì vẫn dùng được các bộ lọc còn lại.
      });
    return () => {
      active = false;
    };
  }, []);

  const buildFilters = useCallback((): AdminItemFilters => {
    const filters: AdminItemFilters = {};
    if (status) filters.status = status;
    if (visibility) filters.active = visibility === 'visible';
    if (offerType) filters.offerType = offerType;
    if (categoryId) filters.categoryId = Number(categoryId);
    if (keyword) filters.q = keyword;
    if (fromDate) filters.from = fromDate;
    if (toDate) filters.to = toDate;
    if (boostedOnly) filters.boosted = true;
    return filters;
  }, [status, visibility, offerType, categoryId, keyword, fromDate, toDate, boostedOnly]);

  // Đổi bộ lọc nào cũng quay về trang đầu.
  useEffect(() => {
    setPage(0);
  }, [status, visibility, offerType, categoryId, keyword, fromDate, toDate, boostedOnly]);

  useEffect(() => {
    if (dateRangeError) return;
    let active = true;
    setLoading(true);
    setError('');
    adminItemApi
      .list({ ...buildFilters(), page, size: PAGE_SIZE })
      .then((response) => {
        if (active) setResult(response);
      })
      .catch((caught) => {
        if (!active) return;
        setResult(null);
        setError(adminErrorMessage(caught, 'Không tải được danh sách bài đăng. Vui lòng thử lại.'));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [buildFilters, dateRangeError, page, reloadKey]);

  return (
    <section className="space-y-5">
      <PageHeader
        title="Quản lý bài đăng"
        description="Xem mọi bài đăng ở mọi trạng thái, ẩn bài vi phạm hoặc hiện lại bài đã ẩn."
        action={
          <Button variant="outline" disabled={loading} onClick={() => setReloadKey((current) => current + 1)}>
            Tải lại
          </Button>
        }
      />

      <div className="grid gap-4 rounded-lg bg-white p-4 ring-1 ring-border md:grid-cols-2 lg:grid-cols-4">
        <div className="md:col-span-2 lg:col-span-4">
          <SearchField
            aria-label="Tìm bài đăng"
            placeholder="Tìm theo tiêu đề, tên hoặc email chủ bài..."
            value={keywordInput}
            onChange={(event) => setKeywordInput(event.target.value)}
          />
        </div>
        <Select
          label="Trạng thái"
          value={status}
          onChange={(event) => setStatus(event.target.value as ItemStatus | '')}
        >
          <option value="">Tất cả</option>
          {Object.entries(itemStatusLabels).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </Select>
        <Select
          label="Hiển thị"
          value={visibility}
          onChange={(event) => setVisibility(event.target.value as VisibilityFilter)}
        >
          <option value="">Tất cả</option>
          <option value="visible">Đang hiện</option>
          <option value="hidden">Đang ẩn</option>
        </Select>
        <Select
          label="Hình thức"
          value={offerType}
          onChange={(event) => setOfferType(event.target.value as OfferType | '')}
        >
          <option value="">Tất cả</option>
          {Object.entries(offerTypeLabels).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </Select>
        <Select label="Danh mục" value={categoryId} onChange={(event) => setCategoryId(event.target.value)}>
          <option value="">Tất cả</option>
          {categories.map((category) => (
            <option key={category.id} value={category.id}>
              {category.name}
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
        <Field label="Đến ngày" type="date" value={toDate} onChange={(event) => setToDate(event.target.value)} />
        <label className="flex items-center gap-2 self-end pb-2 text-sm font-medium text-text-primary">
          <input
            type="checkbox"
            className="size-4"
            checked={boostedOnly}
            onChange={(event) => setBoostedOnly(event.target.checked)}
          />
          Chỉ bài đang đẩy
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
        <SkeletonRows label="Đang tải danh sách bài đăng" />
      ) : result ? (
        <div>
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Bài đăng</th>
                  <th>Chủ bài</th>
                  <th>Hình thức</th>
                  <th>Trạng thái</th>
                  <th>Phí đăng</th>
                  <th>Yêu cầu chờ</th>
                  <th>Thời gian</th>
                  <th>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {result.items.map((row) => (
                  <ItemRow
                    key={row.id}
                    row={row}
                    onHide={() => setDialog({ row, mode: 'hide' })}
                    onUnhide={() => setDialog({ row, mode: 'unhide' })}
                  />
                ))}
                {result.items.length === 0 ? (
                  <tr>
                    <td colSpan={8} className="text-center text-text-muted">
                      Không có bài đăng phù hợp bộ lọc hiện tại.
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

      {dialog ? (
        <AdminItemVisibilityDialog
          itemId={dialog.row.id}
          itemTitle={dialog.row.title}
          mode={dialog.mode}
          onClose={() => setDialog(null)}
          onDone={() => {
            setDialog(null);
            setReloadKey((current) => current + 1);
          }}
        />
      ) : null}
    </section>
  );
}

function ItemRow({
  row,
  onHide,
  onUnhide,
}: {
  row: AdminItemRow;
  onHide: () => void;
  onUnhide: () => void;
}) {
  // Bài đã có người nhận thì không ẩn được, Admin phải huỷ giao dịch trước. Backend vẫn kiểm lại mọi trường hợp.
  const hideBlocked = row.status === 'RESERVED' || row.status === 'TRADED';
  return (
    <tr>
      <td>
        <div className="flex items-center gap-3">
          <ItemImage
            src={row.imageUrl ?? undefined}
            alt={row.title}
            className="h-11 w-14 shrink-0 rounded-md object-cover"
          />
          <div className="min-w-0">
            <Link
              to={`/admin/items/${row.id}`}
              className="block max-w-56 truncate font-semibold text-primary hover:underline"
            >
              {row.title}
            </Link>
            <p className="text-xs text-text-muted">{row.categoryName || '—'}</p>
          </div>
        </div>
      </td>
      <td>
        <p className="font-semibold text-text-primary">{row.ownerName || 'Người dùng đã xóa'}</p>
        {row.ownerEmail ? <p className="text-xs text-text-muted">{row.ownerEmail}</p> : null}
      </td>
      <td>{offerTypeLabels[row.offerType]}</td>
      <td>
        <div className="flex flex-col items-start gap-1">
          <span
            className={`inline-flex whitespace-nowrap rounded-sm px-2 py-1 text-2xs font-semibold ${statusClass(row.status)}`}
          >
            {itemStatusLabels[row.status]}
          </span>
          {row.active ? (
            <span className="text-xs text-text-muted">Đang hiện</span>
          ) : (
            <span className="inline-flex whitespace-nowrap rounded-sm bg-error-soft px-2 py-1 text-2xs font-semibold text-error">
              Đang ẩn
              {row.hiddenSource ? `: ${hiddenSourceLabels[row.hiddenSource]}` : ''}
            </span>
          )}
          {row.boosting ? <span className="text-xs font-semibold text-secondary">Đang đẩy</span> : null}
        </div>
      </td>
      <td className="tabular-nums">{row.postFeePaid > 0 ? `${row.postFeePaid} Credit` : '—'}</td>
      <td className="tabular-nums">{row.pendingRequestCount}</td>
      <td className="text-xs">
        <p>Tạo lúc: {formatDateTime(row.createdAt)}</p>
        <p>Duyệt lúc: {formatDateTime(row.approvedAt)}</p>
        <p>Hết hạn: {formatDateTime(row.expireAt)}</p>
      </td>
      <td>
        <div className="flex flex-wrap gap-2">
          <Link
            to={`/admin/items/${row.id}`}
            className="inline-flex min-h-9 items-center rounded-md border border-border px-3 text-sm font-semibold text-text-primary hover:bg-surface-low"
          >
            Xem
          </Link>
          {row.active ? (
            <Button
              variant="danger"
              disabled={hideBlocked}
              title={hideBlocked ? 'Bài đã có giao dịch, hãy huỷ giao dịch trước khi ẩn.' : undefined}
              onClick={onHide}
            >
              Ẩn
            </Button>
          ) : (
            <Button variant="outline" onClick={onUnhide}>
              Hiện lại
            </Button>
          )}
        </div>
      </td>
    </tr>
  );
}
