// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { AppShell } from './AppShell';
import { markRequestChatSeen } from '../utils/request';

const {
  authState,
  dispatchMock,
  logoutMock,
  requestApiMineMock,
  requestApiForItemMock,
  itemApiMineMock,
  chatApiListMock,
} = vi.hoisted(() => ({
  authState: {
    currentUser: {
      id: 'local-user',
      name: 'Test User',
      email: 'test@example.com',
      role: 'user',
    } as { id: string; name: string; email: string; role: 'user' } | null,
  },
  dispatchMock: vi.fn(),
  logoutMock: vi.fn().mockResolvedValue(undefined),
  requestApiMineMock: vi.fn().mockResolvedValue([]),
  requestApiForItemMock: vi.fn().mockResolvedValue([]),
  itemApiMineMock: vi.fn().mockResolvedValue([]),
  chatApiListMock: vi.fn().mockResolvedValue([]),
}));

vi.mock('../app/hooks', () => ({
  useAppDispatch: () => dispatchMock,
  useAppSelector: () => authState.currentUser,
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
      forItem: requestApiForItemMock,
    },
  };
});

vi.mock('../services/itemApi', () => ({
  itemApi: { mine: itemApiMineMock },
}));

vi.mock('../services/chatApi', () => ({
  chatApi: { list: chatApiListMock },
}));

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

function requestWithLastMessage(id = 1) {
  return {
    id,
    itemId: 45,
    receiverId: 1,
    type: 'GIVE' as const,
    status: 'AWAITING_HANDOVER' as const,
    createdAt: '2026-11-12T08:00:00Z',
    lastMessageAt: '2026-11-12T09:00:00Z',
  };
}

function messageFrom(senderId: number, requestId = 1) {
  return {
    id: 10,
    requestId,
    senderId,
    kind: 'TEXT',
    content: 'Tin nhắn mới',
    mediaId: null,
    refItemId: null,
    itemCard: null,
    offerStatus: null,
    moderation: 'PASS',
    blockedReason: null,
    createdAt: '2026-11-12T09:00:00Z',
  };
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  localStorage.clear();
  authState.currentUser = {
    id: 'local-user',
    name: 'Test User',
    email: 'test@example.com',
    role: 'user',
  };
  requestApiMineMock.mockResolvedValue([]);
  requestApiForItemMock.mockResolvedValue([]);
  itemApiMineMock.mockResolvedValue([]);
  chatApiListMock.mockResolvedValue([]);
});

describe.each([
  ['desktop', 1280],
  ['mobile', 375],
])('AppShell header dropdowns on %s', (_viewport, width) => {
  it('shows the about link after the AI assistant for signed-in users', () => {
    renderShell(width);

    const header = screen.getByRole('banner');
    const headerLinks = within(header).getAllByRole('link');
    const aiIndex = headerLinks.findIndex((link) => link.textContent === 'Trợ lý AI');
    const aboutIndex = headerLinks.findIndex((link) => link.textContent === 'Giới thiệu');

    expect(aiIndex).toBeGreaterThanOrEqual(0);
    expect(aboutIndex).toBe(aiIndex + 1);
    expect(headerLinks[aboutIndex].getAttribute('href')).toBe('/about');
  });

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

describe.each([
  ['desktop', 1280],
  ['mobile', 375],
])('AppShell unread message dot on %s', (_viewport, width) => {
  it('hiện chấm đỏ 8px sát chữ Tin nhắn khi tin cuối do người khác gửi', async () => {
    authState.currentUser!.id = 'api_1';
    requestApiMineMock.mockResolvedValue([requestWithLastMessage()]);
    chatApiListMock.mockResolvedValue([messageFrom(2)]);
    renderShell(width);

    if (width < 1280) {
      const user = userEvent.setup();
      await user.click(screen.getByRole('button', { name: 'Mở điều hướng' }));
    }
    const messageLinks = within(screen.getByRole('banner')).getAllByRole('link', {
      name: 'Tin nhắn',
    });
    const messageLink = width < 1280 ? messageLinks[messageLinks.length - 1] : messageLinks[0];

    await waitFor(() => expect(messageLink.querySelector('[data-message-unread-dot]')).toBeTruthy());
    const dot = messageLink.querySelector('[data-message-unread-dot]')!;
    expect(dot.classList.contains('size-2')).toBe(true);
    expect(dot.classList.contains('bg-message-unread')).toBe(true);
  });
});

describe('AppShell unread message state', () => {
  it('không tính tin cuối do chính người dùng gửi', async () => {
    authState.currentUser!.id = 'api_1';
    requestApiMineMock.mockResolvedValue([requestWithLastMessage()]);
    chatApiListMock.mockResolvedValue([messageFrom(1)]);

    renderShell(1280);

    await waitFor(() => expect(chatApiListMock).toHaveBeenCalledWith(1));
    expect(document.querySelector('[data-message-unread-dot]')).toBeNull();
  });

  it('tự ẩn chấm khi hội thoại được đánh dấu đã xem', async () => {
    authState.currentUser!.id = 'api_1';
    requestApiMineMock.mockResolvedValue([requestWithLastMessage()]);
    chatApiListMock.mockResolvedValue([messageFrom(2)]);
    renderShell(1280);

    await waitFor(() => expect(document.querySelector('[data-message-unread-dot]')).toBeTruthy());
    markRequestChatSeen(1);
    await waitFor(() => expect(document.querySelector('[data-message-unread-dot]')).toBeNull());
  });

  it('tính cả tin nhắn nhận trên bài do người dùng sở hữu', async () => {
    authState.currentUser!.id = 'api_1';
    itemApiMineMock.mockResolvedValue([{ id: 45 }]);
    requestApiForItemMock.mockResolvedValue([requestWithLastMessage()]);
    chatApiListMock.mockResolvedValue([messageFrom(2)]);

    renderShell(1280);

    await waitFor(() => expect(document.querySelector('[data-message-unread-dot]')).toBeTruthy());
    expect(requestApiForItemMock).toHaveBeenCalledWith(45, 1);
  });

  it('không hiển thị hoặc tải unread khi chưa đăng nhập', () => {
    authState.currentUser = null;

    renderShell(1280);

    expect(screen.queryByRole('link', { name: 'Tin nhắn' })).toBeNull();
    expect(document.querySelector('[data-message-unread-dot]')).toBeNull();
    expect(requestApiMineMock).not.toHaveBeenCalled();
  });
});

describe('AppShell footer support links', () => {
  it('trỏ tới ba trang hỗ trợ và địa chỉ email', () => {
    renderShell(1280);

    expect(screen.getByRole('link', { name: 'Giới thiệu ShareLoop' }).getAttribute('href')).toBe('/about');
    expect(screen.getByRole('link', { name: 'Hướng dẫn sử dụng' }).getAttribute('href')).toBe('/guide');
    expect(screen.getByRole('link', { name: 'An toàn khi giao nhận' }).getAttribute('href')).toBe('/safety');
    expect(screen.getByRole('link', { name: 'support@shareloop.vn' }).getAttribute('href'))
      .toBe('mailto:support@shareloop.vn');
  });
});

describe('AppShell unread chat polling visibility', () => {
  it('pauses polling when document is hidden and fetches immediately when visible', async () => {
    vi.useFakeTimers();
    authState.currentUser!.id = 'api_1';
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
    authState.currentUser!.id = 'local-user';
  });
});

