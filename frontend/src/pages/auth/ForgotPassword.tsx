import { useEffect, useState, type FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '../../app/hooks';
import { actions, selectData } from '../../app/store';
import { Alert, Button, Field, PageHeader } from '../../components/ui';
import { PasswordField } from '../../components/PasswordField';
import { authApi } from '../../services/authApi';
import { authErrorMessage } from '../../services/authErrors';
import type { ApiError } from '../../services/http';
import {
  generateOtp,
  hashPassword,
  passwordRules,
  validEmail,
  validatePassword,
  verifyPassword,
} from '../../utils/passwordSecurity';

const OTP_TTL_MS = 5 * 60 * 1000;
const RESEND_WAIT_MS = 60 * 1000;

interface ApiResetState {
  flow: 'api';
  email: string;
  expiresAt: string;
  resendAvailableAt: string;
}

export function ForgotPassword() {
  const data = useAppSelector(selectData);
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const location = useLocation();
  const path = location.pathname;
  const step = path === '/forgot-password/verify' ? 'otp' : path === '/reset-password' ? 'reset' : 'email';
  const apiReset = location.state as ApiResetState | null;
  const isApiReset = apiReset?.flow === 'api';
  const [email, setEmail] = useState(apiReset?.email ?? data.passwordReset?.email ?? '');
  const [otp, setOtp] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [processing, setProcessing] = useState(false);
  const [seconds, setSeconds] = useState(0);

  useEffect(() => {
    const resendAvailableAt = isApiReset ? apiReset.resendAvailableAt : data.passwordReset?.resendAvailableAt;
    if (!resendAvailableAt) return;
    setSeconds(Math.max(0, Math.ceil((new Date(resendAvailableAt).getTime() - Date.now()) / 1000)));
  }, [apiReset, data.passwordReset, isApiReset]);

  useEffect(() => {
    if (!seconds) return undefined;
    const timer = window.setInterval(() => setSeconds((value) => Math.max(0, value - 1)), 1000);
    return () => window.clearInterval(timer);
  }, [seconds]);

  useEffect(() => {
    if (step === 'otp' && !data.passwordReset && !isApiReset) navigate('/forgot-password', { replace: true });
    if (step === 'reset' && !data.passwordReset?.verified) navigate('/forgot-password', { replace: true });
  }, [apiReset, data.passwordReset, isApiReset, navigate, step]);

  const createOtpRequest = async (resend = false, requestEmail = email.trim().toLowerCase()) => {
    setProcessing(true);
    setError('');
    setNotice('');
    try {
      const demoAccount = data.users.some((user) => user.email.toLowerCase() === requestEmail);
      if (demoAccount && !isApiReset) {
        const generated = generateOtp();
        const now = Date.now();
        const payload = {
          otpHash: await hashPassword(generated),
          expiresAt: new Date(now + OTP_TTL_MS).toISOString(),
          resendAvailableAt: new Date(now + RESEND_WAIT_MS).toISOString(),
        };
        if (resend && data.passwordReset) {
          dispatch(actions.resendPasswordOtp({ resetId: data.passwordReset.id, ...payload }));
        } else {
          dispatch(actions.requestPasswordReset({
            email: requestEmail,
            resetId: crypto.randomUUID(),
            resetToken: crypto.randomUUID(),
            ...payload,
          }));
        }
        setSeconds(60);
        navigate('/forgot-password/verify');
      } else {
        const result = await authApi.forgotPassword(requestEmail);
        const now = Date.now();
        const state: ApiResetState = {
          flow: 'api',
          email: result.email || requestEmail,
          expiresAt: new Date(now + result.otpExpiresInSeconds * 1000).toISOString(),
          resendAvailableAt: new Date(now + result.resendCooldownSeconds * 1000).toISOString(),
        };
        setEmail(state.email);
        setSeconds(result.resendCooldownSeconds);
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

  const verifyOtp = async (event: FormEvent) => {
    event.preventDefault();
    const reset = data.passwordReset;
    setError('');
    if (!otp) {
      setError('Vui lòng nhập mã OTP.');
      return;
    }
    if (!/^\d{6}$/.test(otp)) {
      setError('Mã OTP không hợp lệ.');
      return;
    }
    if (isApiReset) {
      if (Date.now() > new Date(apiReset.expiresAt).getTime()) {
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
        await authApi.resetPassword({ email: apiReset.email, otp, newPassword });
        navigate('/login', { state: { notice: 'Đặt lại mật khẩu thành công.' } });
      } catch (apiError) {
        setError(authErrorMessage(apiError as ApiError));
      } finally {
        setProcessing(false);
      }
      return;
    }
    if (!reset || Date.now() > new Date(reset.expiresAt).getTime()) {
      setError('Mã OTP đã hết hạn. Vui lòng gửi lại mã.');
      return;
    }
    if (reset.attempts >= 5) {
      setError('Bạn đã nhập sai quá số lần cho phép. Vui lòng gửi lại mã.');
      return;
    }
    setProcessing(true);
    const valid = await verifyPassword(otp, reset.otpHash);
    dispatch(actions.verifyPasswordOtp({ resetId: reset.id, otpHash: valid ? reset.otpHash : '' }));
    setProcessing(false);
    if (!valid) {
      setError(reset.attempts + 1 >= 5 ? 'Bạn đã nhập sai quá số lần cho phép. Vui lòng gửi lại mã.' : 'Mã OTP không chính xác.');
      return;
    }
    navigate('/reset-password');
  };

  const resetPassword = async (event: FormEvent) => {
    event.preventDefault();
    const errors = validatePassword(newPassword);
    if (errors.length) {
      setError(errors[0]);
      return;
    }
    if (newPassword !== confirmPassword) {
      setError(passwordRules.mismatch);
      return;
    }
    const reset = data.passwordReset;
    if (!reset?.verified || !reset.resetToken) {
      setError('Phiên đặt lại mật khẩu không hợp lệ.');
      return;
    }
    setProcessing(true);
    const newPasswordHash = await hashPassword(newPassword);
    dispatch(actions.resetPassword({ resetId: reset.id, resetToken: reset.resetToken, newPasswordHash }));
    setProcessing(false);
    navigate('/login', { state: { notice: 'Đặt lại mật khẩu thành công.' } });
  };

  return (
    <div className="page-shell flex min-h-[620px] items-center justify-center">
      <form
        onSubmit={step === 'email' ? submitEmail : step === 'otp' ? verifyOtp : resetPassword}
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
          {step === 'otp' ? (
            <>
              <Field
                required
                label="Mã OTP"
                inputMode="numeric"
                maxLength={6}
                value={otp}
                onChange={(event) => setOtp(event.target.value.replace(/\D/g, '').slice(0, 6))}
              />
              <Button type="button" variant="ghost" disabled={seconds > 0 || processing} onClick={() => void createOtpRequest(true)}>
                {seconds > 0 ? `Gửi lại mã sau 00:${String(seconds).padStart(2, '0')}` : 'Gửi lại mã'}
              </Button>
              {isApiReset ? (
                <>
                  <PasswordField label="Mật khẩu mới" value={newPassword} onChange={setNewPassword} error={error} autoComplete="new-password" />
                  <PasswordField label="Xác nhận mật khẩu mới" value={confirmPassword} onChange={setConfirmPassword} autoComplete="new-password" />
                </>
              ) : null}
            </>
          ) : null}
          {step === 'reset' ? (
            <>
              <PasswordField label="Mật khẩu mới" value={newPassword} onChange={setNewPassword} error={error} autoComplete="new-password" />
              <PasswordField label="Xác nhận mật khẩu mới" value={confirmPassword} onChange={setConfirmPassword} autoComplete="new-password" />
            </>
          ) : null}
          <Button className="w-full" disabled={processing}>
            {step === 'email' ? 'Gửi mã OTP' : step === 'otp' ? (isApiReset ? 'Đặt lại mật khẩu' : 'Xác minh OTP') : 'Đặt lại mật khẩu'}
          </Button>
        </div>
      </form>
    </div>
  );
}
