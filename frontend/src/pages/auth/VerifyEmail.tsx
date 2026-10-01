import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '../../app/hooks';
import { actions, selectData } from '../../app/store';
import { Alert, Button } from '../../components/ui';
import { generateOtp, hashPassword, verifyPassword } from '../../utils/passwordSecurity';

const OTP_TTL_MS = 5 * 60 * 1000;
const RESEND_WAIT_MS = 60 * 1000;

export function VerifyEmail() {
  const data = useAppSelector(selectData);
  const verification = data.emailVerification;
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const [otp, setOtp] = useState(['', '', '', '', '', '']);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [processing, setProcessing] = useState(false);
  const [seconds, setSeconds] = useState(0);

  useEffect(() => {
    if (!verification) return;
    setSeconds(Math.max(0, Math.ceil((new Date(verification.resendAvailableAt).getTime() - Date.now()) / 1000)));
  }, [verification]);

  useEffect(() => {
    if (!seconds) return undefined;
    const timer = window.setInterval(() => setSeconds((value) => Math.max(0, value - 1)), 1000);
    return () => window.clearInterval(timer);
  }, [seconds]);

  const resend = async () => {
    if (!verification || seconds > 0) return;
    setError('');
    setNotice('');
    setProcessing(true);
    const now = Date.now();
    const generatedOtp = generateOtp();
    dispatch(actions.resendRegistrationOtp({
      verificationId: verification.id,
      otpHash: await hashPassword(generatedOtp),
      expiresAt: new Date(now + OTP_TTL_MS).toISOString(),
      resendAvailableAt: new Date(now + RESEND_WAIT_MS).toISOString(),
    }));
    setSeconds(60);
    setNotice('Mã OTP mới đã được gửi đến email của bạn.');
    setProcessing(false);
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const code = otp.join('');
    setError('');
    setNotice('');
    if (!verification) {
      setError('Không tìm thấy phiên đăng ký. Vui lòng đăng ký lại.');
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
    if (Date.now() > new Date(verification.expiresAt).getTime()) {
      setError('Mã OTP đã hết hạn. Vui lòng gửi lại mã.');
      return;
    }
    if (verification.attempts >= 5) {
      setError('Bạn đã nhập sai quá số lần cho phép. Vui lòng gửi lại mã.');
      return;
    }
    setProcessing(true);
    const valid = await verifyPassword(code, verification.otpHash);
    dispatch(actions.verifyRegistrationOtp({
      verificationId: verification.id,
      otpHash: valid ? verification.otpHash : '',
    }));
    setProcessing(false);
    if (!valid) {
      setError(verification.attempts + 1 >= 5 ? 'Bạn đã nhập sai quá số lần cho phép. Vui lòng gửi lại mã.' : 'Mã OTP không chính xác.');
      return;
    }
    navigate('/login', { state: { notice: 'Đăng ký thành công. Vui lòng đăng nhập.' } });
  };

  return (
    <div className="page-shell flex min-h-[620px] items-center justify-center">
      <form
        onSubmit={submit}
        className="w-full max-w-md rounded-xl bg-white p-6 shadow-md ring-1 ring-border/70 sm:p-8"
      >
        <h1 className="text-2xl font-bold">Xác thực email</h1>
        <p className="mt-2 text-sm leading-6 text-text-muted">Mã OTP đã được gửi đến email của bạn.</p>
        {!verification ? (
          <div className="mt-5">
            <Alert tone="warning">
              Phiên xác thực không tồn tại. <Link to="/register" className="font-semibold underline">Đăng ký lại</Link>
            </Alert>
          </div>
        ) : null}
        {notice ? <div className="mt-5"><Alert tone="success">{notice}</Alert></div> : null}
        {error ? <div className="mt-5"><Alert tone="error">{error}</Alert></div> : null}
        <div className="mt-7">
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
          <Button className="mt-5 w-full" disabled={processing || !verification}>
            {processing ? 'Đang xác nhận...' : 'Xác nhận'}
          </Button>
          <Button
            type="button"
            variant="ghost"
            className="mt-2 w-full"
            disabled={processing || !verification || seconds > 0}
            onClick={() => void resend()}
          >
            {seconds > 0 ? `Gửi lại mã sau 00:${String(seconds).padStart(2, '0')}` : 'Gửi lại mã'}
          </Button>
        </div>
      </form>
    </div>
  );
}
