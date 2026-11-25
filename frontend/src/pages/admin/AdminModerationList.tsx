import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import {
  Alert,
  Button,
  EmptyState,
  ItemImage,
  PageHeader,
  SearchField,
  Select,
  SkeletonRows,
} from '../../components/ui';
import { StarRating } from '../../components/StarRating';
import { Pagination } from '../../components/Pagination';
import {
  adminModerationApi,
  type ReviewQueueItem,
  type ReviewQueueParams,
  type ReviewRiskSignal,
} from '../../services/adminModerationApi';
import { adminItemApi, type AdminItemRow } from '../../services/adminItemApi';
import { catalogApi, type CategoryResponse } from '../../services/catalogApi';
import { adminErrorMessage } from './adminErrors';
import { displayModerationNote, displayRuleFlag, offerTypeLabels } from './moderationDisplay';
import { useRealtimeRefresh } from '../../utils/useRealtimeRefresh';
import { useDebouncedValue } from '../../utils/useDebouncedValue';
import { DashboardGrid, DashboardStatCard } from './AdminDashboardCards';
import './admin-moderation.css';

const QUEUE_SIZE = 20;
// Cỡ trang khi đọc thêm dữ liệu phụ (danh mục, email, dấu hiệu rủi ro) và số trang tối đa được đọc.
const LOOKUP_SIZE = 100;
const LOOKUP_MAX_PAGES = 20;

const riskSignalLabels: Record<ReviewRiskSignal, string> = {
  LOW_TRUST: 'Ít sao uy tín',
  REPEATED_PAIR: 'Có cảnh báo giao dịch lặp',
  NEWCOMER: 'Người đăng mới',
};
const RISK_SIGNALS = Object.keys(riskSignalLabels) as ReviewRiskSignal[];

type StatValue = number | null | 'error';
interface QueueStats {
  total: StatValue;
  give: StatValue;
  swap: StatValue;
}
const EMPTY_STATS: QueueStats = { total: null, give: null, swap: null };

/** Thông tin phụ của một bài lấy từ trang Quản lý bài đăng: hàng chờ duyệt không trả các trường này. */
type ItemExtra = Pick<AdminItemRow, 'categoryName' | 'ownerEmail' | 'offerType'>;

async function loadMissingThumbnails(items: ReviewQueueItem[]) {
  return Promise.all(
    items.map(async (item) => {
      if (item.thumbnailUrl?.trim()) return item;
      try {
        const detail = await adminModerationApi.detail(item.id);
        const thumbnailUrl = detail.images.find((image) => image.trim());
        return thumbnailUrl ? { ...item, thumbnailUrl } : item;
      } catch {
        return item;
      }
    }),
  );
}

/**
 * Đọc lần lượt các trang tới khi gặp đủ các id cần tìm hoặc hết dữ liệu.
 * Trả về những id tìm thấy kèm giá trị tương ứng.
 */
async function collectByIds<T>(
  wanted: number[],
  fetchPage: (page: number) => Promise<{ rows: T[]; total: number | undefined }>,
  idOf: (row: T) => number,
) {
  const remaining = new Set(wanted);
  const found = new Map<number, T>();
  for (let page = 0; page < LOOKUP_MAX_PAGES && remaining.size; page += 1) {
    const { rows, total } = await fetchPage(page);
    for (const row of rows) {
      const id = idOf(row);
      if (remaining.delete(id)) found.set(id, row);
    }
    const loaded = (page + 1) * LOOKUP_SIZE;
    if (rows.length < LOOKUP_SIZE || (total !== undefined && loaded >= total)) break;
  }
  return found;
}

/** Hàng chờ duyệt bài: mỗi bài một hàng gọn, bấm "Xem và duyệt" để sang trang duyệt riêng. */
export function AdminModerationList() {
  const location = useLocation();
  const navigate = useNavigate();
  // Trang chi tiết gửi thông báo "đã duyệt" hoặc "đã từ chối" qua state khi quay về danh sách.
  const [notice] = useState(() => (location.state as { notice?: string } | null)?.notice ?? '');
  // Xóa state sau khi đọc để tải lại trang không hiện lại thông báo cũ.
  useEffect(() => {
    if (location.state) navigate(location.pathname, { replace: true, state: null });
  }, [location.pathname, location.state, navigate]);
  const [items, setItems] = useState<ReviewQueueItem[]>([]);
  // Bộ lọc đã dùng để tải items, dùng lại khi hỏi dấu hiệu rủi ro của đúng các bài đang hiện.
  const [itemsFilters, setItemsFilters] = useState<ReviewQueueParams>({});
  const [query, setQuery] = useState('');
  const keyword = useDebouncedValue(query.trim());
  const [filterType, setFilterType] = useState<'all' | 'new' | 'edited'>('all');
  const [categories, setCategories] = useState<CategoryResponse[]>([]);
  const [categoryId, setCategoryId] = useState('');
  const [risk, setRisk] = useState<ReviewRiskSignal | ''>('');
  const [page, setPage] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  const [stats, setStats] = useState<QueueStats>(EMPTY_STATS);
  const [extras, setExtras] = useState<Record<number, ItemExtra>>({});
  const [signals, setSignals] = useState<Record<number, ReviewRiskSignal[]> | null>(null);
  useRealtimeRefresh(['MODERATION'], () => setReloadKey((value) => value + 1));

  useEffect(() => {
    let active = true;
    catalogApi
      .categories()
      .then((result) => {
        if (active) setCategories(result);
      })
      .catch(() => {
        if (active) setCategories([]);
      });
    return () => {
      active = false;
    };
  }, [reloadKey]);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    const filters: ReviewQueueParams = {
      ...(keyword ? { q: keyword } : {}),
      ...(categoryId ? { categoryId: Number(categoryId) } : {}),
      ...(filterType === 'all' ? {} : { edited: filterType === 'edited' }),
    };
    adminModerationApi
      .pendingReviews({
        page,
        size: QUEUE_SIZE,
        ...filters,
        ...(risk ? { risk } : {}),
      })
      .then(async (response) => {
        if (!active) return;
        const total = response.totalElements ?? response.items.length;
        const lastPage = Math.max(0, Math.ceil(total / QUEUE_SIZE) - 1);
        if (page > lastPage) {
          setPage(lastPage);
          return;
        }
        const itemsWithThumbnails = await loadMissingThumbnails(response.items);
        if (active) {
          setItems(itemsWithThumbnails);
          setItemsFilters(filters);
          setTotalElements(total);
        }
      })
      .catch((caught) => {
        if (!active) return;
        setItems([]);
        setTotalElements(0);
        setError(adminErrorMessage(caught, 'Không tải được danh sách bài chờ duyệt.'));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [categoryId, filterType, keyword, page, reloadKey, risk]);

  // Thẻ thống kê: tổng số toàn hệ thống từ API Quản lý bài đăng, không phụ thuộc bộ lọc hay trang đang xem.
  useEffect(() => {
    let active = true;
    setStats(EMPTY_STATS);
    const load = (key: keyof QueueStats, offerType?: 'GIVE' | 'SWAP') => {
      adminItemApi
        .list({ status: 'PENDING_REVIEW', ...(offerType ? { offerType } : {}), page: 0, size: 1 })
        .then((response) => {
          if (active) setStats((current) => ({ ...current, [key]: response.totalElements }));
        })
        .catch(() => {
          if (active) setStats((current) => ({ ...current, [key]: 'error' }));
        });
    };
    load('total');
    load('give', 'GIVE');
    load('swap', 'SWAP');
    return () => {
      active = false;
    };
  }, [reloadKey]);

  // Danh mục, email và hình thức của các bài đang hiện: đọc từ danh sách bài chờ duyệt của trang Quản lý bài đăng.
  useEffect(() => {
    if (!items.length) return;
    let active = true;
    collectByIds(
      items.map((item) => item.id),
      (lookupPage) =>
        adminItemApi
          .list({ status: 'PENDING_REVIEW', page: lookupPage, size: LOOKUP_SIZE })
          .then((response) => ({ rows: response.items, total: response.totalElements })),
      (row) => row.id,
    )
      .then((found) => {
        if (!active) return;
        const next: Record<number, ItemExtra> = {};
        found.forEach((row, id) => {
          next[id] = {
            categoryName: row.categoryName,
            ownerEmail: row.ownerEmail,
            offerType: row.offerType,
          };
        });
        setExtras(next);
      })
      .catch(() => {
        // Thông tin phụ, lỗi thì các cột vẫn hiện phần dữ liệu có sẵn.
      });
    return () => {
      active = false;
    };
  }, [items]);

  // Dấu hiệu rủi ro từng bài: hàng chờ chỉ lọc theo dấu hiệu nên hỏi một lần cho mỗi dấu hiệu, cùng các bộ lọc đang chọn.
  useEffect(() => {
    if (!items.length) return;
    let active = true;
    setSignals(null);
    const wanted = items.map((item) => item.id);
    Promise.all(
      RISK_SIGNALS.map((signal) =>
        collectByIds(
          wanted,
          (lookupPage) =>
            adminModerationApi
              .pendingReviews({
                ...itemsFilters,
                risk: signal,
                page: lookupPage,
                size: LOOKUP_SIZE,
              })
              .then((response) => ({ rows: response.items, total: response.totalElements })),
          (row) => row.id,
        ).then((found) => ({ signal, ids: [...found.keys()] })),
      ),
    )
      .then((results) => {
        if (!active) return;
        const next: Record<number, ReviewRiskSignal[]> = {};
        for (const { signal, ids } of results) {
          for (const id of ids) (next[id] ??= []).push(signal);
        }
        setSignals(next);
      })
      .catch(() => {
        if (active) setSignals({});
      });
    return () => {
      active = false;
    };
  }, [items, itemsFilters]);

  const hasFilters = Boolean(query.trim() || categoryId || risk || filterType !== 'all');

  return (
    <section className="admin-moderation-page">
      <div className="admin-moderation-header">
        <PageHeader
          title="Duyệt bài"
          description="Danh sách bài đăng đang chờ kiểm duyệt."
          action={
            <Link to="/admin/items" className="text-sm font-semibold text-primary hover:underline">
              Quản lý bài đăng
            </Link>
          }
        />
      </div>

      <div className="admin-dash-vars" aria-label="Thống kê bài chờ duyệt">
        <DashboardGrid columns={3}>
          <QueueStat modifier="total" label="Tổng bài chờ duyệt" value={stats.total} />
          <QueueStat modifier="give" label="Bài cho tặng" value={stats.give} />
          <QueueStat modifier="swap" label="Bài trao đổi" value={stats.swap} />
        </DashboardGrid>
      </div>

      <div className="admin-moderation-filters">
        <div className="admin-moderation-filter-search">
          <span className="label">Tìm kiếm</span>
          <SearchField
            aria-label="Tìm bài chờ duyệt"
            placeholder="Tìm theo tiêu đề bài đăng..."
            value={query}
            onChange={(event) => {
              setQuery(event.target.value);
              setPage(0);
            }}
          />
        </div>
        <Select
          className="admin-moderation-filter-category"
          label="Danh mục"
          value={categoryId}
          onChange={(event) => {
            setCategoryId(event.target.value);
            setPage(0);
          }}
        >
          <option value="">Tất cả danh mục</option>
          {categories.map((category) => (
            <option key={category.id} value={category.id}>
              {category.name}
            </option>
          ))}
        </Select>
        <Select
          className="admin-moderation-filter-risk"
          label="Dấu hiệu rủi ro"
          value={risk}
          onChange={(event) => {
            setRisk(event.target.value as ReviewRiskSignal | '');
            setPage(0);
          }}
        >
          <option value="">Tất cả dấu hiệu</option>
          {RISK_SIGNALS.map((signal) => (
            <option key={signal} value={signal}>
              {riskSignalLabels[signal]}
            </option>
          ))}
        </Select>
        <Select
          className="admin-moderation-filter-type"
          label="Lọc theo loại bài"
          value={filterType}
          onChange={(event) => {
            setFilterType(event.target.value as 'all' | 'new' | 'edited');
            setPage(0);
          }}
        >
          <option value="all">Tất cả loại bài</option>
          <option value="new">Bài mới</option>
          <option value="edited">Bài sửa</option>
        </Select>
      </div>

      {notice ? (
        <div role="status">
          <Alert tone="success">{notice}</Alert>
        </div>
      ) : null}
      {error ? (
        <div className="space-y-3">
          <Alert tone="error">{error}</Alert>
          <Button variant="outline" onClick={() => setReloadKey((key) => key + 1)}>
            Thử tải lại
          </Button>
        </div>
      ) : null}

      {loading ? (
        <SkeletonRows label="Đang tải bài chờ duyệt" />
      ) : items.length ? (
        <div className="admin-moderation-table-section">
          <p className="admin-moderation-count">
            {totalElements.toLocaleString('vi-VN')} bài đăng khớp bộ lọc
          </p>
          <div className="table-wrap admin-moderation-table-wrap">
            <table className="data-table admin-moderation-table">
              <thead>
                <tr>
                  <th>Bài đăng</th>
                  <th>Người đăng</th>
                  <th className="text-center">Hình thức</th>
                  <th className="text-center">Uy tín</th>
                  <th>Dấu hiệu rủi ro</th>
                  <th>Gửi duyệt lúc</th>
                  <th className="text-center">Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {items.map((item) => (
                  <QueueRow
                    key={item.id}
                    item={item}
                    extra={extras[item.id]}
                    signals={signals ? (signals[item.id] ?? []) : null}
                  />
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ) : (
        <EmptyState
          title={hasFilters ? 'Không tìm thấy bài phù hợp' : 'Không có bài chờ duyệt'}
          text={
            hasFilters
              ? 'Hãy thử thay đổi từ khóa hoặc bộ lọc.'
              : 'Hiện chưa có bài đăng nào trong hàng chờ.'
          }
        />
      )}
      {totalElements > QUEUE_SIZE ? (
        <Pagination
          page={page}
          size={QUEUE_SIZE}
          totalElements={totalElements}
          disabled={loading}
          onPageChange={setPage}
        />
      ) : null}
    </section>
  );
}

function QueueStat({
  label,
  value,
  modifier,
}: {
  label: string;
  value: StatValue;
  modifier: 'total' | 'give' | 'swap';
}) {
  const icons = { total: 'moderation', give: 'manageItems', swap: 'transactions' } as const;
  return (
    <DashboardStatCard
      variant="system"
      icon={icons[modifier]}
      accent="gold"
      label={label}
      value={value === null ? '...' : value === 'error' ? '—' : value.toLocaleString('vi-VN')}
      note={value === 'error' ? 'Chưa tải được số liệu' : 'Toàn hệ thống'}
      loading={value === null}
    />
  );
}

function QueueRow({
  item,
  extra,
  signals,
}: {
  item: ReviewQueueItem;
  extra: ItemExtra | undefined;
  signals: ReviewRiskSignal[] | null;
}) {
  const flag = displayRuleFlag(item);
  const note = displayModerationNote(item);
  const hasRisk = Boolean(flag || (note && note !== flag) || signals?.length);
  return (
    <tr className="admin-moderation-row">
      <td>
        <div className="flex min-w-0 items-center gap-2.5">
          <ItemImage
            src={item.thumbnailUrl ?? undefined}
            alt={`Ảnh ${item.title}`}
            loading="lazy"
            className="size-12 shrink-0 rounded-sm object-cover"
          />
          <div className="min-w-0">
            <p className="admin-moderation-title" title={item.title}>
              {item.title}
            </p>
            <div className="mt-0.5 flex flex-wrap items-center gap-1.5">
              {extra?.categoryName ? (
                <span className="text-xs text-text-muted">{extra.categoryName}</span>
              ) : null}
              {item.isEdited || (item.editCount != null && item.editCount > 0) ? (
                <span className="rounded-sm bg-warning-soft px-1.5 py-0.5 text-2xs font-semibold text-warning">
                  {item.editCount ? `Đã chỉnh sửa (lần ${item.editCount})` : 'Đã chỉnh sửa'}
                </span>
              ) : null}
            </div>
          </div>
        </div>
      </td>
      <td className="min-w-0">
        <p className="admin-moderation-owner-name">{item.donorName || 'Tài khoản đã xóa'}</p>
        {extra?.ownerEmail ? (
          <p className="admin-moderation-owner-email">{extra.ownerEmail}</p>
        ) : null}
      </td>
      <td className="whitespace-nowrap text-center">
        {extra ? offerTypeLabels[extra.offerType] : '—'}
      </td>
      <td className="whitespace-nowrap text-center">
        {item.donorTrustStars == null ? (
          'Chưa có dữ liệu'
        ) : (
          <StarRating value={item.donorTrustStars} />
        )}
      </td>
      <td>
        <div className="admin-moderation-risks">
          {flag ? (
            <span className="admin-moderation-risk admin-moderation-risk--flag">{flag}</span>
          ) : null}
          {signals?.map((signal) => (
            <span key={signal} className="admin-moderation-risk admin-moderation-risk--signal">
              {riskSignalLabels[signal]}
            </span>
          ))}
          {note && note !== flag ? (
            <span className="admin-moderation-risk-note">{note}</span>
          ) : null}
          {signals === null && !flag ? (
            <span className="admin-moderation-risk-note">Đang kiểm tra...</span>
          ) : null}
          {signals !== null && !hasRisk ? (
            <span className="admin-moderation-risk-note">Không có</span>
          ) : null}
        </div>
      </td>
      <td className="admin-moderation-time">
        {new Date(item.queuedAt ?? item.createdAt).toLocaleString('vi-VN')}
      </td>
      <td>
        <div className="admin-moderation-actions">
          <Link to={`/admin/moderation/${item.id}`} className="admin-moderation-action">
            Xem và duyệt
          </Link>
        </div>
      </td>
    </tr>
  );
}
