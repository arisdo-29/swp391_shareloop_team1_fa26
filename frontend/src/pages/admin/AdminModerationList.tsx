import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Alert, Button, EmptyState, ItemImage, PageHeader, SearchField, Select, SkeletonRows } from '../../components/ui';
import { StarRating } from '../../components/StarRating';
import { Pagination } from '../../components/Pagination';
import { adminModerationApi, type ReviewQueueItem, type ReviewRiskSignal } from '../../services/adminModerationApi';
import { catalogApi, type CategoryResponse } from '../../services/catalogApi';
import { adminErrorMessage } from './adminErrors';
import { displayModerationNote, displayRuleFlag } from './moderationDisplay';
import { useRealtimeRefresh } from '../../utils/useRealtimeRefresh';
import { useDebouncedValue } from '../../utils/useDebouncedValue';

const QUEUE_SIZE = 100;

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

/** Hàng chờ duyệt bài: mỗi bài một hàng gọn, bấm "Xem chi tiết và duyệt" để sang trang duyệt riêng. */
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
  useRealtimeRefresh(['MODERATION'], () => setReloadKey((value) => value + 1));

  useEffect(() => {
    let active = true;
    catalogApi.categories().then((result) => {
      if (active) setCategories(result);
    }).catch(() => {
      if (active) setCategories([]);
    });
    return () => { active = false; };
  }, [reloadKey]);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    adminModerationApi
      .pendingReviews({
        page,
        size: QUEUE_SIZE,
        ...(keyword ? { q: keyword } : {}),
        ...(categoryId ? { categoryId: Number(categoryId) } : {}),
        ...(risk ? { risk } : {}),
        ...(filterType === 'all' ? {} : { edited: filterType === 'edited' }),
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

  const hasFilters = Boolean(query.trim() || categoryId || risk || filterType !== 'all');

  return (
    <>
      <PageHeader title="Duyệt bài" description="Danh sách bài đăng đang chờ kiểm duyệt."
        action={<Link to="/admin/items" className="text-sm font-semibold text-primary hover:underline">Quản lý bài đăng</Link>}
      />
      <div className="mb-5 flex flex-col gap-3 rounded-lg bg-white p-3 ring-1 ring-border/80 lg:flex-row lg:flex-wrap lg:items-end">
        <div className="lg:min-w-48 lg:flex-1">
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
        <Select label="Danh mục" value={categoryId} onChange={(event) => {
          setCategoryId(event.target.value);
          setPage(0);
        }}>
          <option value="">Tất cả danh mục</option>
          {categories.map((category) => (
            <option key={category.id} value={category.id}>{category.name}</option>
          ))}
        </Select>
        <Select label="Dấu hiệu rủi ro" value={risk} onChange={(event) => {
          setRisk(event.target.value as ReviewRiskSignal | '');
          setPage(0);
        }}>
          <option value="">Tất cả dấu hiệu</option>
          <option value="LOW_TRUST">Ít sao uy tín</option>
          <option value="REPEATED_PAIR">Có cảnh báo giao dịch lặp</option>
          <option value="NEWCOMER">Người đăng mới</option>
        </Select>
        <Select
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
        <span className="self-center text-xs text-text-muted">{totalElements} bài đăng</span>
      </div>

      {notice ? (
        <div className="mb-4" role="status">
          <Alert tone="success">{notice}</Alert>
        </div>
      ) : null}
      {error ? (
        <div className="mb-4 space-y-3">
          <Alert tone="error">{error}</Alert>
          <Button variant="outline" onClick={() => setReloadKey((key) => key + 1)}>
            Thử tải lại
          </Button>
        </div>
      ) : null}

      {loading ? (
        <SkeletonRows label="Đang tải bài chờ duyệt" />
      ) : items.length ? (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Bài đăng</th>
                <th>Người đăng</th>
                <th>Uy tín</th>
                <th>Gửi duyệt lúc</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <QueueRow key={item.id} item={item} />
              ))}
            </tbody>
          </table>
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
        <Pagination page={page} size={QUEUE_SIZE} totalElements={totalElements} disabled={loading} onPageChange={setPage} />
      ) : null}
    </>
  );
}

function QueueRow({ item }: { item: ReviewQueueItem }) {
  const flag = displayRuleFlag(item);
  const note = displayModerationNote(item);
  return (
    <tr>
      <td className="max-w-md">
        <div className="flex min-w-0 items-center gap-3">
          <ItemImage
            src={item.thumbnailUrl ?? undefined}
            alt={`Ảnh ${item.title}`}
            loading="lazy"
            className="size-12 shrink-0 rounded-sm object-cover"
          />
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-1.5">
              <p className="font-semibold leading-6 text-text-primary">{item.title}</p>
              {item.isEdited || (item.editCount != null && item.editCount > 0) ? (
                <span className="rounded-sm bg-warning-soft px-1.5 py-0.5 text-2xs font-semibold text-warning">
                  {item.editCount ? `Đã chỉnh sửa (lần ${item.editCount})` : 'Đã chỉnh sửa'}
                </span>
              ) : null}
            </div>
            {flag || note ? (
              <p className="mt-1 flex flex-wrap items-center gap-2 text-xs">
                {flag ? (
                  <span className="rounded-sm bg-error-soft px-2 py-1 font-semibold text-error">{flag}</span>
                ) : null}
                {note && note !== flag ? <span className="text-text-secondary">{note}</span> : null}
              </p>
            ) : null}
          </div>
        </div>
      </td>
      <td>{item.donorName || 'Tài khoản đã xóa'}</td>
      <td className="whitespace-nowrap">
        {item.donorTrustStars == null ? 'Chưa có dữ liệu' : <StarRating value={item.donorTrustStars} />}
      </td>
      <td className="whitespace-nowrap text-xs">{new Date(item.queuedAt ?? item.createdAt).toLocaleString('vi-VN')}</td>
      <td className="text-right">
        <Link to={`/admin/moderation/${item.id}`}>
          <Button>Xem chi tiết và duyệt</Button>
        </Link>
      </td>
    </tr>
  );
}
