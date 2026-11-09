import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Pagination } from '../../components/Pagination';
import { toast } from '../../components/toast';
import {
  Alert,
  Button,
  EmptyState,
  Icon,
  PageHeader,
  SearchField,
  Select,
  SkeletonRows,
} from '../../components/ui';
import {
  notificationApi,
  notificationCategory,
  notificationCategoryLabels,
  type NotificationCategory,
  type NotificationResponse,
} from '../../services/notificationApi';
import { useRealtimeRefresh } from '../../utils/useRealtimeRefresh';

type CategoryFilter = 'all' | Exclude<NotificationCategory, 'other'>;
type ReadFilter = 'all' | 'unread' | 'read';

const DEFAULT_PAGE_SIZE = 20;

const readFilterOptions: Array<[ReadFilter, string]> = [
  ['all', 'Tất cả'],
  ['unread', 'Chưa đọc'],
  ['read', 'Đã đọc'],
];

/** Link trong thông báo chỉ được là đường dẫn nội bộ, không cho mở trang ngoài. */
function isInternalLink(link: string | null): link is string {
  return Boolean(link?.startsWith('/') && !link.startsWith('//') && !link.includes('\\'));
}

function matchesFilters(
  notification: NotificationResponse,
  keyword: string,
  category: CategoryFilter,
  readFilter: ReadFilter,
) {
  if (category !== 'all' && notificationCategory(notification.type) !== category) return false;
  if (readFilter === 'unread' && notification.isRead) return false;
  if (readFilter === 'read' && !notification.isRead) return false;
  if (!keyword) return true;
  const text = `${notification.title} ${notification.body}`.toLocaleLowerCase('vi-VN');
  return text.includes(keyword);
}

/**
 * Trang xem tất cả thông báo của tôi. Backend chưa phân trang có tổng số và chưa lọc theo loại,
 * nên trang tải hết một lần rồi tìm kiếm, lọc và phân trang ngay trên trình duyệt.
 */
export function Notifications({ inAdmin = false }: { inAdmin?: boolean }) {
  const [notifications, setNotifications] = useState<NotificationResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  useRealtimeRefresh(['NOTIFICATION'], () => setReloadKey((value) => value + 1));
  const [busy, setBusy] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [category, setCategory] = useState<CategoryFilter>('all');
  const [readFilter, setReadFilter] = useState<ReadFilter>('all');
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setLoadError('');
    notificationApi
      .listAll()
      .then((response) => {
        if (active) setNotifications(response);
      })
      .catch(() => {
        if (active) setLoadError('Không tải được danh sách thông báo.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [reloadKey]);

  const normalizedKeyword = keyword.trim().toLocaleLowerCase('vi-VN');
  const filtered = notifications.filter((notification) =>
    matchesFilters(notification, normalizedKeyword, category, readFilter),
  );
  const lastPage = Math.max(0, Math.ceil(filtered.length / size) - 1);
  // Sau khi xoá bớt, trang hiện tại có thể vượt quá trang cuối; lùi về trang cuối còn dữ liệu.
  const currentPage = Math.min(page, lastPage);
  const pageEntries = filtered.slice(currentPage * size, (currentPage + 1) * size);
  const selected = notifications.filter((notification) => selectedIds.has(notification.id));
  const allOnPageSelected =
    pageEntries.length > 0 && pageEntries.every((entry) => selectedIds.has(entry.id));
  const hasRead = notifications.some((notification) => notification.isRead);

  // Đổi bộ lọc thì về trang đầu và bỏ chọn, tránh thao tác nhầm lên dòng đang bị ẩn khỏi danh sách.
  function changeFilter(apply: () => void) {
    apply();
    setPage(0);
    setSelectedIds(new Set());
  }

  function toggleSelected(id: number) {
    setSelectedIds((current) => {
      const next = new Set(current);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  function toggleSelectPage() {
    setSelectedIds((current) => {
      const next = new Set(current);
      for (const entry of pageEntries) {
        if (allOnPageSelected) next.delete(entry.id);
        else next.add(entry.id);
      }
      return next;
    });
  }

  /** Chạy một thao tác hàng loạt: khoá nút, báo lỗi bằng toast, luôn mở khoá lại. */
  async function runBulkAction(action: () => Promise<void>, errorMessage: string) {
    if (busy) return;
    setBusy(true);
    try {
      await action();
    } catch {
      toast.error(errorMessage);
    } finally {
      setBusy(false);
    }
  }

  function markSelectedRead() {
    void runBulkAction(async () => {
      const updated = await notificationApi.markManyRead(selected);
      const updatedById = new Map(updated.map((entry) => [entry.id, entry]));
      setNotifications((current) => current.map((entry) => updatedById.get(entry.id) ?? entry));
      setSelectedIds(new Set());
    }, 'Không thể đánh dấu đã đọc. Hãy thử lại.');
  }

  function deleteSelected() {
    const ids = selected.map((entry) => entry.id);
    void runBulkAction(async () => {
      await notificationApi.deleteMultiple(ids);
      setNotifications((current) => current.filter((entry) => !ids.includes(entry.id)));
      setSelectedIds(new Set());
      toast.success(`Đã xoá ${ids.length} thông báo.`);
    }, 'Không thể xoá thông báo. Hãy thử lại.');
  }

  function clearRead() {
    void runBulkAction(async () => {
      const removed = await notificationApi.clearRead();
      setNotifications((current) => current.filter((entry) => !entry.isRead));
      setSelectedIds(new Set());
      toast.success(
        removed ? `Đã dọn ${removed} thông báo đã đọc.` : 'Không có thông báo đã đọc để dọn.',
      );
    }, 'Không thể dọn thông báo đã đọc. Hãy thử lại.');
  }

  async function openNotification(notification: NotificationResponse) {
    if (notification.isRead) return;
    try {
      const updated = await notificationApi.markRead(notification.id);
      setNotifications((current) =>
        current.map((entry) => (entry.id === updated.id ? updated : entry)),
      );
    } catch {
      // Không chặn việc mở link chỉ vì chưa đánh dấu được đã đọc.
    }
  }

  const content = (
    <>
      <PageHeader
        title="Thông báo"
        description="Xem lại mọi thông báo về bài đăng, giao dịch, khiếu nại và Credit của bạn."
      />
      <NotificationFilters
        keyword={keyword}
        category={category}
        readFilter={readFilter}
        onKeywordChange={(value) => changeFilter(() => setKeyword(value))}
        onCategoryChange={(value) => changeFilter(() => setCategory(value))}
        onReadFilterChange={(value) => changeFilter(() => setReadFilter(value))}
      />
      {loading ? (
        <div className="card p-5">
          <SkeletonRows rows={6} label="Đang tải thông báo" />
        </div>
      ) : loadError ? (
        <div className="space-y-3">
          <Alert tone="error">{loadError}</Alert>
          <Button variant="outline" onClick={() => setReloadKey((key) => key + 1)}>
            Thử lại
          </Button>
        </div>
      ) : (
        <section aria-label="Danh sách thông báo">
          <BulkActionBar
            selectedCount={selected.length}
            hasSelectedUnread={selected.some((entry) => !entry.isRead)}
            hasRead={hasRead}
            allOnPageSelected={allOnPageSelected}
            pageHasEntries={pageEntries.length > 0}
            busy={busy}
            onToggleSelectPage={toggleSelectPage}
            onMarkRead={markSelectedRead}
            onDelete={deleteSelected}
            onClearRead={clearRead}
          />
          {pageEntries.length ? (
            <ul className="card divide-y divide-border overflow-hidden">
              {pageEntries.map((notification) => (
                <NotificationRow
                  key={notification.id}
                  notification={notification}
                  selected={selectedIds.has(notification.id)}
                  disabled={busy}
                  onToggle={() => toggleSelected(notification.id)}
                  onOpen={() => void openNotification(notification)}
                />
              ))}
            </ul>
          ) : (
            <EmptyState
              title={notifications.length ? 'Không có thông báo phù hợp' : 'Chưa có thông báo'}
              text={
                notifications.length
                  ? 'Thử đổi từ khoá hoặc bộ lọc để xem thêm thông báo.'
                  : 'Khi có cập nhật về bài đăng, giao dịch hay khiếu nại, thông báo sẽ hiện ở đây.'
              }
            />
          )}
          <Pagination
            page={currentPage}
            size={size}
            totalElements={filtered.length}
            disabled={busy}
            onPageChange={setPage}
            onSizeChange={(nextSize) => {
              setSize(nextSize);
              setPage(0);
            }}
          />
        </section>
      )}
    </>
  );

  // Trong khung Admin đã có lề và độ rộng riêng, không bọc thêm page-shell.
  return inAdmin ? content : <div className="page-shell py-6 sm:py-10">{content}</div>;
}

function NotificationFilters({
  keyword,
  category,
  readFilter,
  onKeywordChange,
  onCategoryChange,
  onReadFilterChange,
}: {
  keyword: string;
  category: CategoryFilter;
  readFilter: ReadFilter;
  onKeywordChange: (value: string) => void;
  onCategoryChange: (value: CategoryFilter) => void;
  onReadFilterChange: (value: ReadFilter) => void;
}) {
  return (
    <div className="mb-4 grid gap-3 md:grid-cols-[minmax(0,1fr)_220px_auto] md:items-center">
      <SearchField
        type="search"
        aria-label="Tìm thông báo"
        placeholder="Tìm theo tiêu đề hoặc nội dung"
        value={keyword}
        onChange={(event) => onKeywordChange(event.target.value)}
      />
      <Select
        aria-label="Lọc theo loại"
        value={category}
        onChange={(event) => onCategoryChange(event.target.value as CategoryFilter)}
      >
        <option value="all">Tất cả loại</option>
        {Object.entries(notificationCategoryLabels).map(([value, label]) => (
          <option key={value} value={value}>
            {label}
          </option>
        ))}
      </Select>
      <div
        role="group"
        aria-label="Lọc theo trạng thái"
        className="flex gap-1 rounded-md bg-surface-container p-1"
      >
        {readFilterOptions.map(([value, label]) => (
          <button
            key={value}
            type="button"
            aria-pressed={readFilter === value}
            onClick={() => onReadFilterChange(value)}
            className={`min-h-9 flex-1 rounded-sm px-3 text-sm font-semibold transition md:flex-none ${readFilter === value ? 'bg-white text-primary shadow-sm' : 'text-text-muted hover:text-text-primary'}`}
          >
            {label}
          </button>
        ))}
      </div>
    </div>
  );
}

function BulkActionBar({
  selectedCount,
  hasSelectedUnread,
  hasRead,
  allOnPageSelected,
  pageHasEntries,
  busy,
  onToggleSelectPage,
  onMarkRead,
  onDelete,
  onClearRead,
}: {
  selectedCount: number;
  hasSelectedUnread: boolean;
  hasRead: boolean;
  allOnPageSelected: boolean;
  pageHasEntries: boolean;
  busy: boolean;
  onToggleSelectPage: () => void;
  onMarkRead: () => void;
  onDelete: () => void;
  onClearRead: () => void;
}) {
  return (
    <div className="mb-3 flex flex-wrap items-center gap-2">
      <label className="mr-auto flex min-h-11 items-center gap-2 text-sm font-semibold text-text-secondary">
        <input
          type="checkbox"
          className="size-4 accent-primary"
          checked={allOnPageSelected}
          disabled={busy || !pageHasEntries}
          onChange={onToggleSelectPage}
        />
        {selectedCount ? `Đã chọn ${selectedCount}` : 'Chọn cả trang'}
      </label>
      <Button
        variant="outline"
        size="sm"
        disabled={busy || !hasSelectedUnread}
        onClick={onMarkRead}
      >
        <Icon name="check" className="size-4" />
        Đánh dấu đã đọc
      </Button>
      <Button variant="danger" size="sm" disabled={busy || !selectedCount} onClick={onDelete}>
        <Icon name="trash" className="size-4" />
        Xóa đã chọn
      </Button>
      <Button variant="ghost" size="sm" disabled={busy || !hasRead} onClick={onClearRead}>
        Dọn sạch thông báo đã đọc
      </Button>
    </div>
  );
}

function NotificationRow({
  notification,
  selected,
  disabled,
  onToggle,
  onOpen,
}: {
  notification: NotificationResponse;
  selected: boolean;
  disabled: boolean;
  onToggle: () => void;
  onOpen: () => void;
}) {
  const category = notificationCategory(notification.type);
  return (
    <li
      className={`flex gap-3 px-4 py-3 sm:px-5 ${notification.isRead ? 'bg-white' : 'bg-primary-faint'}`}
    >
      <input
        type="checkbox"
        className="mt-1 size-4 shrink-0 accent-primary"
        aria-label={`Chọn thông báo ${notification.title}`}
        checked={selected}
        disabled={disabled}
        onChange={onToggle}
      />
      <span
        aria-hidden
        className={`mt-2 size-2 shrink-0 rounded-full ${notification.isRead ? 'bg-border' : 'bg-primary'}`}
      />
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <p className="text-sm font-bold">{notification.title}</p>
          {category !== 'other' ? (
            <span className="rounded-sm bg-surface-container px-2 py-0.5 text-2xs font-semibold text-text-secondary">
              {notificationCategoryLabels[category]}
            </span>
          ) : null}
          {!notification.isRead ? <span className="sr-only">Chưa đọc</span> : null}
        </div>
        <p className="mt-1 text-sm leading-6 text-text-secondary">{notification.body}</p>
        <div className="mt-1 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs">
          <time dateTime={notification.createdAt} className="text-text-muted">
            {new Date(notification.createdAt).toLocaleString('vi-VN')}
          </time>
          {isInternalLink(notification.link) ? (
            <Link
              to={notification.link}
              onClick={onOpen}
              className="font-semibold text-link hover:underline"
            >
              Xem chi tiết
            </Link>
          ) : null}
        </div>
      </div>
    </li>
  );
}
