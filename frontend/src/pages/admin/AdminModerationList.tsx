import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Alert, Button, EmptyState, ItemImage, PageHeader, SearchField, SkeletonRows } from '../../components/ui';
import { adminModerationApi, type ReviewQueueItem } from '../../services/adminModerationApi';
import { adminErrorMessage } from './adminErrors';
import { displayModerationNote, displayRuleFlag } from './moderationDisplay';

// Hàng chờ có thứ tự ưu tiên từ Backend nên lấy một lượt đủ lớn và tìm kiếm ngay trên danh sách này.
const QUEUE_SIZE = 100;

function normalize(value: string) {
  return value.trim().toLowerCase();
}

function matchesQuery(item: ReviewQueueItem, query: string) {
  const text = [item.title, item.donorName ?? '', displayRuleFlag(item)];
  return normalize(text.join(' ')).includes(normalize(query));
}

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

/** Hàng chờ duyệt bài: mỗi bài một hàng gọn, bấm "Xem chi tiết & duyệt" để sang trang duyệt riêng. */
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
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    adminModerationApi
      .pendingReviews({ page: 0, size: QUEUE_SIZE })
      .then(async (response) => {
        const itemsWithThumbnails = await loadMissingThumbnails(response.items);
        if (active) setItems(itemsWithThumbnails);
      })
      .catch((caught) => {
        if (!active) return;
        setItems([]);
        setError(adminErrorMessage(caught, 'Không tải được danh sách bài chờ duyệt.'));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [reloadKey]);

  const rows = query.trim() ? items.filter((item) => matchesQuery(item, query)) : items;

  return (
    <>
      <PageHeader title="Duyệt bài" description="Danh sách bài đăng đang chờ kiểm duyệt." />
      <div className="mb-5 flex flex-col gap-3 rounded-lg bg-white p-3 ring-1 ring-border/80 sm:flex-row sm:items-center">
        <div className="flex-1">
          <SearchField
            aria-label="Tìm bài chờ duyệt"
            placeholder="Tìm theo tiêu đề, người đăng hoặc mức rủi ro..."
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
        </div>
        <span className="text-xs text-text-muted">{items.length} bài đăng chờ</span>
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
      ) : rows.length ? (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Bài đăng</th>
                <th>Người đăng</th>
                <th>Uy tín</th>
                <th>Thời gian đăng</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {rows.map((item) => (
                <QueueRow key={item.id} item={item} />
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <EmptyState
          title={query ? 'Không tìm thấy bài phù hợp' : 'Không có bài chờ duyệt'}
          text={query ? 'Hãy thử từ khóa khác.' : 'Hiện chưa có bài đăng nào trong hàng chờ.'}
        />
      )}
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
            <p className="font-semibold leading-6 text-text-primary">{item.title}</p>
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
        {item.donorTrustStars == null ? 'Chưa có dữ liệu' : `${item.donorTrustStars}/5 sao`}
      </td>
      <td className="whitespace-nowrap text-xs">{new Date(item.createdAt).toLocaleString('vi-VN')}</td>
      <td className="text-right">
        <Link to={`/admin/moderation/${item.id}`}>
          <Button>Xem chi tiết & duyệt</Button>
        </Link>
      </td>
    </tr>
  );
}
