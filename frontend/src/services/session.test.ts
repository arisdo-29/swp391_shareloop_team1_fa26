// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { catalogApi } from './catalogApi';
import { tokenStorage } from './http';
import { fetchSessionUser, restoreSession } from './session';
import { userApi, type MeResponse } from './userApi';

vi.mock('./catalogApi', () => ({
  catalogApi: { areas: vi.fn() },
}));

vi.mock('./userApi', () => ({
  userApi: { getMe: vi.fn() },
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

beforeEach(() => {
  vi.mocked(userApi.getMe).mockResolvedValue(adminProfile);
  vi.mocked(catalogApi.areas).mockResolvedValue([]);
});

afterEach(() => {
  vi.restoreAllMocks();
  vi.clearAllMocks();
});

describe('khôi phục phiên Admin', () => {
  it('xác thực bằng /me nhưng không chờ hoặc gọi /areas cho Admin', async () => {
    const user = await fetchSessionUser();

    expect(userApi.getMe).toHaveBeenCalledTimes(1);
    expect(catalogApi.areas).not.toHaveBeenCalled();
    expect(user).toEqual(expect.objectContaining({ id: 'api_7', role: 'admin', district: '' }));
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
