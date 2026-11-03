import type { AppDispatch } from '../app/store';
import { actions } from '../app/store';
import { userApi } from './userApi';
import { tokenStorage } from './http';
import { catalogApi } from './catalogApi';
import { toAreaOptions, toFeUser } from './mappers';
import { reputationApi } from './reputationApi';

export async function fetchSessionUser() {
  const profile = await userApi.getMe();
  const [reputation, areas] = await Promise.all([
    reputationApi
      .getMyReputation(0, 1)
      .catch(() => ({ trustStars: 0, rank: 'BRONZE' as const })),
    profile.isAdmin || profile.areaId === null
      ? Promise.resolve([])
      : catalogApi.areas().catch(() => []),
  ]);
  const district = toAreaOptions(areas).find((area) => area.id === profile.areaId)?.label ?? '';
  return toFeUser(profile, district, reputation);
}

/** Goi khi mo app: con token va userId API thi hoi /me de khoi phuc phien dang nhap. */
export async function restoreSession(dispatch: AppDispatch, currentUserId: string | null) {
  if (!tokenStorage.get()) {
    if (currentUserId?.startsWith('api_')) dispatch(actions.logout());
    return;
  }
  const userId = tokenStorage.getUserId();
  if (!userId) {
    tokenStorage.clear();
    dispatch(actions.logout());
    return;
  }
  try {
    const user = await fetchSessionUser();
    // Một API phụ có thể lỗi 403 mà không phải lỗi xác thực. Chỉ khôi phục Redux nếu token vẫn còn.
    if (!tokenStorage.get()) {
      dispatch(actions.logout());
      return;
    }
    dispatch(actions.loginFromApi(user));
  } catch {
    // Interceptor chỉ xóa token khi JWT không hợp lệ/hết hạn hoặc tài khoản bị khóa/cấm.
    if (!tokenStorage.get()) dispatch(actions.logout());
  }
}
