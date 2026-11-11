import { useEffect, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { notificationApi, withoutHidden, type NotificationResponse } from '../services/notificationApi';
import { toBackendUserId } from '../services/requestApi';
import { Button, Icon, IconButton } from './ui';
import { useRealtimeRefresh } from '../utils/useRealtimeRefresh';

const PAGE_SIZE = 20;

export function NotificationMenu({
  userId,
  isHome,
  open,
  onOpenChange,
  onOpen,
}: {
  userId: string;
  isHome: boolean;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onOpen: () => void;
}) {
  const backendUserId = toBackendUserId(userId);
  const location = useLocation();
  // Admin xem trang thông báo trong khung quản trị, Member xem trong AppShell.
  const allNotificationsPath = location.pathname.startsWith('/admin') ? '/admin/notifications' : '/notifications';
  const activeUserId = useRef(backendUserId);
  const listLoadingRef = useRef(false);
  const markingIdRef = useRef<number | null>(null);
  const [notifications, setNotifications] = useState<NotificationResponse[]>([]);
  const [unreadCount, setUnreadCount] = useState<number | null>(null);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [listLoaded, setListLoaded] = useState(false);
  const [listLoading, setListLoading] = useState(false);
  const [listError, setListError] = useState('');
  const [countError, setCountError] = useState('');
  const [markingId, setMarkingId] = useState<number | null>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [filter, setFilter] = useState<'all' | 'unread'>('all');

  useEffect(() => {
    activeUserId.current = backendUserId;
    onOpenChange(false);
    setNotifications([]);
    setUnreadCount(backendUserId ? null : 0);
    setPage(0);
    setHasMore(false);
    setListLoaded(false);
    setListError('');
    setCountError('');
    setMarkingId(null);
    setDeletingId(null);
    setFilter('all');
    listLoadingRef.current = false;
    markingIdRef.current = null;
    if (!backendUserId) return;

    let active = true;
    notificationApi
      .unreadCount()
      .then((response) => {
        if (active) setUnreadCount(response.unreadCount);
      })
      .catch(() => {
        if (!active) return;
        setUnreadCount(null);
        setCountError('Không tải được số thông báo chưa đọc.');
      });
    return () => {
      active = false;
    };
  }, [backendUserId, onOpenChange]);

  const loadNotifications = async (nextPage: number, replace: boolean) => {
    if (!backendUserId || listLoadingRef.current) return;
    const requestedUserId = backendUserId;
    listLoadingRef.current = true;
    setListLoading(true);
    setListError('');
    try {
      const batch = await notificationApi.list({ page: nextPage, size: PAGE_SIZE });
      if (activeUserId.current !== requestedUserId) return;
      const response = withoutHidden(batch);
      setNotifications((current) => {
        if (replace) return response;
        const existingIds = new Set(current.map((notification) => notification.id));
        return [...current, ...response.filter((notification) => !existingIds.has(notification.id))];
      });
      setPage(nextPage);
      setHasMore(batch.length === PAGE_SIZE);
      setListLoaded(true);
    } catch {
      if (activeUserId.current === requestedUserId) {
        setListError('Không tải được danh sách thông báo.');
      }
    } finally {
      if (activeUserId.current === requestedUserId) {
        listLoadingRef.current = false;
        setListLoading(false);
      }
    }
  };

  useRealtimeRefresh(['NOTIFICATION'], () => {
    if (!backendUserId) return;
    void notificationApi.unreadCount().then((response) => setUnreadCount(response.unreadCount)).catch(() => undefined);
    if (open || listLoaded) void loadNotifications(0, true);
  });

  const markAsRead = async (notification: NotificationResponse) => {
    if (!backendUserId || notification.isRead || markingIdRef.current !== null) return;
    markingIdRef.current = notification.id;
    setMarkingId(notification.id);
    setListError('');
    try {
      const updated = await notificationApi.markRead(notification.id);
      if (activeUserId.current !== backendUserId) return;
      setNotifications((current) =>
        current.map((entry) => (entry.id === updated.id ? updated : entry)),
      );
      try {
        const count = await notificationApi.unreadCount();
        if (activeUserId.current === backendUserId) {
          setUnreadCount(count.unreadCount);
          setCountError('');
        }
      } catch {
        if (activeUserId.current === backendUserId) {
          setCountError('Đã đánh dấu đã đọc, nhưng chưa làm mới được số chưa đọc.');
        }
      }
    } catch {
      if (activeUserId.current === backendUserId) {
        setListError('Không thể đánh dấu thông báo đã đọc.');
      }
    } finally {
      if (activeUserId.current === backendUserId) {
        markingIdRef.current = null;
        setMarkingId(null);
      }
    }
  };

  // Dùng chung khoá markingIdRef với markAsRead để hai thao tác không chạy chồng lên nhau.
  const removeNotification = async (notification: NotificationResponse) => {
    if (!backendUserId || markingIdRef.current !== null) return;
    markingIdRef.current = notification.id;
    setDeletingId(notification.id);
    setListError('');
    try {
      await notificationApi.deleteNotification(notification.id);
      if (activeUserId.current !== backendUserId) return;
      setNotifications((current) => current.filter((entry) => entry.id !== notification.id));
      if (!notification.isRead) {
        setUnreadCount((count) => (count === null ? count : Math.max(0, count - 1)));
      }
    } catch {
      if (activeUserId.current === backendUserId) {
        setListError('Không thể xoá thông báo.');
      }
    } finally {
      if (activeUserId.current === backendUserId) {
        markingIdRef.current = null;
        setDeletingId(null);
      }
    }
  };

  const visibleNotifications =
    filter === 'unread' ? notifications.filter((notification) => !notification.isRead) : notifications;
  const busy = markingId !== null || deletingId !== null;

  const toggle = () => {
    const nextOpen = !open;
    onOpenChange(nextOpen);
    if (!nextOpen) return;
    onOpen();
    if (backendUserId && !listLoaded && !listLoading) {
      void loadNotifications(0, true);
    }
  };

  return (
    <div className="relative">
      <IconButton
        icon="bell"
        label={unreadCount ? `Thông báo, ${unreadCount} chưa đọc` : 'Thông báo'}
        className={isHome ? 'site-header-icon-button' : ''}
        aria-expanded={open}
        onClick={toggle}
      />
      {unreadCount !== null && unreadCount > 0 ? (
        <span className="pointer-events-none absolute -right-1 -top-1 grid min-h-5 min-w-5 place-items-center rounded-full bg-error px-1 text-[10px] font-bold text-white">
          {unreadCount > 99 ? '99+' : unreadCount}
        </span>
      ) : null}
      {open ? (
        <div className="absolute right-0 top-12 z-50 w-[min(360px,calc(100vw-2rem))] overflow-hidden rounded-lg bg-surface text-text-primary shadow-lg ring-1 ring-border">
          <div className="border-b border-border px-4 py-3">
            <h2 className="text-base font-bold">
              Thông báo
              {unreadCount ? <span className="ml-2 text-xs font-semibold text-error">{unreadCount} mới</span> : null}
            </h2>
            {countError ? <p className="mt-1 text-xs leading-5 text-error">{countError}</p> : null}
            <div role="group" aria-label="Lọc thông báo" className="mt-2 flex gap-1 rounded-md bg-surface-container p-1">
              {(
                [
                  ['all', 'Tất cả'],
                  ['unread', 'Chưa đọc'],
                ] as const
              ).map(([value, label]) => (
                <button
                  key={value}
                  type="button"
                  aria-pressed={filter === value}
                  onClick={() => setFilter(value)}
                  className={`min-h-9 flex-1 rounded-sm px-3 text-xs font-semibold transition ${filter === value ? 'bg-surface text-primary shadow-sm' : 'text-text-muted hover:text-text-primary'}`}
                >
                  {label}
                </button>
              ))}
            </div>
          </div>
          {!backendUserId ? (
            <p className="px-4 py-10 text-center text-sm text-text-muted">Chưa có thông báo.</p>
          ) : (
            <div className="max-h-96 overflow-y-auto">
              {listLoading && !listLoaded ? (
                <p className="px-4 py-10 text-center text-sm text-text-muted">Đang tải thông báo...</p>
              ) : null}
              {listError ? (
                <div className="px-4 py-5 text-center">
                  <p className="text-sm text-error">{listError}</p>
                  {!listLoaded ? (
                    <Button className="mt-3" size="sm" variant="outline" onClick={() => void loadNotifications(0, true)}>
                      Thử lại
                    </Button>
                  ) : null}
                </div>
              ) : null}
              {listLoaded && !visibleNotifications.length ? (
                <p className="px-4 py-10 text-center text-sm text-text-muted">
                  {filter === 'unread' && notifications.length ? 'Không có thông báo chưa đọc.' : 'Chưa có thông báo.'}
                </p>
              ) : null}
              {visibleNotifications.map((notification) => {
                return (
                  <article
                    key={notification.id}
                    className={`border-b border-border/70 px-4 py-3 ${notification.isRead ? 'bg-surface' : 'bg-primary-faint'}`}
                  >
                    <div className="flex items-start gap-3">
                      <span className={`mt-1 size-2 shrink-0 rounded-full ${notification.isRead ? 'bg-border' : 'bg-primary'}`} />
                      <div className="min-w-0 flex-1">
                        <div className="flex items-start justify-between gap-2">
                          <p className="text-sm font-bold">{notification.title}</p>
                          <button
                            type="button"
                            aria-label={`Xoá thông báo ${notification.title}`}
                            title="Xoá thông báo"
                            disabled={busy}
                            onClick={() => void removeNotification(notification)}
                            className="-mr-2 -mt-1 grid size-8 shrink-0 place-items-center rounded-md text-text-muted transition hover:bg-error-soft hover:text-error disabled:cursor-not-allowed disabled:opacity-40"
                          >
                            <Icon name="trash" className="size-4" />
                          </button>
                        </div>
                        <p className="mt-1 text-xs leading-5 text-text-muted">{notification.body}</p>
                        <p className="mt-1 text-[11px] text-text-muted">
                          {new Date(notification.createdAt).toLocaleString('vi-VN')}
                        </p>
                        <div className="mt-2 flex items-center gap-3 text-xs font-semibold">
                          {notification.link?.startsWith('/') && !notification.link.startsWith('//') && !notification.link.includes('\\') ? (
                            <Link
                              to={notification.link}
                              className="text-link hover:underline"
                              onClick={() => {
                                void markAsRead(notification);
                                onOpenChange(false);
                              }}
                            >
                              Xem chi tiết
                            </Link>
                          ) : null}
                          {!notification.isRead ? (
                            <button
                              type="button"
                              disabled={busy}
                              onClick={() => void markAsRead(notification)}
                              className="text-text-secondary hover:text-link disabled:cursor-not-allowed disabled:opacity-40"
                            >
                              {markingId === notification.id ? 'Đang cập nhật...' : 'Đánh dấu đã đọc'}
                            </button>
                          ) : null}
                        </div>
                      </div>
                    </div>
                  </article>
                );
              })}
              {hasMore ? (
                <div className="p-3 text-center">
                  <Button
                    size="sm"
                    variant="ghost"
                    disabled={listLoading}
                    onClick={() => void loadNotifications(page + 1, false)}
                  >
                    {listLoading ? 'Đang tải...' : 'Tải thêm'}
                  </Button>
                </div>
              ) : null}
            </div>
          )}
          {backendUserId ? (
            <Link
              to={allNotificationsPath}
              onClick={() => onOpenChange(false)}
              className="flex min-h-11 items-center justify-center border-t border-border text-sm font-semibold text-link hover:bg-primary-faint"
            >
              Xem tất cả thông báo
            </Link>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
