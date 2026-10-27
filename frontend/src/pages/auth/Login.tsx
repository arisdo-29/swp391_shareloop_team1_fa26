import { type FormEvent, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAppDispatch } from '../../app/hooks';
import { preloadRoute } from '../../app/routePreload';
import { actions } from '../../app/store';
import { PasswordField } from '../../components/PasswordField';
import { Alert, Button, Field } from '../../components/ui';
import { authApi } from '../../services/authApi';
import { authErrorMessage } from '../../services/authErrors';
import type { ApiError } from '../../services/http';
import { fetchSessionUser } from '../../services/session';
import { validEmail } from '../../utils/validation';
import { AuthLayout } from './AuthLayout';

export function Login() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [processing, setProcessing] = useState(false);
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const location = useLocation();
  const successNotice = (location.state as { notice?: string } | null)?.notice;

  const login = async (inputEmail: string, pass: string) => {
    if (processing) return;
    const normalized = inputEmail.trim().toLowerCase();
    setError('');
    if (!normalized) {
      setError('Vui lòng nhập email.');
      return;
    }
    if (!validEmail(normalized)) {
      setError('Email không đúng định dạng.');
      return;
    }
    if (!pass) {
      setError('Vui lòng nhập mật khẩu.');
      return;
    }
    setProcessing(true);
    try {
      const authentication = await authApi.login({ email: normalized, password: pass });
      void preloadRoute(authentication.user.isAdmin ? '/admin' : '/').catch(() => undefined);
      const user = await fetchSessionUser();
      dispatch(actions.loginFromApi(user));
      navigate(user.role === 'admin' ? '/admin' : '/');
    } catch (apiError) {
      const authError = apiError as ApiError;
      if (authError.code === 'AUTH_EMAIL_NOT_VERIFIED') {
        navigate('/verify-email', { state: { email: normalized } });
        return;
      }
      setError(authErrorMessage(authError));
    } finally {
      setProcessing(false);
    }
  };

  const submit = (event: FormEvent) => {
    event.preventDefault();
    void login(email, password);
  };

  return (
    <AuthLayout showTabs>
      <form onSubmit={submit} aria-busy={processing} aria-labelledby="login-title" className="space-y-4">
        <h2 id="login-title" className="text-2xl font-bold">
          Chào bạn quay lại
        </h2>
        {error ? <Alert tone="error">{error}</Alert> : null}
        {successNotice ? <Alert tone="success">{successNotice}</Alert> : null}
        <Field
          required
          label="Email"
          type="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          autoComplete="email"
        />
        <PasswordField
          label="Mật khẩu"
          value={password}
          onChange={setPassword}
          autoComplete="current-password"
        />
        <div className="flex justify-end">
          <Link
            to="/forgot-password"
            className="inline-flex min-h-11 items-center text-sm font-semibold text-primary hover:text-primary-hover"
          >
            Quên mật khẩu?
          </Link>
        </div>
        <Button size="lg" className="w-full" disabled={processing}>
          {processing ? 'Đang đăng nhập...' : 'Đăng nhập'}
        </Button>
      </form>
    </AuthLayout>
  );
}
