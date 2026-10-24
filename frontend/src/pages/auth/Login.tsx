import { type FormEvent, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAppDispatch } from '../../app/hooks';
import { preloadRoute } from '../../app/routePreload';
import { actions } from '../../app/store';
import { PasswordField } from '../../components/PasswordField';
import { Alert, Button, Field, Icon } from '../../components/ui';
import { authApi } from '../../services/authApi';
import { authErrorMessage } from '../../services/authErrors';
import type { ApiError } from '../../services/http';
import { fetchSessionUser } from '../../services/session';
import { validEmail } from '../../utils/passwordSecurity';
import './login.css';

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
    <main className="login-page">
      <div className="login-page__shell">
        <section className="login-welcome" aria-labelledby="login-welcome-title">
          <div>
            <p className="login-welcome__eyebrow">
              <span className="login-welcome__eyebrow-icon" aria-hidden="true">
                <Icon name="renew" className="h-4 w-4" />
              </span>
              Cộng đồng sẻ chia bền vững
            </p>
            <h1 id="login-welcome-title" className="login-welcome__title">
              Chào mừng bạn quay lại <span>ShareLoop</span>
            </h1>
            <p className="login-welcome__description">
              Tiếp tục hành trình chia sẻ, tìm kiếm và lan tỏa những món đồ có giá trị đến nhiều người hơn.
            </p>
          </div>

          <div className="login-values" aria-label="Giá trị cộng đồng ShareLoop">
            <article className="login-value">
              <span className="login-value__icon" aria-hidden="true">
                <Icon name="payments" className="h-5 w-5" />
              </span>
              <div>
                <h2>Tiết kiệm chi phí</h2>
                <p>Trao đổi thông minh, tận dụng giá trị sẵn có.</p>
              </div>
            </article>
            <article className="login-value">
              <span className="login-value__icon" aria-hidden="true">
                <Icon name="group" className="h-5 w-5" />
              </span>
              <div>
                <h2>Kết nối cộng đồng</h2>
                <p>Gặp gỡ những người cùng tinh thần sẻ chia.</p>
              </div>
            </article>
            <article className="login-value">
              <span className="login-value__icon" aria-hidden="true">
                <Icon name="renew" className="h-5 w-5" />
              </span>
              <div>
                <h2>Bảo vệ môi trường</h2>
                <p>Kéo dài vòng đời đồ dùng, giảm bớt lãng phí.</p>
              </div>
            </article>
          </div>

          <div className="login-welcome__story" aria-hidden="true">
            <span className="login-welcome__story-mark">
              <Icon name="renew" className="h-7 w-7" />
            </span>
            <div>
              <p>Trao món đồ bạn không còn dùng, nhận lại một kết nối mới.</p>
              <span>Chia sẻ có trách nhiệm · Kết nối bền vững</span>
            </div>
          </div>
        </section>

        <section className="login-form-panel" aria-labelledby="login-form-title">
          <form className="login-card" onSubmit={submit} aria-busy={processing}>
            <header className="login-card__header">
              <p className="login-card__eyebrow">Tài khoản ShareLoop</p>
              <h2 id="login-form-title">Đăng nhập</h2>
              <p>Chào mừng bạn quay lại. Hãy đăng nhập để tiếp tục chia sẻ.</p>
            </header>

            {error ? (
              <div className="login-card__alert">
                <Alert tone="error">{error}</Alert>
              </div>
            ) : null}
            {successNotice ? (
              <div className="login-card__alert">
                <Alert tone="success">{successNotice}</Alert>
              </div>
            ) : null}

            <div className="login-form">
              <Field
                className="login-form__input"
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
              <div className="login-form__forgot">
                <Link to="/forgot-password">Quên mật khẩu?</Link>
              </div>
              <Button className="login-form__submit" disabled={processing}>
                {processing ? 'Đang đăng nhập...' : 'Đăng nhập'}
              </Button>
            </div>

            <div className="login-card__register">
              <span>Chưa có tài khoản?</span>
              <Link to="/register">Đăng ký ngay</Link>
            </div>
          </form>
        </section>
      </div>
    </main>
  );
}
