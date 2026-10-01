import { type FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '../../app/hooks';
import { actions, selectData } from '../../app/store';
import { Alert, Button, Field, Select } from '../../components/ui';
import { PasswordField } from '../../components/PasswordField';
import {
  generateOtp,
  hashPassword,
  passwordRules,
  validEmail,
  validVietnamPhone,
  validatePassword,
} from '../../utils/passwordSecurity';

const OTP_TTL_MS = 5 * 60 * 1000;
const RESEND_WAIT_MS = 60 * 1000;

export function Register() {
  const data = useAppSelector(selectData);
  const districts = data.districts
    .filter((entry) => entry.status === 'active')
    .map((entry) => entry.name);
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [username, setUsername] = useState('');
  const [phone, setPhone] = useState('');
  const [district, setDistrict] = useState<string>(districts[0] ?? '');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [processing, setProcessing] = useState(false);
  const dispatch = useAppDispatch();
  const navigate = useNavigate();

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const nextErrors: Record<string, string> = {};
    const trimmedName = name.trim();
    const trimmedEmail = email.trim().toLowerCase();
    const trimmedUsername = username.trim();
    const trimmedPhone = phone.trim();

    if (!trimmedName) nextErrors.name = 'Vui lòng nhập họ và tên.';
    if (!trimmedEmail) nextErrors.email = 'Vui lòng nhập email.';
    else if (!validEmail(trimmedEmail)) nextErrors.email = 'Email không đúng định dạng.';
    if (!trimmedPhone) nextErrors.phone = 'Vui lòng nhập số điện thoại.';
    else if (!validVietnamPhone(trimmedPhone)) nextErrors.phone = 'Số điện thoại Việt Nam không hợp lệ.';
    if (!trimmedUsername) nextErrors.username = 'Vui lòng nhập tên đăng nhập.';
    const passwordErrors = validatePassword(password);
    if (passwordErrors.length) nextErrors.password = passwordErrors[0];
    if (!confirmPassword) nextErrors.confirmPassword = 'Vui lòng xác nhận mật khẩu.';
    else if (password !== confirmPassword) nextErrors.confirmPassword = passwordRules.mismatch;
    if (data.users.some((user) => user.username.toLowerCase() === trimmedUsername.toLowerCase())) {
      nextErrors.username = 'Tên đăng nhập đã được sử dụng.';
    }
    if (data.users.some((user) => user.email.toLowerCase() === trimmedEmail)) {
      nextErrors.email = 'Email đã được sử dụng.';
    }
    if (
      data.emailVerification &&
      (data.emailVerification.registration.username.toLowerCase() === trimmedUsername.toLowerCase() ||
        data.emailVerification.registration.email.toLowerCase() === trimmedEmail)
    ) {
      nextErrors.email = 'Email này đang chờ xác thực. Vui lòng kiểm tra mã OTP.';
    }
    if (Object.keys(nextErrors).length) {
      setErrors(nextErrors);
      return;
    }

    setProcessing(true);
    const now = Date.now();
    const generatedOtp = generateOtp();
    dispatch(actions.startRegistration({
      registration: {
        name: trimmedName,
        email: trimmedEmail,
        username: trimmedUsername,
        phone: trimmedPhone,
        district,
        passwordHash: await hashPassword(password),
      },
      verificationId: crypto.randomUUID(),
      otpHash: await hashPassword(generatedOtp),
      expiresAt: new Date(now + OTP_TTL_MS).toISOString(),
      resendAvailableAt: new Date(now + RESEND_WAIT_MS).toISOString(),
    }));
    setProcessing(false);
    navigate('/verify-email');
  };

  return (
    <div className="page-shell flex min-h-[620px] items-center justify-center">
      <form
        onSubmit={submit}
        className="w-full max-w-lg rounded-xl bg-white p-6 shadow-md ring-1 ring-border/70 sm:p-8"
      >
        <h1 className="text-2xl font-bold">Tạo tài khoản</h1>
        <p className="mt-2 text-sm leading-6 text-text-muted">
          Sau khi đăng ký, ShareLoop sẽ gửi mã OTP đến email của bạn để xác thực tài khoản.
        </p>
        {errors.form ? <div className="mt-5"><Alert tone="error">{errors.form}</Alert></div> : null}
        <div className="mt-6 space-y-4">
          <Field
            required
            label="Họ và tên"
            value={name}
            onChange={(event) => setName(event.target.value)}
            error={errors.name}
            autoComplete="name"
          />
          <Field
            required
            label="Email"
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            error={errors.email}
            autoComplete="email"
          />
          <div className="grid gap-4 sm:grid-cols-2">
            <Field
              required
              label="Số điện thoại"
              value={phone}
              onChange={(event) => setPhone(event.target.value)}
              error={errors.phone}
              placeholder="09xx xxx xxx"
              autoComplete="tel"
            />
            <Field
              required
              label="Tên đăng nhập"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              error={errors.username}
              autoComplete="username"
            />
          </div>
          <Select label="Quận" value={district} onChange={(event) => setDistrict(event.target.value)}>
            {districts.map((entry) => (
              <option key={entry}>{entry}</option>
            ))}
          </Select>
          <PasswordField
            label="Mật khẩu"
            value={password}
            onChange={setPassword}
            autoComplete="new-password"
            error={errors.password}
          />
          <PasswordField
            label="Xác nhận mật khẩu"
            value={confirmPassword}
            onChange={setConfirmPassword}
            autoComplete="new-password"
            error={errors.confirmPassword}
          />
          <Button className="w-full" disabled={processing}>
            {processing ? 'Đang gửi mã...' : 'Đăng ký'}
          </Button>
        </div>
        <p className="mt-6 text-center text-sm text-text-muted">
          Đã có tài khoản?{' '}
          <Link to="/login" className="font-semibold text-primary">
            Đăng nhập
          </Link>
        </p>
      </form>
    </div>
  );
}
