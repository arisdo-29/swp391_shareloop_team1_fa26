import { type FormEvent, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAppDispatch } from '../../app/hooks';
import { actions } from '../../app/store';
import { Alert, Button, Field, Icon } from '../../components/ui';
import { PasswordField } from '../../components/PasswordField';
import { authApi } from '../../services/authApi';
import { authErrorMessage } from '../../services/authErrors';
import type { ApiError } from '../../services/http';
import { toFeUser } from '../../services/mappers';

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
    const normalized = inputEmail.trim().toLowerCase();
    setError('');
    if (!normalized) {
      setError('Vui lòng nhập email.');
      return;
    }
    if (!pass) {
      setError('Vui lòng nhập mật khẩu.');
      return;
    }
    setProcessing(true);
    try {
      const result = await authApi.login(normalized, pass);
      const user = toFeUser(result.user);
      dispatch(actions.loginFromApi(user));
      navigate(user.role === 'admin' ? '/admin' : '/');
    } catch (apiError) {
      setError(authErrorMessage(apiError as ApiError));
    } finally {
      setProcessing(false);
    }
  };

  const submit = (event: FormEvent) => {
    event.preventDefault();
    void login(email, password);
  };

  return (
    <div className="page-shell grid min-h-[620px] items-center gap-10 lg:grid-cols-2">
      <section className="hidden max-w-lg lg:block">
        <span className="grid size-12 place-items-center rounded-lg bg-primary text-white">
          <Icon name="renew" className="size-6" weight="bold" />
        </span>
        <h1 className="mt-6 text-4xl font-bold leading-tight">Chào mừng bạn quay lại cộng đồng.</h1>
        <p className="mt-4 text-base leading-7 text-text-muted">
          Tiếp tục theo dõi món đã đăng, trò chuyện và hoàn tất các giao dịch an toàn.
        </p>
      </section>
      <form
        onSubmit={submit}
        className="mx-auto w-full max-w-md rounded-xl bg-white p-6 shadow-md ring-1 ring-border/70 sm:p-8"
      >
        <h1 className="text-2xl font-bold">Đăng nhập</h1>
        <p className="mt-2 text-sm text-text-muted">Dùng tài khoản của bạn để tiếp tục.</p>
        {error ? <div className="mt-5"><Alert tone="error">{error}</Alert></div> : null}
        {successNotice ? <div className="mt-5"><Alert tone="success">{successNotice}</Alert></div> : null}
        <div className="mt-6 space-y-4">
          <Field
            required
            label="Email"
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            autoComplete="email"
          />
          <PasswordField label="Mật khẩu" value={password} onChange={setPassword} autoComplete="current-password" />
          <Button className="w-full" disabled={processing}>
            {processing ? 'Đang đăng nhập...' : 'Đăng nhập'}
          </Button>
        </div>
        <Link to="/forgot-password" className="mt-4 block text-center text-sm font-semibold text-primary hover:underline">
          Quên mật khẩu?
        </Link>
        <p className="mt-8 text-center text-sm text-text-muted">
          Chưa có tài khoản?{' '}
          <Link to="/register" className="font-semibold text-primary hover:underline">
            Đăng ký
          </Link>
        </p>
      </form>
    </div>
  );
}
