import { type FormEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Alert, Button, Field } from '../../components/ui';
import { PasswordField } from '../../components/PasswordField';
import { AuthLayout } from './AuthLayout';
import {
  normalizeVietnamPhone,
  validEmail,
  validVietnamPhone,
  validatePassword,
} from '../../utils/validation';
import { authApi } from '../../services/authApi';
import { authErrorMessage } from '../../services/authErrors';
import type { ApiError } from '../../services/http';

type RegisterField = 'name' | 'email' | 'phone' | 'password';

function validateField(field: RegisterField, value: string) {
  if (field === 'name') {
    const trimmedName = value.trim();
    if (!trimmedName) return 'Vui lòng nhập họ và tên.';
    if (trimmedName.length > 100) return 'Họ và tên không được vượt quá 100 ký tự.';
  }

  if (field === 'email') {
    const trimmedEmail = value.trim();
    if (!trimmedEmail) return 'Vui lòng nhập email.';
    if (!validEmail(trimmedEmail)) return 'Email không đúng định dạng.';
    if (trimmedEmail.length > 255) return 'Email không được vượt quá 255 ký tự.';
  }

  if (field === 'phone') {
    const normalizedPhone = normalizeVietnamPhone(value.trim());
    if (!normalizedPhone) return 'Vui lòng nhập số điện thoại.';
    if (!validVietnamPhone(normalizedPhone)) {
      return 'Số điện thoại phải có dạng 0xxxxxxxxx hoặc +84xxxxxxxxx.';
    }
  }

  if (field === 'password') return validatePassword(value)[0] ?? '';
  return '';
}

export function Register() {
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [processing, setProcessing] = useState(false);
  const navigate = useNavigate();

  const values: Record<RegisterField, string> = { name, email, phone, password };

  const updateFieldError = (field: RegisterField, value: string) => {
    const error = validateField(field, value);
    setErrors((current) => {
      const next = { ...current };
      if (error) next[field] = error;
      else delete next[field];
      return next;
    });
  };

  const blurField = (field: RegisterField) => {
    updateFieldError(field, values[field]);
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (processing) return;
    const nextErrors: Record<string, string> = {};
    const trimmedName = name.trim();
    const trimmedEmail = email.trim().toLowerCase();
    const normalizedPhone = normalizeVietnamPhone(phone.trim());

    (Object.keys(values) as RegisterField[]).forEach((field) => {
      const error = validateField(field, values[field]);
      if (error) nextErrors[field] = error;
    });
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
    <AuthLayout showTabs>
      <form onSubmit={submit} noValidate aria-labelledby="register-title">
        <h2 id="register-title" className="text-2xl font-bold">Tạo tài khoản</h2>
        {errors.form ? <div className="mt-4"><Alert tone="error">{errors.form}</Alert></div> : null}
        <div className="mt-5 space-y-4 [&_.label]:after:ml-0.5 [&_.label]:after:text-error [&_.label]:after:content-['*']">
          <Field
            required
            label="Họ và tên"
            value={name}
            onChange={(event) => setName(event.target.value)}
            onBlur={() => blurField('name')}
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
            onBlur={() => blurField('email')}
            error={errors.email}
            hint="Mã xác minh sẽ gửi về email này."
            maxLength={255}
            autoComplete="email"
          />
          <Field
            required
            label="Số điện thoại"
            type="tel"
            value={phone}
            onChange={(event) => setPhone(event.target.value)}
            onBlur={() => blurField('phone')}
            error={errors.phone}
            placeholder="0901234567"
            hint="Chỉ hiện cho người giao dịch với bạn, sau khi hai bên chốt lịch."
            autoComplete="tel"
          />
          <PasswordField
            label="Mật khẩu"
            value={password}
            onChange={setPassword}
            onBlur={() => blurField('password')}
            autoComplete="new-password"
            error={errors.password}
          />
          <Button size="lg" className="w-full" disabled={processing}>
            {processing ? 'Đang gửi mã...' : 'Đăng ký'}
          </Button>
        </div>
      </form>
    </AuthLayout>
  );
}
