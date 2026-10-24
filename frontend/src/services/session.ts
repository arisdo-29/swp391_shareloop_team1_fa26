import type { AppDispatch } from '../app/store';
import { actions } from '../app/store';
import { userApi } from './userApi';
import { tokenStorage } from './http';
import { catalogApi } from './catalogApi';
import { toAreaOptions, toFeUser } from './mappers';

export async function fetchSessionUser() {
  const profile = await userApi.getMe();
  if (profile.isAdmin || profile.areaId === null) return toFeUser(profile);

  const areas = await catalogApi.areas();
  const district = toAreaOptions(areas).find((area) => area.id === profile.areaId)?.label ?? '';
  return toFeUser(profile, district);
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
    dispatch(actions.loginFromApi(await fetchSessionUser()));
  } catch {
    dispatch(actions.logout()); // token het han: interceptor da xoa token
  }
}
