// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { User } from '../types/domain';
import { AdminRoute } from './Guards';

const mocks = vi.hoisted(() => ({
  currentUserId: null as string | null,
  users: [] as User[],
  dispatch: vi.fn(),
}));

vi.mock('../app/hooks', () => ({
  useAppDispatch: () => mocks.dispatch,
  useAppSelector: (selector: (state: { data: { currentUserId: string | null; users: User[] } }) => unknown) =>
    selector({ data: { currentUserId: mocks.currentUserId, users: mocks.users } }),
}));

const admin: User = {
  id: 'api_7', username: 'admin@shareloop.vn', password: '', name: 'Quản trị viên',
  email: 'admin@shareloop.vn', phone: '0900000000', district: '', avatarInitials: 'QV',
  totalCredit: 0, availableCredit: 0, holdCredit: 0, rewardPoints: 0, reputationStars: 5,
  rank: 'Thành viên mới', status: 'active', role: 'admin', joinedAt: '2026-10-24T00:00:00Z', totalTx: 0,
};

function renderAdminRoute() {
  return render(
    <MemoryRouter initialEntries={['/admin']}>
      <Routes>
        <Route element={<AdminRoute />}>
          <Route path="/admin" element={<div>Admin dashboard</div>} />
        </Route>
        <Route path="/login" element={<div>Login page</div>} />
        <Route path="/" element={<div>Public home</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

afterEach(() => {
  cleanup();
  mocks.currentUserId = null;
  mocks.users = [];
  mocks.dispatch.mockClear();
});

describe('phân quyền AdminRoute', () => {
  it('cho Admin đang hoạt động mở Dashboard', () => {
    mocks.currentUserId = admin.id;
    mocks.users = [admin];

    renderAdminRoute();

    expect(screen.getByText('Admin dashboard')).toBeTruthy();
  });

  it('chuyển khách tới đăng nhập và người dùng thường về trang chủ', () => {
    const guestView = renderAdminRoute();
    expect(screen.getByText('Login page')).toBeTruthy();
    guestView.unmount();

    mocks.currentUserId = 'api_8';
    mocks.users = [{ ...admin, id: 'api_8', role: 'user' }];
    renderAdminRoute();
    expect(screen.getByText('Public home')).toBeTruthy();
  });
});
