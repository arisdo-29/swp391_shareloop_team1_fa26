import { ThemeSelect } from '../components/ThemeSelect';
import { Suspense, useEffect, useRef, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '../app/hooks';
import { actions, selectCurrentUser } from '../app/store';
import { Button, Icon, IconButton } from '../components/ui';
import { authApi } from '../services/authApi';
import { NotificationMenu } from '../components/NotificationMenu';
import { ShareLoopLogo } from '../components/ShareLoopLogo';
import { WalletProvider } from '../app/WalletContext';
import { useWallet } from '../app/useWallet';
import { requestApi, toBackendUserId } from '../services/requestApi';
import { isRequestUnread } from '../utils/request';
import { loginPath } from '../utils/authRedirect';
import { connectRealtime } from '../services/realtime';
import { useRealtimeRefresh } from '../utils/useRealtimeRefresh';
import { tokenStorage } from '../services/http';

// Trang tài khoản dùng khung riêng (AuthLayout) nên không cần chân trang.
const authPaths = new Set([
  '/login',
  '/register',
  '/verify-email',
  '/forgot-password',
  '/forgot-password/verify',
  '/reset-password',
]);

function ScrollToTop() {
  const { pathname } = useLocation();

  useEffect(() => {
    window.scrollTo(0, 0);
  }, [pathname]);

  return null;
}

export function AppShell() {
  return (
    <WalletProvider>
      <AppShellContent />
    </WalletProvider>
  );
}

function AppShellContent() {
  const location = useLocation();
  const { pathname } = location;
  const navigate = useNavigate();
  const user = useAppSelector(selectCurrentUser);
  const authenticatedUserId = user?.id;
  const { wallet } = useWallet();
  const isAdmin = user?.role === 'admin';
  const isHome = pathname === '/' && !isAdmin;
  const dispatch = useAppDispatch();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const [notificationOpen, setNotificationOpen] = useState(false);
  const [hasUnreadChat, setHasUnreadChat] = useState(false);
  const [unreadReloadKey, setUnreadReloadKey] = useState(0);
  const [homeHeaderSolid, setHomeHeaderSolid] = useState(false);
  const headerActionsRef = useRef<HTMLDivElement>(null);
  useRealtimeRefresh(['MESSAGE', 'REQUEST'], () => setUnreadReloadKey((value) => value + 1));

  useEffect(() => {
    if (!authenticatedUserId || !tokenStorage.get()) return;
    return connectRealtime('/api/v1/notifications/events', true);
  }, [authenticatedUserId]);

  useEffect(() => {
    if (
      authenticatedUserId ||
      (pathname !== '/' &&
        pathname !== '/browse' &&
        !pathname.startsWith('/items/') &&
        !pathname.startsWith('/product/'))
    )
      return;
    return connectRealtime('/api/v1/items/events', false);
  }, [authenticatedUserId, pathname]);

  useEffect(() => {
    if (!isHome) {
      setHomeHeaderSolid(false);
      return undefined;
    }

    const updateHeaderBackground = () => {
      const hero = document.querySelector<HTMLElement>('[data-home-hero]');
      const heroBottom = hero?.getBoundingClientRect().bottom ?? window.innerHeight;
      setHomeHeaderSolid(heroBottom <= 68);
    };

    updateHeaderBackground();
    window.addEventListener('scroll', updateHeaderBackground, { passive: true });
    window.addEventListener('resize', updateHeaderBackground);
    return () => {
      window.removeEventListener('scroll', updateHeaderBackground);
      window.removeEventListener('resize', updateHeaderBackground);
    };
  }, [isHome]);

  useEffect(() => {
    if (!user || isAdmin) {
      setHasUnreadChat(false);
      return;
    }
    const numericUserId = toBackendUserId(user.id);
    if (!numericUserId) return;

    let active = true;
    let intervalId: number | undefined;

    const checkUnread = async () => {
      if (document.visibilityState === 'hidden') return;
      try {
        const requests = await requestApi.mine(numericUserId);
        if (!active) return;
        const unreadExists = requests.some((req) => isRequestUnread(req.lastMessageAt, req.id));
        setHasUnreadChat(unreadExists);
      } catch {
        // Ignored
      }
    };

    const resetInterval = () => {
      if (intervalId !== undefined) {
        window.clearInterval(intervalId);
      }
      intervalId = window.setInterval(checkUnread, 30_000);
    };

    void checkUnread();
    resetInterval();

    const handleSeen = () => void checkUnread();
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        void checkUnread();
        resetInterval();
      }
    };

    window.addEventListener('shareloop_chat_seen', handleSeen);
    document.addEventListener('visibilitychange', handleVisibilityChange);

    return () => {
      active = false;
      window.removeEventListener('shareloop_chat_seen', handleSeen);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      if (intervalId !== undefined) {
        window.clearInterval(intervalId);
      }
    };
  }, [user, isAdmin, pathname, unreadReloadKey]);

  useEffect(() => {
    setMobileOpen(false);
    setProfileOpen(false);
    setNotificationOpen(false);
  }, [location.hash, location.pathname, location.search]);

  useEffect(() => {
    const closeDropdowns = () => {
      setProfileOpen(false);
      setNotificationOpen(false);
    };
    const handlePointerDown = (event: PointerEvent) => {
      if (!headerActionsRef.current?.contains(event.target as Node)) closeDropdowns();
    };
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') closeDropdowns();
    };

    document.addEventListener('pointerdown', handlePointerDown);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, []);
  const nav = isAdmin
    ? [
        ['/admin', 'Dashboard'],
        ['/admin/moderation', 'Quản lý món đồ'],
        ['/admin/users', 'Người dùng'],
        ['/admin/complaints', 'Báo cáo / Khiếu nại'],
        ['/admin/transactions', 'Giao dịch'],
        ['/admin/finance', 'Thống kê'],
      ]
    : user
      ? [
          ['/', 'Trang chủ'],
          ['/browse', 'Tìm đồ'],
          ['/activities', 'Hoạt động'],
          ['/ai', 'Trợ lý AI'],
          ['/about', 'Giới thiệu'],
          ['/messages', 'Tin nhắn'],
        ]
      : [
          ['/', 'Trang chủ'],
          ['/browse', 'Tìm đồ'],
          ['/ai', 'Trợ lý AI'],
          ['/about', 'Giới thiệu'],
        ];
  const closeMenus = () => {
    setMobileOpen(false);
    setProfileOpen(false);
    setNotificationOpen(false);
  };
  const logout = () => {
    void authApi.logout();
    dispatch(actions.logout());
    closeMenus();
    navigate('/login', { replace: true });
  };
  return (
    <div className="member-layout flex min-h-screen flex-col bg-background">
      <ScrollToTop />
      <a
        href="#main-content"
        className="fixed left-3 top-3 z-50 -translate-y-20 rounded-md bg-primary px-4 py-2 text-sm font-semibold text-white focus:translate-y-0"
      >
        Bỏ qua điều hướng
      </a>
      <header
        className={`site-header top-0 z-30 border-b backdrop-blur-md ${
          isHome
            ? `site-header-home fixed inset-x-0 ${homeHeaderSolid ? 'site-header-home-solid' : ''}`
            : 'sticky'
        }`}
      >
        <div className="wide-shell flex min-h-[68px] items-center gap-5 py-2">
          <Link
            to={isAdmin ? '/admin' : '/'}
            onClick={closeMenus}
            className="flex shrink-0 items-center gap-2.5"
          >
            <span
              className={`grid size-9 place-items-center rounded-md text-white ${
                isHome ? 'bg-white/15 ring-1 ring-white/25' : 'bg-accent'
              }`}
            >
              <ShareLoopLogo className="size-5" />
            </span>
            <span className="hidden text-base font-extrabold text-white md:inline">
              {isAdmin ? (
                'SHARELOOP ADMIN'
              ) : (
                <>
                  SHARE<span className="text-white">LOOP</span>
                </>
              )}
            </span>
          </Link>
          {!isAdmin ? (
            <nav className="hidden min-w-0 flex-1 items-center gap-0.5 xl:flex">
              {nav.map(([to, label]) => (
                <NavLink
                  end={to === '/'}
                  key={to}
                  to={to}
                  className={({ isActive }) =>
                    `inline-flex min-h-11 items-center gap-1.5 whitespace-nowrap rounded-md px-3 py-2 text-sm font-medium transition focus-visible:bg-white/[0.14] focus-visible:outline-none ${
                      isActive
                        ? `${isHome ? 'bg-white/[0.16]' : 'bg-white/[0.14]'} text-white`
                        : `${isHome ? 'text-white' : 'text-white/80'} hover:bg-white/10 hover:text-white`
                    }`
                  }
                >
                  <span>{label}</span>
                  {to === '/messages' && hasUnreadChat ? (
                    <span className="size-2 rounded-full bg-error" title="Có tin nhắn mới" />
                  ) : null}
                </NavLink>
              ))}
            </nav>
          ) : null}
          <div
            ref={headerActionsRef}
            className="ml-auto flex min-w-0 items-center gap-2"
            onClick={(event) => {
              const action = (event.target as HTMLElement).closest('a, button');
              if (action && !action.hasAttribute('aria-expanded')) {
                setProfileOpen(false);
                setNotificationOpen(false);
              }
            }}
          >
            <ThemeSelect />
            {user ? (
              <>
                {!isAdmin ? (
                  <>
                    <Link
                      to="/credit"
                      aria-label={`Ví Credit: ${wallet ? wallet.available : 'chưa tải'} Credit khả dụng`}
                      className="site-header-credit hidden min-h-11 shrink-0 items-center rounded-md border px-3 py-2 text-xs font-semibold transition sm:flex"
                    >
                      {wallet ? wallet.available : '--'} Credit
                    </Link>
                    <Link to="/post" className="hidden shrink-0 sm:block">
                      <Button className="bronze-action">Đăng đồ</Button>
                    </Link>
                  </>
                ) : null}
                <div className="shrink-0">
                  <NotificationMenu
                    userId={user.id}
                    isHome
                    open={notificationOpen}
                    onOpenChange={setNotificationOpen}
                    onOpen={() => setProfileOpen(false)}
                  />
                </div>
                <div className="relative min-w-0">
                  <button
                    onClick={() => {
                      setProfileOpen(!profileOpen);
                      setNotificationOpen(false);
                    }}
                    className="flex min-h-11 min-w-11 max-w-full items-center gap-1 rounded-md px-2 transition hover:bg-white/10"
                    aria-expanded={profileOpen}
                  >
                    <Icon name="user" className="size-5 shrink-0 sm:hidden" />
                    <span
                      title={user.name}
                      className="site-header-account-name min-w-0 text-right text-sm font-semibold leading-tight"
                    >
                      {user.name}
                    </span>
                    <Icon name="chevronDown" className="hidden size-4 shrink-0 text-white/75 sm:block" />
                  </button>
                  {profileOpen ? (
                    <div className="absolute right-0 top-12 w-60 rounded-lg bg-surface p-2 text-text-primary shadow-lg ring-1 ring-border">
                      <div className="border-b border-border/70 px-3 py-2.5">
                        <p className="break-words text-sm font-bold">
                          {isAdmin ? 'Quản trị viên' : user.name}
                        </p>
                        <p className="mt-0.5 truncate text-xs text-text-secondary">{user.email}</p>
                      </div>
                      <Link
                        to="/profile"
                        onClick={closeMenus}
                        className="mt-1 flex items-center rounded-md min-h-11 px-3 py-2 text-sm text-text-secondary transition hover:bg-surface-low"
                      >
                        {isAdmin ? 'Hồ sơ quản trị' : 'Hồ sơ'}
                      </Link>
                      {!isAdmin ? (
                        <Link
                          to="/credit"
                          onClick={closeMenus}
                          className="flex items-center rounded-md min-h-11 px-3 py-2 text-sm text-text-secondary transition hover:bg-surface-low"
                        >
                          Ví Credit
                        </Link>
                      ) : null}
                      <Link
                        to="/change-password"
                        onClick={closeMenus}
                        className="flex items-center rounded-md min-h-11 px-3 py-2 text-sm text-text-secondary transition hover:bg-surface-low"
                      >
                        Đổi mật khẩu
                      </Link>
                      <button
                        onClick={() => {
                          logout();
                        }}
                        className="flex w-full items-center rounded-md min-h-11 px-3 py-2 text-sm text-error transition hover:bg-error-soft"
                      >
                        Đăng xuất
                      </button>
                    </div>
                  ) : null}
                </div>
              </>
            ) : (
              <>
                <Link
                  to="/login"
                  className="hidden min-h-11 items-center px-2 text-sm font-semibold text-white hover:text-white sm:inline-flex"
                >
                  Đăng nhập
                </Link>
                <Link to="/register">
                  <Button className="bronze-action">Đăng ký</Button>
                </Link>
              </>
            )}
            {!isAdmin ? (
              <IconButton
                icon={mobileOpen ? 'close' : 'menu'}
                label="Mở điều hướng"
                className="site-header-icon-button !size-11 shrink-0 xl:hidden"
                onClick={() => setMobileOpen(!mobileOpen)}
              />
            ) : null}
          </div>
        </div>
        {mobileOpen && !isAdmin ? (
          <nav
            className={`site-header-mobile border-t px-4 py-3 xl:hidden ${isHome ? 'site-header-home-mobile' : ''}`}
          >
            {nav.map(([to, label]) => (
              <NavLink
                end={to === '/'}
                key={to}
                to={to}
                onClick={closeMenus}
                className={({ isActive }) =>
                  `flex items-center justify-between rounded-md px-3 py-3 text-sm font-semibold focus-visible:bg-white/[0.14] focus-visible:outline-none ${
                    isActive
                      ? `${isHome ? 'bg-white/[0.16]' : 'bg-white/[0.14]'} text-white`
                      : isHome
                        ? 'text-white'
                        : 'text-white/80'
                  }`
                }
              >
                <span>{label}</span>
                {to === '/messages' && hasUnreadChat ? (
                  <span className="size-2 rounded-full bg-error" title="Có tin nhắn mới" />
                ) : null}
              </NavLink>
            ))}
            {user && !isAdmin ? (
              <Link
                to="/post"
                onClick={closeMenus}
                className="bronze-action mt-2 flex min-h-11 items-center justify-center rounded-md border px-4 py-3 text-sm transition"
              >
                Đăng đồ
              </Link>
            ) : null}
          </nav>
        ) : null}
      </header>
      <main id="main-content" className="flex-1">
        <Suspense fallback={<div className="min-h-[620px]" aria-hidden="true" />}>
          <Outlet />
        </Suspense>
      </main>
      {/* Quản trị viên và các trang tài khoản không hiện chân trang dành cho người dùng. */}
      {!isAdmin && !authPaths.has(pathname) ? (
        <footer className="site-footer mt-auto border-t">
          <div className="wide-shell pb-8 pt-12 sm:pt-14">
            <div className="grid gap-[clamp(1.5rem,3vw,2.5rem)] sm:grid-cols-2 lg:grid-cols-[1.4fr_1fr_1fr_1fr]">
              <div className="max-w-sm">
                <Link to="/" className="inline-flex items-center gap-2.5">
                  <span className="grid size-9 place-items-center rounded-md bg-primary text-white">
                    <ShareLoopLogo className="size-5" />
                  </span>
                  <strong className="site-footer-brand text-base font-extrabold">
                    SHARE<span>LOOP</span>
                  </strong>
                </Link>
                <p className="site-footer-copy mt-4 text-sm leading-6">
                  Trao món đồ cũ, mở một vòng đời mới cho cộng đồng.
                </p>
              </div>
              <FooterGroup
                title="Khám phá"
                onPostClick={!user ? () => navigate(loginPath('/post')) : undefined}
                links={[
                  ['Tìm đồ', '/browse'],
                  ['Trợ lý AI', '/ai'],
                  ['Đăng đồ', '/post'],
                ]}
              />
              <FooterGroup
                title="Tài khoản"
                links={[
                  ['Hồ sơ', '/profile'],
                  ['Ví Credit', '/credit'],
                  ['Hoạt động', '/activities'],
                ]}
              />
              <div>
                <h2 className="site-footer-title text-sm font-bold">Hỗ trợ</h2>
                <nav className="mt-3 space-y-1 text-sm leading-6">
                  <Link to="/about" className="site-footer-link block py-2 transition">
                    Giới thiệu ShareLoop
                  </Link>
                  <Link to="/guide" className="site-footer-link block py-2 transition">
                    Hướng dẫn sử dụng
                  </Link>
                  <Link to="/safety" className="site-footer-link block py-2 transition">
                    An toàn khi giao nhận
                  </Link>
                  <a
                    href="mailto:support@shareloop.vn"
                    className="site-footer-link block py-2 transition"
                  >
                    support@shareloop.vn
                  </a>
                </nav>
              </div>
            </div>
            <div className="site-footer-copy mt-12 flex flex-col gap-3 border-t border-border pt-6 text-xs sm:flex-row sm:items-center sm:justify-between">
              <p>© 2026 SHARELOOP</p>
            </div>
          </div>
        </footer>
      ) : null}
    </div>
  );
}

function FooterGroup({
  title,
  links,
  onPostClick,
}: {
  title: string;
  links: Array<[string, string]>;
  onPostClick?: () => void;
}) {
  return (
    <div>
      <h2 className="site-footer-title text-sm font-bold">{title}</h2>
      <nav className="mt-3 space-y-1">
        {links.map(([label, to]) =>
          onPostClick && to === '/post' ? (
            <button
              key={to}
              type="button"
              onClick={onPostClick}
              className="site-footer-link block py-2 text-left text-sm leading-6 transition"
            >
              {label}
            </button>
          ) : (
            <Link
              key={to}
              to={to}
              className="site-footer-link block py-2 text-sm leading-6 transition"
            >
              {label}
            </Link>
          ),
        )}
      </nav>
    </div>
  );
}
