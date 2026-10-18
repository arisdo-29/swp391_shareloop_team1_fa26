import { useEffect, useState, type FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Alert, Button, Field, PageHeader } from '../../components/ui';
import { PasswordField } from '../../components/PasswordField';
import { authApi } from '../../services/authApi';
import { authErrorMessage } from '../../services/authErrors';
import type { ApiError } from '../../services/http';
import { passwordRules, validEmail, validatePassword } from '../../utils/passwordSecurity';

interface ApiResetState {
  email: string;
  otpExpiresInSeconds: number;
  resendCooldownSeconds: number;
}

function formatCountdown(seconds: number) {
  const minutes = Math.floor(seconds / 60);
  return `${String(minutes).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`;
}

export function ForgotPassword() {
  const navigate = useNavigate();
  const location = useLocation();
  const path = location.pathname;
  const step = path === '/forgot-password' ? 'email' : 'reset';
  const apiReset = location.state as ApiResetState | null;
  const [email, setEmail] = useState(apiReset?.email ?? '');
  const [otp, setOtp] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [processing, setProcessing] = useState(false);
  const [otpSeconds, setOtpSeconds] = useState<number | null>(apiReset?.otpExpiresInSeconds ?? null);
  const [resendSeconds, setResendSeconds] = useState(apiReset?.resendCooldownSeconds ?? 0);

  useEffect(() => {
    if (!resendSeconds) return undefined;
    const timer = window.setInterval(() => setResendSeconds((value) => Math.max(0, value - 1)), 1000);
    return () => window.clearInterval(timer);
  }, [resendSeconds]);

  useEffect(() => {
    if (otpSeconds === null || otpSeconds === 0) return undefined;
    const timer = window.setInterval(
      () => setOtpSeconds((value) => value === null ? null : Math.max(0, value - 1)),
      1000,
    );
    return () => window.clearInterval(timer);
  }, [otpSeconds]);

  useEffect(() => {
    if (step === 'reset' && !email) navigate('/forgot-password', { replace: true });
  }, [email, navigate, step]);

  const createOtpRequest = async (resend = false, requestEmail = email.trim().toLowerCase()) => {
    if (processing || (resend && resendSeconds > 0)) return;
    setProcessing(true);
    setError('');
    setNotice('');
    try {
      const result = await authApi.forgotPassword({ email: requestEmail });
      const state: ApiResetState = {
        email: result.email || requestEmail,
        otpExpiresInSeconds: result.otpExpiresInSeconds,
        resendCooldownSeconds: result.resendCooldownSeconds,
      };
      setEmail(state.email);
      setOtpSeconds(result.otpExpiresInSeconds);
      setResendSeconds(result.resendCooldownSeconds);
      if (!resend) {
        navigate('/forgot-password/verify', { state });
      }
      setNotice('Nếu email tồn tại trong hệ thống, mã OTP đã được gửi.');
    } catch (apiError) {
      setError(authErrorMessage(apiError as ApiError));
    } finally {
      setProcessing(false);
    }
  };

  const submitEmail = async (event: FormEvent) => {
    event.preventDefault();
    const trimmedEmail = email.trim().toLowerCase();
    if (!trimmedEmail) {
      setError('Vui lòng nhập email.');
      return;
    }
    if (!validEmail(trimmedEmail)) {
      setError('Email không đúng định dạng.');
      return;
    }
    setEmail(trimmedEmail);
    await createOtpRequest(false, trimmedEmail);
  };

  const resetPassword = async (event: FormEvent) => {
    event.preventDefault();
    setError('');
    if (!/^\d{6}$/.test(otp)) {
      setError('Mã OTP phải gồm đúng 6 chữ số.');
      return;
    }
    if (otpSeconds === 0) {
      setError('Mã OTP đã hết hạn. Vui lòng gửi lại mã.');
      return;
    }
    const passwordErrors = validatePassword(newPassword);
    if (passwordErrors.length) {
      setError(passwordErrors[0]);
      return;
    }
    if (newPassword !== confirmPassword) {
      setError(passwordRules.mismatch);
      return;
    }
    setProcessing(true);
    try {
      await authApi.resetPassword({ email, otp, newPassword });
      navigate('/login', {
        replace: true,
        state: { notice: 'Đặt lại mật khẩu thành công. Vui lòng đăng nhập.' },
      });
    } catch (apiError) {
      const responseError = apiError as ApiError;
      if (responseError.code === 'AUTH_NEW_PASSWORD_SAME_AS_CURRENT') setOtpSeconds(0);
      setError(authErrorMessage(responseError));
    } finally {
      setProcessing(false);
    }
  };

  return (
    <div className="page-shell flex min-h-[620px] items-center justify-center">
      <form
        onSubmit={step === 'email' ? submitEmail : resetPassword}
        className="w-full max-w-md rounded-xl bg-white p-6 shadow-md ring-1 ring-border/70 sm:p-8"
      >
        <PageHeader
          title="Quên mật khẩu"
          description="Khôi phục quyền truy cập an toàn cho tài khoản của bạn."
        />
        {notice ? <Alert tone="info">{notice}</Alert> : null}
        {error ? <div className="mt-4"><Alert tone="error">{error}</Alert></div> : null}
        <div className="mt-6 space-y-4">
          {step === 'email' ? (
            <Field
              required
              label="Email"
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              error={error && !validEmail(email) ? error : undefined}
              autoComplete="email"
            />
          ) : null}
          {step === 'reset' ? (
            <>
              <Field label="Email" type="email" value={email} readOnly autoComplete="email" />
              <Field
                required
                label="Mã OTP"
                inputMode="numeric"
                maxLength={6}
                value={otp}
                onChange={(event) => setOtp(event.target.value.replace(/\D/g, '').slice(0, 6))}
              />
              <PasswordField label="Mật khẩu mới" value={newPassword} onChange={setNewPassword} autoComplete="new-password" />
              <PasswordField label="Xác nhận mật khẩu mới" value={confirmPassword} onChange={setConfirmPassword} autoComplete="new-password" />
              {otpSeconds !== null ? (
                <p className={`text-center text-xs ${otpSeconds === 0 ? 'text-error' : 'text-text-muted'}`}>
                  {otpSeconds === 0 ? 'Mã OTP đã hết hạn.' : `Mã OTP hết hạn sau ${formatCountdown(otpSeconds)}.`}
                </p>
              ) : null}
              <Button
                type="button"
                variant="ghost"
                className="w-full"
                disabled={resendSeconds > 0 || processing}
                onClick={() => void createOtpRequest(true)}
              >
                {resendSeconds > 0 ? `Gửi lại mã sau ${formatCountdown(resendSeconds)}` : 'Gửi lại mã'}
              </Button>
            </>
          ) : null}
          <Button className="w-full" disabled={processing || (step === 'reset' && otpSeconds === 0)}>
            {processing ? 'Đang xử lý...' : step === 'email' ? 'Gửi mã OTP' : 'Đặt lại mật khẩu'}
          </Button>
        </div>
      </form>
    </div>
  );
}
