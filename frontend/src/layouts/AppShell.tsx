import { Suspense, useEffect, useRef, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '../app/hooks';
import { actions, selectCurrentUser } from '../app/store';
import { Button, Icon, IconButton } from '../components/ui';
import { DialogFrame } from '../components/Dialog';
import { authApi } from '../services/authApi';
import { NotificationMenu } from '../components/NotificationMenu';
import { ShareLoopLogo } from '../components/ShareLoopLogo';
import { WalletProvider } from '../app/WalletContext';
import { useWallet } from '../app/useWallet';

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
  const { wallet } = useWallet();
  const isAdmin = user?.role === 'admin';
  const isHome = pathname === '/' && !isAdmin;
  const dispatch = useAppDispatch();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const [notificationOpen, setNotificationOpen] = useState(false);
  const [loginPromptOpen, setLoginPromptOpen] = useState(false);
  const headerActionsRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    setMobileOpen(false);
    setProfileOpen(false);
    setNotificationOpen(false);
  }, [location.hash, location.pathname, location.search]);

  useEffect(() => {
    // Esc đóng cả hộp thoại nhắc đăng nhập, giống Dialog dùng chung.
    const closeLoginPrompt = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setLoginPromptOpen(false);
    };
    document.addEventListener('keydown', closeLoginPrompt);
    return () => document.removeEventListener('keydown', closeLoginPrompt);
  }, []);

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
        // Tạm ẩn "Giao dịch" (/admin/transactions): backend chưa có API danh sách giao dịch cho Admin.
        ['/admin/finance', 'Thống kê'],
      ]
    : user
    ? [
        ['/', 'Trang chủ'],
        ['/browse', 'Tìm đồ'],
        ['/activities', 'Hoạt động'],
        ['/ai', 'Trợ lý AI'],
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
    <div className="flex min-h-screen flex-col">
      <ScrollToTop />
      <a
        href="#main-content"
        className="fixed left-3 top-3 z-50 -translate-y-20 rounded-md bg-primary px-4 py-2 text-sm font-semibold text-white focus:translate-y-0"
      >
        Bỏ qua điều hướng
      </a>
      <header
        className={`top-0 z-30 border-b backdrop-blur-md ${
          isHome
            ? 'absolute inset-x-0 border-white/15 bg-black/10 text-white'
            : 'sticky border-border/80 bg-white/95'
        }`}
      >
        <div className="mx-auto flex h-[68px] w-full max-w-[1280px] items-center gap-5 px-4 sm:px-6 lg:px-8">
          <Link to={isAdmin ? '/admin' : '/'} onClick={closeMenus} className="flex shrink-0 items-center gap-2.5">
            <span
              className={`grid size-9 place-items-center rounded-md text-white ${
                isHome ? 'bg-white/15 ring-1 ring-white/25' : 'bg-primary'
              }`}
            >
              <ShareLoopLogo className="size-5" />
            </span>
            <span
              className={`text-base font-extrabold ${isHome ? 'text-white' : 'text-text-primary'}`}
            >
              {isAdmin ? 'SHARELOOP ADMIN' : <>SHARE<span className={isHome ? 'text-primary-fixed' : 'text-primary'}>LOOP</span></>}
            </span>
          </Link>
          {!isAdmin ? (
            <nav className="hidden min-w-0 flex-1 items-center gap-0.5 lg:flex">
              {nav.map(([to, label]) => (
                <NavLink
                  end={to === '/'}
                  key={to}
                  to={to}
                  className={({ isActive }) =>
                    `inline-flex min-h-11 items-center whitespace-nowrap rounded-md px-3 py-2 text-sm font-medium transition ${
                      isHome
                        ? isActive
                          ? 'bg-white/15 text-white'
                          : 'text-white/80 hover:bg-white/10 hover:text-white'
                        : isActive
                          ? 'bg-primary-faint text-primary'
                          : 'text-text-secondary hover:bg-surface-low hover:text-text-primary'
                    }`
                  }
                >
                  {label}
                </NavLink>
              ))}
            </nav>
          ) : null}
          <div
            ref={headerActionsRef}
            className="ml-auto flex shrink-0 items-center gap-2"
            onClick={(event) => {
              const action = (event.target as HTMLElement).closest('a, button');
              if (action && !action.hasAttribute('aria-expanded')) {
                setProfileOpen(false);
                setNotificationOpen(false);
              }
            }}
          >
            {user ? (
              <>
                {!isAdmin ? <>
                <Link
                  to="/credit"
                  aria-label={`Ví Credit: ${wallet ? wallet.available : 'chưa tải'} Credit khả dụng`}
                  className={`hidden min-h-11 items-center rounded-md border px-3 py-2 text-xs font-semibold transition sm:flex ${
                    isHome
                      ? 'border-white/25 bg-black/10 text-white/90 hover:border-white/50 hover:bg-white/10 hover:text-white'
                      : 'border-border bg-white text-text-secondary hover:border-primary/30 hover:bg-primary-faint hover:text-primary'
                  }`}
                >
                  {wallet ? wallet.available : '--'} Credit
                </Link>
                <Link to="/post" className="hidden sm:block">
                  <Button>
                    Đăng đồ
                  </Button>
                </Link>
                </> : null}
                <NotificationMenu
                  userId={user.id}
                  isHome={isHome}
                  open={notificationOpen}
                  onOpenChange={setNotificationOpen}
                  onOpen={() => setProfileOpen(false)}
                />
                <div className="relative">
                  <button
                    onClick={() => {
                      setProfileOpen(!profileOpen);
                      setNotificationOpen(false);
                    }}
                    className={`flex min-h-11 items-center gap-1 rounded-md px-2 transition ${
                      isHome ? 'hover:bg-white/10' : 'hover:bg-surface-low'
                    }`}
                    aria-expanded={profileOpen}
                  >
                    <span className="inline-block max-w-[120px] truncate text-sm font-semibold">
                      {user.name}
                    </span>
                    <Icon
                      name="chevronDown"
                      className={`size-4 ${
                        isHome ? 'text-white/75' : 'text-text-muted'
                      }`}
                    />
                  </button>
                  {profileOpen ? (
                    <div className="absolute right-0 top-12 w-60 rounded-lg bg-white p-2 text-text-primary shadow-lg ring-1 ring-border">
                      <div className="border-b border-border/70 px-3 py-2.5">
                        <p className="truncate text-sm font-bold">{isAdmin ? 'Quản trị viên' : user.name}</p>
                        <p className="mt-0.5 truncate text-xs text-text-secondary">{user.email}</p>
                      </div>
                      <Link
                        to="/profile"
                        onClick={closeMenus}
                        className="mt-1 flex items-center rounded-md min-h-11 px-3 py-2 text-sm text-text-secondary transition hover:bg-surface-low"
                      >
                        {isAdmin ? 'Hồ sơ quản trị' : 'Hồ sơ'}
                      </Link>
                      {!isAdmin ? <Link
                        to="/credit"
                        onClick={closeMenus}
                        className="flex items-center rounded-md min-h-11 px-3 py-2 text-sm text-text-secondary transition hover:bg-surface-low"
                      >
                        Ví Credit
                      </Link> : null}
                      <Link to="/change-password" onClick={closeMenus} className="flex items-center rounded-md min-h-11 px-3 py-2 text-sm text-text-secondary transition hover:bg-surface-low">
                        Đổi mật khẩu
                      </Link>
                      {isAdmin ? (
                        <Link
                          to="/profile"
                          onClick={closeMenus}
                          className="flex items-center rounded-md min-h-11 px-3 py-2 text-sm text-text-secondary transition hover:bg-surface-low"
                        >
                          Chỉnh sửa hồ sơ
                        </Link>
                      ) : null}
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
                  className={`hidden min-h-11 items-center px-2 text-sm font-semibold sm:inline-flex ${
                    isHome
                      ? 'text-white/85 hover:text-white'
                      : 'text-text-secondary hover:text-primary'
                  }`}
                >
                  Đăng nhập
                </Link>
                <Link to="/register">
                  <Button>Đăng ký</Button>
                </Link>
              </>
            )}
            {!isAdmin ? (
              <IconButton
                icon={mobileOpen ? 'close' : 'menu'}
                label="Mở điều hướng"
                className={`!size-11 lg:hidden ${isHome ? '!border-white/30 !bg-white/10 !text-white hover:!bg-white/20 hover:!text-white' : ''}`}
                onClick={() => setMobileOpen(!mobileOpen)}
              />
            ) : null}
          </div>
        </div>
        {mobileOpen && !isAdmin ? (
          <nav
            className={`border-t px-4 py-3 lg:hidden ${
              isHome ? 'border-white/15 bg-primary-hover/95 text-white' : 'border-border bg-white'
            }`}
          >
            {nav.map(([to, label]) => (
              <NavLink
                end={to === '/'}
                key={to}
                to={to}
                onClick={closeMenus}
                className={({ isActive }) =>
                  `flex items-center rounded-md px-3 py-3 text-sm font-semibold ${
                    isHome
                      ? isActive
                        ? 'bg-white/15 text-white'
                        : 'text-white/80'
                      : isActive
                        ? 'bg-primary-faint text-primary'
                        : 'text-text-secondary'
                  }`
                }
              >
                {label}
              </NavLink>
            ))}
            {user && !isAdmin ? (
              <Link
                to="/post"
                onClick={closeMenus}
                className="mt-2 flex min-h-11 items-center justify-center rounded-md bg-primary px-4 py-3 text-sm font-semibold text-white transition hover:bg-primary-hover"
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
        <footer className="mt-auto border-t border-border bg-white">
          <div className="mx-auto w-full max-w-[1280px] px-4 pb-8 pt-12 sm:px-6 sm:pt-14 lg:px-8">
            <div className="grid gap-10 sm:grid-cols-2 lg:grid-cols-[1.4fr_1fr_1fr_1fr]">
              <div className="max-w-sm">
                <Link to="/" className="inline-flex items-center gap-2.5">
                  <span className="grid size-9 place-items-center rounded-md bg-primary text-white">
                    <ShareLoopLogo className="size-5" />
                  </span>
                  <strong className="text-base font-extrabold text-text-primary">
                    SHARE<span className="text-primary">LOOP</span>
                  </strong>
                </Link>
                <p className="mt-4 text-sm leading-6 text-text-secondary">
                  Trao món đồ cũ, mở một vòng đời mới cho cộng đồng.
                </p>
              </div>
              <FooterGroup
                title="Khám phá"
                onPostClick={!user ? () => setLoginPromptOpen(true) : undefined}
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
                <h2 className="text-sm font-bold text-text-primary">Hỗ trợ</h2>
                <nav className="mt-3 space-y-1 text-sm leading-6 text-text-secondary">
                  <Link to="/about" className="block py-2 transition hover:text-primary">
                    Giới thiệu ShareLoop
                  </Link>
                  <Link to="/about#cach-dung" className="block py-2 transition hover:text-primary">
                    Hướng dẫn sử dụng
                  </Link>
                  <Link to="/about#an-toan" className="block py-2 transition hover:text-primary">
                    An toàn khi giao nhận
                  </Link>
                  <a href="mailto:support@shareloop.vn" className="block py-2 transition hover:text-primary">
                    support@shareloop.vn
                  </a>
                </nav>
              </div>
            </div>
            <div className="mt-12 flex flex-col gap-3 border-t border-border pt-6 text-xs text-text-secondary sm:flex-row sm:items-center sm:justify-between">
              <p>© 2026 SHARELOOP</p>
            </div>
          </div>
        </footer>
      ) : null}
      {loginPromptOpen ? (
        <DialogFrame labelledBy="login-prompt-title" size="sm" className="text-text-primary">
          <h2 id="login-prompt-title" className="text-xl font-bold">Bạn cần đăng nhập để đăng đồ.</h2>
          <div className="mt-6 flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setLoginPromptOpen(false)}>
              Để sau
            </Button>
            <Link to="/login" onClick={() => setLoginPromptOpen(false)}>
              <Button>Đăng nhập</Button>
            </Link>
          </div>
        </DialogFrame>
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
      <h2 className="text-sm font-bold text-text-primary">{title}</h2>
      <nav className="mt-3 space-y-1">
        {links.map(([label, to]) =>
          onPostClick && to === '/post' ? (
            <button
              key={to}
              type="button"
              onClick={onPostClick}
              className="block py-2 text-left text-sm leading-6 text-text-secondary transition hover:text-primary"
            >
              {label}
            </button>
          ) : (
            <Link
              key={to}
              to={to}
              className="block py-2 text-sm leading-6 text-text-secondary transition hover:text-primary"
            >
              {label}
            </Link>
          ),
        )}
      </nav>
    </div>
  );
}
