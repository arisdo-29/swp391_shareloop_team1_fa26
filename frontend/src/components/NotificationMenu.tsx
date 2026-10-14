import { useEffect, useRef, useState } from 'react';
import { notificationApi, type NotificationResponse } from '../services/notificationApi';
import { toBackendUserId } from '../services/requestApi';
import { Button, IconButton } from './ui';

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
  }, [backendUserId]);

  const loadNotifications = async (nextPage: number, replace: boolean) => {
    if (!backendUserId || listLoadingRef.current) return;
    const requestedUserId = backendUserId;
    listLoadingRef.current = true;
    setListLoading(true);
    setListError('');
    try {
      const response = await notificationApi.list({ page: nextPage, size: PAGE_SIZE });
      if (activeUserId.current !== requestedUserId) return;
      setNotifications((current) => {
        if (replace) return response;
        const existingIds = new Set(current.map((notification) => notification.id));
        return [...current, ...response.filter((notification) => !existingIds.has(notification.id))];
      });
      setPage(nextPage);
      setHasMore(response.length === PAGE_SIZE);
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
        className={isHome ? 'text-white hover:bg-white/10' : ''}
        aria-expanded={open}
        onClick={toggle}
      />
      {unreadCount !== null && unreadCount > 0 ? (
        <span className="pointer-events-none absolute -right-1 -top-1 grid min-h-5 min-w-5 place-items-center rounded-full bg-error px-1 text-[10px] font-bold text-white">
          {unreadCount > 99 ? '99+' : unreadCount}
        </span>
      ) : null}
      {open ? (
        <div className="absolute right-0 top-12 z-50 w-[min(360px,calc(100vw-2rem))] overflow-hidden rounded-lg bg-white text-text-primary shadow-lg ring-1 ring-border">
          <div className="border-b border-border px-4 py-3">
            <h2 className="text-sm font-bold">Thông báo</h2>
            {countError ? <p className="mt-1 text-xs leading-5 text-error">{countError}</p> : null}
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
              {listLoaded && !notifications.length ? (
                <p className="px-4 py-10 text-center text-sm text-text-muted">Chưa có thông báo.</p>
              ) : null}
              {notifications.map((notification) => {
                return (
                  <article
                    key={notification.id}
                    className={`border-b border-border/70 px-4 py-3 ${notification.isRead ? 'bg-white' : 'bg-primary-faint'}`}
                  >
                    <div className="flex items-start gap-3">
                      <span className={`mt-1 size-2 shrink-0 rounded-full ${notification.isRead ? 'bg-border' : 'bg-primary'}`} />
                      <div className="min-w-0 flex-1">
                        <p className="text-sm font-bold">{notification.title}</p>
                        <p className="mt-1 text-xs leading-5 text-text-muted">{notification.body}</p>
                        <p className="mt-1 text-[11px] text-text-muted">
                          {new Date(notification.createdAt).toLocaleString('vi-VN')}
                        </p>
                        <div className="mt-2 flex items-center gap-3 text-xs font-semibold">
                          {!notification.isRead ? (
                            <button
                              type="button"
                              disabled={markingId !== null}
                              onClick={() => void markAsRead(notification)}
                              className="text-text-secondary hover:text-primary disabled:cursor-not-allowed disabled:opacity-40"
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
        </div>
      ) : null}
    </div>
  );
}
