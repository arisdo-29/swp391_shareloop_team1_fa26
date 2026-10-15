import type { AppDispatch } from '../app/store';
import { actions } from '../app/store';
import { userApi } from './userApi';
import { tokenStorage } from './http';
import { toFeUser } from './mappers';

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
    const user = await userApi.getMe();
    dispatch(actions.loginFromApi(toFeUser(user)));
  } catch {
    dispatch(actions.logout()); // token het han: interceptor da xoa token
  }
}
