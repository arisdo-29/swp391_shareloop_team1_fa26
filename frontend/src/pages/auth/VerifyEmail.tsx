import { useEffect, useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAppDispatch } from '../../app/hooks';
import { actions } from '../../app/store';
import { Alert, Button, Field } from '../../components/ui';
import { authApi } from '../../services/authApi';
import { authErrorMessage } from '../../services/authErrors';
import type { ApiError } from '../../services/http';
import { fetchSessionUser } from '../../services/session';
import { validEmail } from '../../utils/passwordSecurity';

type VerifyEmailState = {
  email?: string;
  otpExpiresInSeconds?: number;
  resendCooldownSeconds?: number;
};

function formatCountdown(seconds: number) {
  const minutes = Math.floor(seconds / 60);
  return `${String(minutes).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`;
}

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
  const [otpSeconds, setOtpSeconds] = useState<number | null>(state.otpExpiresInSeconds ?? null);
  const [resendSeconds, setResendSeconds] = useState(state.resendCooldownSeconds ?? 0);

  useEffect(() => {
    if (!resendSeconds) return undefined;
    const timer = window.setInterval(() => setResendSeconds((value) => Math.max(0, value - 1)), 1000);
    return () => window.clearInterval(timer);
  }, [resendSeconds]);

  useEffect(() => {
    if (otpSeconds === null || otpSeconds === 0) return undefined;
    const timer = window.setInterval(() => setOtpSeconds((value) => value === null ? null : Math.max(0, value - 1)), 1000);
    return () => window.clearInterval(timer);
  }, [otpSeconds]);

  const resend = async () => {
    const normalizedEmail = email.trim().toLowerCase();
    if (!normalizedEmail || resendSeconds > 0 || processing) return;
    if (!validEmail(normalizedEmail)) {
      setError('Email không đúng định dạng.');
      return;
    }
    setError('');
    setNotice('');
    setProcessing(true);
    try {
      const result = await authApi.resendOtp({ email: normalizedEmail });
      setEmail(result.email || normalizedEmail);
      setOtpSeconds(result.otpExpiresInSeconds);
      setResendSeconds(result.resendCooldownSeconds);
      setNotice('Mã OTP mới đã được gửi đến email của bạn.');
    } catch (apiError) {
      const apiResponseError = apiError as ApiError;
      const retryAfter = apiResponseError.details?.retryAfterSeconds;
      if (typeof retryAfter === 'number') setResendSeconds(retryAfter);
      setError(authErrorMessage(apiResponseError));
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
    if (!validEmail(normalizedEmail)) {
      setError('Email không đúng định dạng.');
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
    if (otpSeconds === 0) {
      setError('Mã OTP đã hết hạn. Vui lòng gửi lại mã.');
      return;
    }
    setProcessing(true);
    try {
      await authApi.verifyOtp({ email: normalizedEmail, otp: code });
      const user = await fetchSessionUser();
      dispatch(actions.loginFromApi(user));
      navigate(user.role === 'admin' ? '/admin' : '/', { replace: true });
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
          {otpSeconds !== null ? (
            <p className={`text-center text-xs ${otpSeconds === 0 ? 'text-error' : 'text-text-muted'}`}>
              {otpSeconds === 0 ? 'Mã OTP đã hết hạn.' : `Mã OTP hết hạn sau ${formatCountdown(otpSeconds)}.`}
            </p>
          ) : null}
          <Button className="w-full" disabled={processing || otpSeconds === 0}>
            {processing ? 'Đang xác nhận...' : 'Xác nhận'}
          </Button>
          <Button
            type="button"
            variant="ghost"
            className="w-full"
            disabled={processing || !email.trim() || resendSeconds > 0}
            onClick={() => void resend()}
          >
            {resendSeconds > 0 ? `Gửi lại mã sau ${formatCountdown(resendSeconds)}` : 'Gửi lại mã'}
          </Button>
        </div>
      </form>
    </div>
  );
}
