import { type FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Alert, Button, Field } from '../../components/ui';
import { PasswordField } from '../../components/PasswordField';
import {
  normalizeVietnamPhone,
  validEmail,
  validVietnamPhone,
  validatePassword,
} from '../../utils/validation';
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
    if (processing) return;
    const nextErrors: Record<string, string> = {};
    const trimmedName = name.trim();
    const trimmedEmail = email.trim().toLowerCase();
    const normalizedPhone = normalizeVietnamPhone(phone.trim());

    if (!trimmedName) nextErrors.name = 'Vui lòng nhập họ và tên.';
    else if (trimmedName.length > 100) nextErrors.name = 'Họ và tên không được vượt quá 100 ký tự.';
    if (!trimmedEmail) nextErrors.email = 'Vui lòng nhập email.';
    else if (!validEmail(trimmedEmail)) nextErrors.email = 'Email không đúng định dạng.';
    else if (trimmedEmail.length > 255) nextErrors.email = 'Email không được vượt quá 255 ký tự.';
    if (!normalizedPhone) nextErrors.phone = 'Vui lòng nhập số điện thoại.';
    else if (!validVietnamPhone(normalizedPhone)) nextErrors.phone = 'Số điện thoại phải có dạng 0xxxxxxxxx hoặc +84xxxxxxxxx.';
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
        phone: normalizedPhone,
      });
      navigate('/verify-email', {
        state: {
          email: result.email || trimmedEmail,
          otpExpiresInSeconds: result.otpExpiresInSeconds,
          resendCooldownSeconds: result.resendCooldownSeconds,
        },
      });
    } catch (apiError) {
      const error = apiError as ApiError;
      // Backend không gắn field cho lỗi trùng số điện thoại nên chỉ về đúng ô nhập bằng mã lỗi.
      const field = error.code === 'AUTH_PHONE_ALREADY_USED' ? 'phone' : error.field === 'fullName' ? 'name' : error.field;
      setErrors(
        field && ['name', 'email', 'phone', 'password'].includes(field)
          ? { [field]: authErrorMessage(error) }
          : { form: authErrorMessage(error) },
      );
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
            maxLength={100}
            autoComplete="name"
          />
          <Field
            required
            label="Email"
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            error={errors.email}
            maxLength={255}
            autoComplete="email"
          />
          <Field
            required
            label="Số điện thoại"
            type="tel"
            value={phone}
            onChange={(event) => setPhone(event.target.value)}
            error={errors.phone}
            placeholder="+84330485296"
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
          <Link to="/login" className="inline-flex min-h-11 items-center font-semibold text-primary hover:text-primary-hover hover:underline">
            Đăng nhập
          </Link>
        </p>
      </form>
    </div>
  );
}
