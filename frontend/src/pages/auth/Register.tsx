import { type FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Alert, Button, Field } from '../../components/ui';
import { PasswordField } from '../../components/PasswordField';
import { validEmail, validVietnamPhone, validatePassword } from '../../utils/passwordSecurity';
import { authApi } from '../../services/authApi';
import { authErrorMessage } from '../../services/authErrors';
import type { ApiError } from '../../services/http';

export function Register() {
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [processing, setProcessing] = useState(false);
  const navigate = useNavigate();

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const nextErrors: Record<string, string> = {};
    const trimmedName = name.trim();
    const trimmedEmail = email.trim().toLowerCase();
    const trimmedPhone = phone.trim();

    if (!trimmedName) nextErrors.name = 'Vui lòng nhập họ và tên.';
    if (!trimmedEmail) nextErrors.email = 'Vui lòng nhập email.';
    else if (!validEmail(trimmedEmail)) nextErrors.email = 'Email không đúng định dạng.';
    if (!trimmedPhone) nextErrors.phone = 'Vui lòng nhập số điện thoại.';
    else if (!validVietnamPhone(trimmedPhone)) nextErrors.phone = 'Số điện thoại Việt Nam không hợp lệ.';
    const passwordErrors = validatePassword(password);
    if (passwordErrors.length) nextErrors.password = passwordErrors[0];
    if (Object.keys(nextErrors).length) {
      setErrors(nextErrors);
      return;
    }

    setErrors({});
    setProcessing(true);
    try {
      const result = await authApi.register({
        email: trimmedEmail,
        password,
        fullName: trimmedName,
        phone: trimmedPhone,
      });
      navigate('/verify-email', {
        state: {
          email: result.email || trimmedEmail,
          resendCooldownSeconds: result.resendCooldownSeconds,
        },
      });
    } catch (apiError) {
      setErrors({ form: authErrorMessage(apiError as ApiError) });
    } finally {
      setProcessing(false);
    }
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
          <Field
            required
            label="Số điện thoại"
            value={phone}
            onChange={(event) => setPhone(event.target.value)}
            error={errors.phone}
            placeholder="09xx xxx xxx"
            autoComplete="tel"
          />
          <PasswordField
            label="Mật khẩu"
            value={password}
            onChange={setPassword}
            autoComplete="new-password"
            error={errors.password}
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
