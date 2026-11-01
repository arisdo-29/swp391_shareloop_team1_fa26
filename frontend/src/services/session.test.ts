// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { catalogApi } from './catalogApi';
import { tokenStorage } from './http';
import { fetchSessionUser, restoreSession } from './session';
import { userApi, type MeResponse } from './userApi';
import { reputationApi } from './reputationApi';

vi.mock('./catalogApi', () => ({
  catalogApi: { areas: vi.fn() },
}));

vi.mock('./userApi', () => ({
  userApi: { getMe: vi.fn() },
}));

vi.mock('./reputationApi', () => ({
  reputationApi: { getMyReputation: vi.fn() },
}));

const adminProfile: MeResponse = {
  id: 7,
  email: 'admin@shareloop.vn',
  fullName: 'Quản trị viên',
  phone: '0900000000',
  isAdmin: true,
  areaId: 12,
  avatarMediaId: null,
  avatarUrl: null,
};

const memberProfile: MeResponse = {
  ...adminProfile,
  id: 42,
  email: 'member@shareloop.vn',
  fullName: 'Thành viên mới',
  isAdmin: false,
  areaId: null,
};

beforeEach(() => {
  vi.mocked(userApi.getMe).mockResolvedValue(adminProfile);
  vi.mocked(catalogApi.areas).mockResolvedValue([]);
  vi.mocked(reputationApi.getMyReputation).mockResolvedValue({
    totalPoints: 0,
    rank: 'BRONZE',
    trustStars: 5,
    accuracyRate: null,
    history: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 1 },
  });
});

afterEach(() => {
  vi.restoreAllMocks();
  vi.clearAllMocks();
});

describe('khôi phục phiên Admin', () => {
  it('xác thực bằng /me nhưng không chờ hoặc gọi /areas cho Admin', async () => {
    const user = await fetchSessionUser();

    expect(userApi.getMe).toHaveBeenCalledTimes(1);
    expect(reputationApi.getMyReputation).toHaveBeenCalledWith(0, 1);
    expect(catalogApi.areas).not.toHaveBeenCalled();
    expect(user).toEqual(expect.objectContaining({
      id: 'api_7',
      role: 'admin',
      district: '',
      status: 'active',
      reputationStars: 5,
      rank: 'BRONZE',
    }));
  });

  it('dùng đúng sao và hạng backend trả về cho thành viên vừa xác thực', async () => {
    vi.mocked(userApi.getMe).mockResolvedValue(memberProfile);

    const user = await fetchSessionUser();

    expect(user).toEqual(expect.objectContaining({
      id: 'api_42',
      role: 'user',
      status: 'active',
      reputationStars: 5,
      rank: 'BRONZE',
    }));
    expect(reputationApi.getMyReputation).toHaveBeenCalledWith(0, 1);
  });

  it('dispatch Admin vào Redux sau khi /me thành công', async () => {
    vi.spyOn(tokenStorage, 'get').mockReturnValue('valid-token');
    vi.spyOn(tokenStorage, 'getUserId').mockReturnValue(7);
    const dispatch = vi.fn();

    await restoreSession(dispatch, null);

    expect(dispatch).toHaveBeenCalledWith(expect.objectContaining({
      type: 'data/loginFromApi',
      payload: expect.objectContaining({ id: 'api_7', role: 'admin' }),
    }));
    expect(userApi.getMe).toHaveBeenCalledTimes(1);
    expect(catalogApi.areas).not.toHaveBeenCalled();
  });
});
