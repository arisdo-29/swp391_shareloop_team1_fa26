import { useEffect, useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAppDispatch } from '../../app/hooks';
import { actions } from '../../app/store';
import { Alert, Button, Field } from '../../components/ui';
import { authApi } from '../../services/authApi';
import { authErrorMessage } from '../../services/authErrors';
import type { ApiError } from '../../services/http';
import { toFeUser } from '../../services/mappers';

type VerifyEmailState = {
  email?: string;
  resendCooldownSeconds?: number;
};

export function VerifyEmail() {
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const location = useLocation();
  const state = (location.state as VerifyEmailState | null) ?? {};
  const [email, setEmail] = useState(state.email?.trim().toLowerCase() ?? '');
  const [otp, setOtp] = useState(['', '', '', '', '', '']);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [processing, setProcessing] = useState(false);
  const [seconds, setSeconds] = useState(state.resendCooldownSeconds ?? 0);

  useEffect(() => {
    if (!seconds) return undefined;
    const timer = window.setInterval(() => setSeconds((value) => Math.max(0, value - 1)), 1000);
    return () => window.clearInterval(timer);
  }, [seconds]);

  const resend = async () => {
    const normalizedEmail = email.trim().toLowerCase();
    if (!normalizedEmail || seconds > 0) return;
    setError('');
    setNotice('');
    setProcessing(true);
    try {
      const result = await authApi.resendOtp(normalizedEmail);
      setSeconds(result.resendCooldownSeconds);
      setNotice('Mã OTP mới đã được gửi đến email của bạn.');
    } catch (apiError) {
      setError(authErrorMessage(apiError as ApiError));
    } finally {
      setProcessing(false);
    }
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const normalizedEmail = email.trim().toLowerCase();
    const code = otp.join('');
    setError('');
    setNotice('');
    if (!normalizedEmail) {
      setError('Vui lòng nhập email.');
      return;
    }
    if (!code) {
      setError('Vui lòng nhập mã OTP.');
      return;
    }
    if (!/^\d{6}$/.test(code)) {
      setError('Mã OTP không hợp lệ.');
      return;
    }
    setProcessing(true);
    try {
      const result = await authApi.verifyOtp(normalizedEmail, code);
      dispatch(actions.loginFromApi(toFeUser(result.user)));
      navigate(result.user.isAdmin ? '/admin' : '/', { replace: true });
    } catch (apiError) {
      setError(authErrorMessage(apiError as ApiError));
    } finally {
      setProcessing(false);
    }
  };

  return (
    <div className="page-shell flex min-h-[620px] items-center justify-center">
      <form
        onSubmit={submit}
        className="w-full max-w-md rounded-xl bg-white p-6 shadow-md ring-1 ring-border/70 sm:p-8"
      >
        <h1 className="text-2xl font-bold">Xác thực email</h1>
        <p className="mt-2 text-sm leading-6 text-text-muted">Nhập email và mã OTP đã được gửi đến email của bạn.</p>
        {!email ? (
          <div className="mt-5">
            <Alert tone="warning">
              Chưa có email xác thực. <Link to="/register" className="font-semibold underline">Đăng ký lại</Link>
            </Alert>
          </div>
        ) : null}
        {notice ? <div className="mt-5"><Alert tone="success">{notice}</Alert></div> : null}
        {error ? <div className="mt-5"><Alert tone="error">{error}</Alert></div> : null}
        <div className="mt-7 space-y-4">
          <Field
            required
            label="Email"
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            autoComplete="email"
          />
          <div className="grid grid-cols-6 gap-2">
            {otp.map((value, index) => (
              <input
                key={index}
                aria-label={`Số OTP ${index + 1}`}
                inputMode="numeric"
                maxLength={1}
                value={value}
                onChange={(event) => {
                  const next = [...otp];
                  next[index] = event.target.value.replace(/\D/g, '');
                  setOtp(next);
                }}
                className="aspect-square min-w-0 rounded-md border border-border text-center text-lg font-bold outline-none focus:border-primary focus:ring-4 focus:ring-primary/10"
              />
            ))}
          </div>
          <Button className="w-full" disabled={processing}>
            {processing ? 'Đang xác nhận...' : 'Xác nhận'}
          </Button>
          <Button
            type="button"
            variant="ghost"
            className="w-full"
            disabled={processing || !email.trim() || seconds > 0}
            onClick={() => void resend()}
          >
            {seconds > 0 ? `Gửi lại mã sau 00:${String(seconds).padStart(2, '0')}` : 'Gửi lại mã'}
          </Button>
        </div>
      </form>
    </div>
  );
}
