// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { AppShell } from './AppShell';

const { currentUser, dispatchMock, logoutMock, requestApiMineMock } = vi.hoisted(() => ({
  currentUser: {
    id: 'local-user',
    name: 'Test User',
    email: 'test@example.com',
    role: 'user',
  },
  dispatchMock: vi.fn(),
  logoutMock: vi.fn().mockResolvedValue(undefined),
  requestApiMineMock: vi.fn().mockResolvedValue([]),
}));

vi.mock('../app/hooks', () => ({
  useAppDispatch: () => dispatchMock,
  useAppSelector: () => currentUser,
}));

vi.mock('../services/authApi', () => ({
  authApi: { logout: logoutMock },
}));

vi.mock('../services/requestApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../services/requestApi')>();
  return {
    ...actual,
    requestApi: {
      ...actual.requestApi,
      mine: requestApiMineMock,
    },
  };
});

vi.mock('../components/NotificationMenu', () => ({
  NotificationMenu: ({
    open,
    onOpen,
    onOpenChange,
  }: {
    open: boolean;
    onOpen: () => void;
    onOpenChange: (open: boolean) => void;
  }) => (
    <div>
      <button
        type="button"
        aria-expanded={open}
        onClick={() => {
          const nextOpen = !open;
          onOpenChange(nextOpen);
          if (nextOpen) onOpen();
        }}
      >
        Notifications
      </button>
      {open ? (
        <button type="button">
          Notification item
        </button>
      ) : null}
    </div>
  ),
}));

function Page() {
  const navigate = useNavigate();
  const location = useLocation();
  return (
    <div>
      <button type="button" onClick={() => navigate('/next')}>
        Change route
      </button>
      <span>Page content</span>
      <span data-testid="pathname">{location.pathname}</span>
    </div>
  );
}

function renderShell(width: number) {
  Object.defineProperty(window, 'innerWidth', { configurable: true, value: width });
  window.scrollTo = vi.fn();
  return render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route element={<AppShell />}>
          <Route path="*" element={<Page />} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe.each([
  ['desktop', 1280],
  ['mobile', 375],
])('AppShell header dropdowns on %s', (_viewport, width) => {
  it('closes dropdowns on route changes, outside clicks, Escape, and menu item clicks', async () => {
    const user = userEvent.setup();
    renderShell(width);

    const postLink = within(screen.getByRole('banner')).getByRole('link', { name: 'Đăng đồ' });
    expect(postLink.getAttribute('href')).toBe('/post');
    expect(postLink.querySelector('svg')).toBeNull();

    const profileButton = screen.getByRole('button', { name: 'Test User' });
    const notificationButton = screen.getByRole('button', { name: 'Notifications' });

    await user.click(profileButton);
    expect(screen.getByText('test@example.com')).toBeTruthy();
    await user.click(notificationButton);
    expect(screen.queryByText('test@example.com')).toBeNull();
    expect(screen.getByRole('button', { name: 'Notification item' })).toBeTruthy();

    await user.click(profileButton);
    expect(screen.queryByRole('button', { name: 'Notification item' })).toBeNull();
    fireEvent.pointerDown(screen.getByText('Page content'));
    expect(screen.queryByText('test@example.com')).toBeNull();

    await user.click(notificationButton);
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(screen.queryByRole('button', { name: 'Notification item' })).toBeNull();

    await user.click(profileButton);
    const creditLinks = screen.getAllByRole('link', { name: /Credit/i });
    await user.click(creditLinks[creditLinks.length - 1]);
    expect(screen.queryByText('test@example.com')).toBeNull();

    await user.click(notificationButton);
    await user.click(screen.getByRole('button', { name: 'Notification item' }));
    expect(screen.queryByRole('button', { name: 'Notification item' })).toBeNull();

    await user.click(profileButton);
    await user.click(screen.getByRole('button', { name: 'Change route' }));
    expect(screen.queryByText('test@example.com')).toBeNull();
  });

  it('runs profile actions before closing the menu and logs out', async () => {
    const user = userEvent.setup();
    renderShell(width);
    const profileButton = screen.getByRole('button', { name: 'Test User' });

    for (const path of ['/profile', '/credit', '/change-password']) {
      await user.click(profileButton);
      const menu = screen.getByText('test@example.com').parentElement?.parentElement;
      const link = menu?.querySelector<HTMLAnchorElement>(`a[href="${path}"]`);
      expect(link).toBeTruthy();
      await user.click(link!);
      expect(screen.getByTestId('pathname').textContent).toBe(path);
      expect(screen.queryByText('test@example.com')).toBeNull();
    }

    await user.click(profileButton);
    const menu = screen.getByText('test@example.com').parentElement?.parentElement;
    await user.click(within(menu!).getByRole('button'));

    expect(logoutMock).toHaveBeenCalledOnce();
    expect(dispatchMock).toHaveBeenCalledOnce();
    expect(screen.getByTestId('pathname').textContent).toBe('/login');
    expect(screen.queryByText('test@example.com')).toBeNull();
  });
});

describe('AppShell unread chat polling visibility', () => {
  it('pauses polling when document is hidden and fetches immediately when visible', async () => {
    vi.useFakeTimers();
    currentUser.id = 'api_1';
    requestApiMineMock.mockClear();

    let visibilityState = 'visible';
    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      get: () => visibilityState,
    });

    renderShell(1280);

    // Initial check fired on mount
    expect(requestApiMineMock).toHaveBeenCalledTimes(1);

    // When tab becomes hidden, interval skips fetching
    visibilityState = 'hidden';
    await vi.advanceTimersByTimeAsync(30_000);
    expect(requestApiMineMock).toHaveBeenCalledTimes(1);

    // When tab becomes visible again, visibilitychange triggers immediate fetch
    visibilityState = 'visible';
    fireEvent(document, new Event('visibilitychange'));
    expect(requestApiMineMock).toHaveBeenCalledTimes(2);

    // Next interval after visible works
    await vi.advanceTimersByTimeAsync(30_000);
    expect(requestApiMineMock).toHaveBeenCalledTimes(3);

    vi.useRealTimers();
    currentUser.id = 'local-user';
  });
});

