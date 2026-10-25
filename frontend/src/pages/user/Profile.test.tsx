// @vitest-environment jsdom

import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { User } from '../../types/domain';
import { reputationApi, type MyReputation } from '../../services/reputationApi';
import { userApi } from '../../services/userApi';
import { catalogApi } from '../../services/catalogApi';
import { Profile } from './Profile';

const mocks = vi.hoisted(() => ({ dispatch: vi.fn(), user: null as User | null }));

vi.mock('../../app/hooks', () => ({
  useAppDispatch: () => mocks.dispatch,
  useAppSelector: () => mocks.user,
}));

vi.mock('../../app/store', () => ({
  actions: { updateProfile: (payload: unknown) => ({ type: 'data/updateProfile', payload }) },
  selectCurrentUser: () => mocks.user,
}));

vi.mock('../../services/userApi', () => ({
  userApi: { getMe: vi.fn(), updateMe: vi.fn(), sendChangePhoneOtp: vi.fn(), changePhone: vi.fn() },
}));

vi.mock('../../services/catalogApi', () => ({
  catalogApi: { areas: vi.fn() },
}));

vi.mock('../../services/reputationApi', () => ({
  reputationApi: { getMyReputation: vi.fn() },
}));

function buildUser(role: User['role']): User {
  return {
    id: 'api_7', username: 'an@shareloop.vn', password: '', name: 'Nguyễn An',
    email: 'an@shareloop.vn', phone: '0900000000', district: '', avatarInitials: 'NA',
    totalCredit: 0, availableCredit: 0, holdCredit: 0, rewardPoints: 0, reputationStars: 5,
    rank: 'Thành viên mới', status: 'active', role, joinedAt: '2026-10-24T00:00:00Z', totalTx: 0,
  };
}

function buildReputation(overrides: Partial<MyReputation> = {}): MyReputation {
  return {
    totalPoints: 120,
    rank: 'GOLD',
    trustStars: 4,
    accuracyRate: 0.92,
    history: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 },
    ...overrides,
  };
}

function renderProfile(url = '/profile') {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <Profile />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  mocks.user = buildUser('user');
  vi.mocked(userApi.getMe).mockResolvedValue({
    id: 7, email: 'an@shareloop.vn', fullName: 'Nguyễn An', phone: '0900000000',
    areaId: null, avatarMediaId: null, avatarUrl: null,
  } as Awaited<ReturnType<typeof userApi.getMe>>);
  vi.mocked(catalogApi.areas).mockResolvedValue([]);
  vi.mocked(reputationApi.getMyReputation).mockResolvedValue(buildReputation());
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('Profile', () => {
  it('hiển thị hạng tiếng Việt ở cột trái ngay khi mở trang, không cần mở tab Uy tín', async () => {
    // given
    // when
    renderProfile();

    // then
    expect(await screen.findByText('Hạng Vàng')).toBeTruthy();
    expect(screen.queryByText('Thành viên mới')).toBeNull();
  });

  it('mở thẳng tab Uy tín khi URL có tab=reputation', async () => {
    // given
    // when
    renderProfile('/profile?tab=reputation');

    // then
    expect(await screen.findByText('Lịch sử uy tín')).toBeTruthy();
    expect(reputationApi.getMyReputation).toHaveBeenCalledTimes(1);
  });

  it('về tab Hồ sơ khi tham số tab không hợp lệ', async () => {
    // given
    // when
    renderProfile('/profile?tab=credit');

    // then
    expect(await screen.findByText('Thông tin cá nhân')).toBeTruthy();
    expect(screen.queryByText('Lịch sử uy tín')).toBeNull();
  });

  it('đổi sang tab Uy tín khi bấm nút và dùng lại kết quả đã tải', async () => {
    // given
    renderProfile();
    await screen.findByText('Hạng Vàng');

    // when
    await userEvent.click(screen.getByRole('button', { name: /Uy tín/ }));

    // then
    expect(await screen.findByText('Lịch sử uy tín')).toBeTruthy();
    expect(reputationApi.getMyReputation).toHaveBeenCalledTimes(1);
  });

  it('hiện "Chưa đủ giao dịch để tính" khi tỉ lệ đúng mô tả là null', async () => {
    // given
    vi.mocked(reputationApi.getMyReputation).mockResolvedValue(buildReputation({ accuracyRate: null }));

    // when
    renderProfile('/profile?tab=reputation');

    // then
    expect(await screen.findByText('Chưa đủ giao dịch để tính')).toBeTruthy();
  });

  it('giữ nhãn quản trị hệ thống và không gọi API uy tín với admin', async () => {
    // given
    mocks.user = buildUser('admin');

    // when
    renderProfile();

    // then
    expect(await screen.findByText('Quản trị hệ thống')).toBeTruthy();
    await waitFor(() => expect(userApi.getMe).toHaveBeenCalled());
    expect(reputationApi.getMyReputation).not.toHaveBeenCalled();
  });
});
