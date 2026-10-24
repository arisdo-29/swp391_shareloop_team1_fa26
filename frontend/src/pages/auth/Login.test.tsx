// @vitest-environment jsdom

import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { User } from '../../types/domain';
import { authApi } from '../../services/authApi';
import { fetchSessionUser } from '../../services/session';
import { preloadRoute } from '../../app/routePreload';
import { Login } from './Login';

const mocks = vi.hoisted(() => ({ dispatch: vi.fn() }));

vi.mock('../../app/hooks', () => ({
  useAppDispatch: () => mocks.dispatch,
}));

vi.mock('../../app/store', () => ({
  actions: { loginFromApi: (user: User) => ({ type: 'data/loginFromApi', payload: user }) },
}));

vi.mock('../../services/authApi', () => ({
  authApi: { login: vi.fn() },
}));

vi.mock('../../services/session', () => ({
  fetchSessionUser: vi.fn(),
}));

vi.mock('../../app/routePreload', () => ({
  preloadRoute: vi.fn(),
}));

vi.mock('../../services/authErrors', () => ({
  authErrorMessage: () => 'Sai thông tin đăng nhập.',
}));

const admin: User = {
  id: 'api_7', username: 'admin@shareloop.vn', password: '', name: 'Quản trị viên',
  email: 'admin@shareloop.vn', phone: '0900000000', district: '', avatarInitials: 'QV',
  totalCredit: 0, availableCredit: 0, holdCredit: 0, rewardPoints: 0, reputationStars: 5,
  rank: 'Thành viên mới', status: 'active', role: 'admin', joinedAt: '2026-10-24T00:00:00Z', totalTx: 0,
};

function renderLogin() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/admin" element={<div>Admin destination</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.mocked(authApi.login).mockResolvedValue({
    accessToken: 'token', tokenType: 'Bearer', expiresIn: 3600,
    user: { id: 7, email: admin.email, fullName: admin.name, isAdmin: true },
  });
  vi.mocked(fetchSessionUser).mockResolvedValue(admin);
  vi.mocked(preloadRoute).mockResolvedValue(undefined);
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('đăng nhập Admin', () => {
  it('hiển thị logo ShareLoop ở phần giới thiệu và banner nhưng giữ icon môi trường', () => {
    const { container } = renderLogin();

    expect(container.querySelectorAll('.shareloop-logo img')).toHaveLength(2);
    const environmentCard = screen.getByRole('heading', { name: 'Bảo vệ môi trường' }).closest('article');
    expect(environmentCard?.querySelector('svg')).toBeTruthy();
    expect(environmentCard?.querySelector('.shareloop-logo img')).toBeNull();
  });

  it('xác thực, dispatch Redux rồi điều hướng tới Dashboard', async () => {
    const user = userEvent.setup();
    renderLogin();

    await user.type(screen.getByLabelText('Email'), admin.email);
    await user.type(screen.getByLabelText('Mật khẩu'), 'Secret123!');
    await user.click(screen.getByRole('button', { name: 'Đăng nhập' }));

    expect(await screen.findByText('Admin destination')).toBeTruthy();
    expect(authApi.login).toHaveBeenCalledWith({ email: admin.email, password: 'Secret123!' });
    expect(preloadRoute).toHaveBeenCalledWith('/admin');
    expect(fetchSessionUser).toHaveBeenCalledTimes(1);
    expect(mocks.dispatch).toHaveBeenCalledWith({ type: 'data/loginFromApi', payload: admin });
    expect(vi.mocked(authApi.login).mock.invocationCallOrder[0]).toBeLessThan(
      vi.mocked(fetchSessionUser).mock.invocationCallOrder[0],
    );
    expect(vi.mocked(fetchSessionUser).mock.invocationCallOrder[0]).toBeLessThan(
      mocks.dispatch.mock.invocationCallOrder[0],
    );
  });

  it('giữ nguyên màn đăng nhập và kết thúc processing khi xác thực thất bại', async () => {
    vi.mocked(authApi.login).mockRejectedValue({ code: 'AUTH_INVALID_CREDENTIALS', status: 401 });
    const user = userEvent.setup();
    renderLogin();

    await user.type(screen.getByLabelText('Email'), admin.email);
    await user.type(screen.getByLabelText('Mật khẩu'), 'wrong-password');
    await user.click(screen.getByRole('button', { name: 'Đăng nhập' }));

    expect(await screen.findByText('Sai thông tin đăng nhập.')).toBeTruthy();
    await waitFor(() => expect((screen.getByRole('button', { name: 'Đăng nhập' }) as HTMLButtonElement).disabled).toBe(false));
    expect(fetchSessionUser).not.toHaveBeenCalled();
    expect(mocks.dispatch).not.toHaveBeenCalled();
  });
});
