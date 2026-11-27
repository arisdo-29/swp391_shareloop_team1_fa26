import { useCallback, useEffect, useId, useState } from 'react';
import { Link } from 'react-router-dom';
import { Pagination } from '../../components/Pagination';
import {
  Alert,
  Button,
  Field,
  Icon,
  ItemImage,
  PageHeader,
  SearchField,
  Select,
  SkeletonRows,
} from '../../components/ui';
import {
  adminItemApi,
  type AdminItemFilters,
  type AdminItemRow,
} from '../../services/adminItemApi';
import { catalogApi, type CategoryResponse } from '../../services/catalogApi';
import type { ItemStatus, OfferType, PageResponse } from '../../services/itemApi';
import { adminErrorMessage } from './adminErrors';
import { formatDateTime, hiddenSourceLabels, itemStatusLabels } from './adminItemDisplay';
import { AdminItemVisibilityDialog } from './AdminItemVisibilityDialog';
import { offerTypeLabels } from './moderationDisplay';
import { useDebouncedValue } from '../../utils/useDebouncedValue';
import { DashboardGrid, DashboardStatCard, type DashboardAccent } from './AdminDashboardCards';
import './admin-items.css';

const PAGE_SIZE = 20;

type VisibilityFilter = '' | 'visible' | 'hidden';

type ItemStatKey = 'total' | 'pending' | 'visible' | 'reserved' | 'traded' | 'hidden';

type ItemStats = Record<ItemStatKey, number | null | 'error'>;

// Thẻ thống kê đọc tổng số toàn hệ thống từ chính API danh sách (xin một dòng, đọc totalElements),
// không đếm trên trang đang xem. "Đang hiển thị" khớp điều kiện bài hiện công khai: đã duyệt và chưa bị ẩn.
const ITEM_STATS: Array<{ key: ItemStatKey; label: string; filters: AdminItemFilters }> = [
  { key: 'total', label: 'Tổng bài đăng', filters: {} },
  { key: 'pending', label: 'Chờ duyệt', filters: { status: 'PENDING_REVIEW' } },
  { key: 'visible', label: 'Đang hiển thị', filters: { status: 'APPROVED', active: true } },
  { key: 'reserved', label: 'Đã giữ chỗ', filters: { status: 'RESERVED' } },
  { key: 'traded', label: 'Đã hoàn tất', filters: { status: 'TRADED' } },
  { key: 'hidden', label: 'Đã ẩn', filters: { active: false } },
];

const EMPTY_STATS: ItemStats = {
  total: null,
  pending: null,
  visible: null,
  reserved: null,
  traded: null,
  hidden: null,
};

function statusClass(status: ItemStatus) {
  if (status === 'PENDING_REVIEW') return 'bg-warning-soft text-warning';
  if (status === 'APPROVED') return 'bg-success-soft text-success';
  if (status === 'REJECTED') return 'bg-error-soft text-error';
  if (status === 'RESERVED') return 'bg-info-soft text-info';
  if (status === 'TRADED') return 'bg-primary text-white';
  return 'border border-border bg-surface-low text-text-muted';
}

/**
 * Tình trạng hiển thị của bài với người dùng. Bài chỉ hiện công khai khi đã duyệt và chưa bị ẩn,
 * nên bài chưa bị ẩn mà chưa duyệt, bị từ chối hoặc đã hết hạn không ghi "Đang hiện" để tránh hiểu nhầm.
 */
function visibilityInfo(row: AdminItemRow): {
  label: string;
  tone: 'visible' | 'hidden' | 'muted';
} {
  if (!row.active) {
    return {
      label: `Đang ẩn${row.hiddenSource ? `: ${hiddenSourceLabels[row.hiddenSource]}` : ''}`,
      tone: 'hidden',
    };
  }
  if (row.status === 'APPROVED') return { label: 'Đang hiện', tone: 'visible' };
  if (row.status === 'PENDING_REVIEW') return { label: 'Chưa hiện, đang chờ duyệt', tone: 'muted' };
  if (row.status === 'RESERVED') return { label: 'Không hiện khi đang giữ chỗ', tone: 'muted' };
  return { label: 'Không hiện công khai', tone: 'muted' };
}

/** Lý do nút Ẩn bị khoá. Backend vẫn kiểm lại mọi trường hợp. */
function hideBlockedReason(row: AdminItemRow) {
  if (row.status === 'RESERVED')
    return 'Bài đang giữ chỗ cho một giao dịch. Hãy hủy giao dịch ở trang Giao dịch trước khi ẩn bài.';
  if (row.status === 'TRADED') return 'Bài đã giao dịch xong nên không thể ẩn.';
  return null;
}

export function AdminItems() {
  const [result, setResult] = useState<PageResponse<AdminItemRow> | null>(null);
  const [stats, setStats] = useState<ItemStats>(EMPTY_STATS);
  const [categories, setCategories] = useState<CategoryResponse[]>([]);
  const [status, setStatus] = useState<ItemStatus | ''>('');
  const [visibility, setVisibility] = useState<VisibilityFilter>('');
  const [offerType, setOfferType] = useState<OfferType | ''>('');
  const [categoryId, setCategoryId] = useState('');
  const [keywordInput, setKeywordInput] = useState('');
  const debouncedKeyword = useDebouncedValue(keywordInput.trim());
  const keyword = keywordInput.trim() ? debouncedKeyword : '';
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [boostedOnly, setBoostedOnly] = useState(false);
  const [showMoreFilters, setShowMoreFilters] = useState(false);
  const moreFiltersId = useId();
  const [page, setPage] = useState(0);
  const [reloadKey, setReloadKey] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [dialog, setDialog] = useState<{ row: AdminItemRow; mode: 'hide' | 'unhide' } | null>(null);

  const hasFilters = Boolean(
    keywordInput ||
    status ||
    visibility ||
    offerType ||
    categoryId ||
    fromDate ||
    toDate ||
    boostedOnly,
  );
  const dateRangeError =
    fromDate && toDate && fromDate > toDate ? '"Từ ngày" không được sau "Đến ngày".' : '';
  const activeExtraFilters = Number(Boolean(offerType)) + Number(Boolean(fromDate || toDate)) + Number(boostedOnly);
  function clearFilters() {
    setKeywordInput('');
    setStatus('');
    setVisibility('');
    setOfferType('');
    setCategoryId('');
    setFromDate('');
    setToDate('');
    setBoostedOnly(false);
    setPage(0);
  }

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

  // Số liệu toàn hệ thống, không phụ thuộc bộ lọc. Tải lại sau khi ẩn hoặc hiện lại bài.
  useEffect(() => {
    let active = true;
    setStats(EMPTY_STATS);
    for (const stat of ITEM_STATS) {
      adminItemApi
        .list({ ...stat.filters, page: 0, size: 1 })
        .then((response) => {
          if (active) setStats((current) => ({ ...current, [stat.key]: response.totalElements }));
        })
        .catch(() => {
          if (active) setStats((current) => ({ ...current, [stat.key]: 'error' }));
        });
    }
    return () => {
      active = false;
    };
  }, [reloadKey]);

  return (
    <section className="admin-items-page">
      <div className="admin-items-header">
        <PageHeader title="Quản lý bài đăng" />
      </div>

      <div className="admin-dash-vars admin-items-stats" aria-label="Thống kê bài đăng">
        <DashboardGrid columns={6}>
          {ITEM_STATS.map((stat) => (
            <ItemStat
              key={stat.key}
              modifier={stat.key}
              label={stat.label}
              value={stats[stat.key]}
            />
          ))}
        </DashboardGrid>
      </div>

      <div className="admin-items-filters">
        <div className="admin-items-filter-search">
          <span className="label">Tìm kiếm</span>
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
          {/* "Chưa bị ẩn" gồm cả bài chưa duyệt, nên không gọi là "Đang hiện". */}
          <option value="visible">Chưa bị ẩn</option>
          <option value="hidden">Đang ẩn</option>
        </Select>
        <Select
          label="Danh mục"
          value={categoryId}
          onChange={(event) => setCategoryId(event.target.value)}
        >
          <option value="">Tất cả</option>
          {categories.map((category) => (
            <option key={category.id} value={category.id}>
              {category.name}
            </option>
          ))}
        </Select>
        <div className="admin-items-filter-actions">
          <Button
            variant="outline"
            size="sm"
            aria-expanded={showMoreFilters}
            aria-controls={moreFiltersId}
            onClick={() => setShowMoreFilters((open) => !open)}
          >
            Bộ lọc khác{activeExtraFilters ? ` (${activeExtraFilters})` : ''}
          </Button>
          <Button
            variant="outline"
            size="sm"
            className="w-11 !px-0"
            aria-label="Xóa bộ lọc"
            title="Xóa bộ lọc"
            disabled={!hasFilters}
            onClick={clearFilters}
          >
            <Icon name="close" className="size-4" />
          </Button>
        </div>
        <div id={moreFiltersId} hidden={!showMoreFilters} className="admin-items-more-filters">
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
          <label className="admin-items-filter-boosted">
            <input
              type="checkbox"
              checked={boostedOnly}
              onChange={(event) => setBoostedOnly(event.target.checked)}
            />
            Chỉ bài đang đẩy
          </label>
        </div>
        {!showMoreFilters && dateRangeError ? (
          <p role="alert" className="col-span-full text-xs text-error">{dateRangeError}</p>
        ) : null}
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
        <div className="admin-items-table-section">
          <div
            key={result.page}
            role="region"
            aria-label="Danh sách bài đăng"
            tabIndex={0}
            className="table-wrap admin-items-table-wrap focus-visible:outline focus-visible:outline-2 focus-visible:outline-primary"
          >
            <table className="data-table admin-items-table">
              <thead>
                <tr>
                  <th>Bài đăng</th>
                  <th>Chủ bài</th>
                  <th className="text-center">Hình thức</th>
                  <th>Trạng thái</th>
                  <th className="text-right">Phí đăng</th>
                  <th className="text-center">Yêu cầu chờ</th>
                  <th>Thời gian</th>
                  <th className="text-center">Thao tác</th>
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

function ItemStat({
  label,
  value,
  modifier,
}: {
  label: string;
  value: number | null | 'error';
  modifier: ItemStatKey;
}) {
  const appearances: Record<
    ItemStatKey,
    {
      icon: 'manageItems' | 'moderation' | 'eye' | 'expiring' | 'completed' | 'eyeSlash';
      accent: DashboardAccent;
    }
  > = {
    total: { icon: 'manageItems', accent: 'green' },
    pending: { icon: 'moderation', accent: 'gold' },
    visible: { icon: 'eye', accent: 'green' },
    reserved: { icon: 'expiring', accent: 'blue' },
    traded: { icon: 'completed', accent: 'green' },
    hidden: { icon: 'eyeSlash', accent: 'red' },
  };
  return (
    <DashboardStatCard
      variant="system"
      icon={appearances[modifier].icon}
      accent={appearances[modifier].accent}
      label={label}
      value={value === null ? '...' : value === 'error' ? '—' : value.toLocaleString('vi-VN')}
      note={value === 'error' ? 'Chưa tải được số liệu' : ''}
      loading={value === null}
    />
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
  const blockedReason = hideBlockedReason(row);
  const blockedId = useId();
  const visibility = visibilityInfo(row);
  return (
    <tr className="admin-items-row">
      <td>
        <div className="flex min-w-0 items-center gap-2.5">
          <ItemImage
            src={row.imageUrl ?? undefined}
            alt={row.title}
            className="size-10 shrink-0 rounded-sm object-cover"
          />
          <div className="min-w-0">
            <Link
              to={`/admin/items/${row.id}`}
              className="admin-items-title font-semibold text-primary hover:underline"
              title={row.title}
            >
              {row.title}
            </Link>
            <p className="text-xs text-text-muted">{row.categoryName || '—'}</p>
          </div>
        </div>
      </td>
      <td className="min-w-0">
        <p className="admin-items-owner-name font-semibold text-text-primary">
          {row.ownerName || 'Người dùng đã xóa'}
        </p>
        {row.ownerEmail ? (
          <p className="admin-items-owner-email text-xs text-text-muted">{row.ownerEmail}</p>
        ) : null}
      </td>
      <td className="whitespace-nowrap text-center">{offerTypeLabels[row.offerType]}</td>
      <td>
        <div className="admin-items-statuses">
          <span
            className={`inline-flex whitespace-nowrap rounded-sm px-2 py-1 text-2xs font-semibold ${statusClass(row.status)}`}
          >
            {itemStatusLabels[row.status]}
          </span>
          {visibility.tone === 'hidden' ? (
            <span className="inline-flex whitespace-nowrap rounded-sm bg-error-soft px-2 py-1 text-2xs font-semibold text-error">
              {visibility.label}
            </span>
          ) : (
            <span className={`admin-items-visibility admin-items-visibility--${visibility.tone}`}>
              {visibility.label}
            </span>
          )}
          {row.boosting ? (
            <span
              className="admin-items-boost"
              title={
                row.boostedUntil
                  ? `Đang được đẩy đến ${formatDateTime(row.boostedUntil)}`
                  : 'Bài đang được đẩy lên đầu danh sách'
              }
            >
              Đang đẩy
            </span>
          ) : null}
        </div>
      </td>
      <td className="whitespace-nowrap text-right tabular-nums">
        {row.postFeePaid > 0 ? `${row.postFeePaid} Credit` : '—'}
      </td>
      <td className="text-center tabular-nums">{row.pendingRequestCount}</td>
      <td className="admin-items-time">
        <p>Tạo: {formatDateTime(row.createdAt)}</p>
        <p>Duyệt: {formatDateTime(row.approvedAt)}</p>
        <p>Hết hạn: {formatDateTime(row.expireAt)}</p>
      </td>
      <td>
        <div className="admin-items-actions">
          <Link
            to={`/admin/items/${row.id}`}
            className="admin-items-action admin-items-action--view"
          >
            Xem chi tiết
          </Link>
          {row.active ? (
            // Nút bị khoá không nhận sự kiện chuột nên tooltip đặt ở lớp bọc bên ngoài.
            <span className="admin-items-action-wrap" title={blockedReason ?? undefined}>
              <Button
                variant="danger"
                className="admin-items-action"
                disabled={Boolean(blockedReason)}
                aria-describedby={blockedReason ? blockedId : undefined}
                onClick={onHide}
              >
                Ẩn
              </Button>
              {blockedReason ? (
                <span id={blockedId} className="sr-only">
                  {blockedReason}
                </span>
              ) : null}
            </span>
          ) : (
            <Button variant="outline" className="admin-items-action" onClick={onUnhide}>
              Hiện lại
            </Button>
          )}
        </div>
      </td>
    </tr>
  );
}
