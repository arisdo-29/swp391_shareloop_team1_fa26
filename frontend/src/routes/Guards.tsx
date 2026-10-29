import { useEffect } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '../app/hooks';
import { actions, selectCurrentUser } from '../app/store';
import { loginPath, safeInternalNextPath } from '../utils/authRedirect';

function useValidSession() {
  const dispatch = useAppDispatch();
  const user = useAppSelector(selectCurrentUser);
  // Tài khoản không còn hoạt động (bị khoá, tạm ngưng) thì đăng xuất khỏi phiên.
  const invalid = Boolean(user && user.status !== 'active');
  useEffect(() => {
    if (invalid) dispatch(actions.logout());
  }, [dispatch, invalid]);
  return invalid ? null : user;
}

const isAdminUser = (user: ReturnType<typeof useValidSession>) => user?.role === 'admin';

export function PublicRoute() {
  const user = useValidSession();
  return isAdminUser(user) ? <Navigate to="/admin" replace /> : <Outlet />;
}

export function GuestOnlyRoute() {
  const user = useValidSession();
  const location = useLocation();
  const nextPath = safeInternalNextPath(new URLSearchParams(location.search).get('next'));
  return user ? <Navigate to={nextPath ?? (isAdminUser(user) ? '/admin' : '/')} replace /> : <Outlet />;
}

export function ProtectedRoute() {
  const user = useValidSession();
  const location = useLocation();
  if (isAdminUser(user)) return <Navigate to="/admin" replace />;
  return user?.status === 'active'
    ? <Outlet />
    : <Navigate to={loginPath(`${location.pathname}${location.search}${location.hash}`)} replace />;
}

export function ProfileRoute() {
  const user = useValidSession();
  const location = useLocation();
  return user?.status === 'active'
    ? <Outlet />
    : <Navigate to={loginPath(`${location.pathname}${location.search}${location.hash}`)} replace />;
}

export function AdminRoute() {
  const user = useValidSession();
  const location = useLocation();
  if (!user) return <Navigate to={loginPath(`${location.pathname}${location.search}${location.hash}`)} replace />;
  if (user.status !== 'active' || user.role !== 'admin') return <Navigate to="/" replace />;
  return <Outlet />;
}
