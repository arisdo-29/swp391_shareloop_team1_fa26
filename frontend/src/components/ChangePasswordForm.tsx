import { useState, type FormEvent } from 'react';
import { authApi } from '../services/authApi';
import { authErrorMessage } from '../services/authErrors';
import type { ApiError } from '../services/http';
import { toBackendUserId } from '../services/requestApi';
import type { User } from '../types/domain';
import { passwordRules, validatePassword } from '../utils/validation';
import { Alert, Button } from './ui';
import { PasswordField } from './PasswordField';

export function ChangePasswordForm({ user }: { user: User }) {
  const isApiAccount = toBackendUserId(user.id) !== null;
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [currentError, setCurrentError] = useState('');
  const [newError, setNewError] = useState('');
  const [confirmError, setConfirmError] = useState('');
  const [processing, setProcessing] = useState(false);
  const [success, setSuccess] = useState('');

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setSuccess('');
    setCurrentError('');
    setNewError('');
    setConfirmError('');
    if (!currentPassword) {
      setCurrentError('Vui lòng nhập mật khẩu hiện tại.');
      return;
    }
    const validationErrors = validatePassword(newPassword);
    if (validationErrors.length) {
      setNewError(validationErrors[0]);
      return;
    }
    if (!confirmPassword) {
      setConfirmError('Vui lòng nhập mật khẩu xác nhận.');
      return;
    }
    if (newPassword !== confirmPassword) {
      setConfirmError(passwordRules.mismatch);
      return;
    }
    if (newPassword === currentPassword) {
      setNewError(passwordRules.different);
      return;
    }
    if (!isApiAccount) {
      setCurrentError('Tài khoản demo không thể đổi mật khẩu qua Backend.');
      return;
    }

    setProcessing(true);
    try {
      await authApi.changePassword({ currentPassword, newPassword });
      setSuccess('Đổi mật khẩu thành công.');
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
    } catch (error) {
      const apiError = error as ApiError;
      if (apiError.field === 'newPassword') setNewError(authErrorMessage(apiError));
      else setCurrentError(authErrorMessage(apiError));
    } finally {
      setProcessing(false);
    }
  };

  return (
    <form onSubmit={submit} className="rounded-xl bg-white p-5 ring-1 ring-border/80 sm:p-6">
      <h2 className="section-title">Đổi mật khẩu</h2>
      <p className="mt-1 text-sm text-text-muted">
        Để bảo vệ tài khoản, hãy chọn mật khẩu mạnh và không dùng lại mật khẩu cũ.
      </p>
      {success ? <div className="mt-4"><Alert tone="success">{success}</Alert></div> : null}
      <div className="mt-6 space-y-4">
        <PasswordField label="Mật khẩu hiện tại" value={currentPassword} onChange={setCurrentPassword} error={currentError} autoComplete="current-password" />
        <PasswordField label="Mật khẩu mới" value={newPassword} onChange={setNewPassword} error={newError} autoComplete="new-password" />
        <PasswordField label="Xác nhận mật khẩu mới" value={confirmPassword} onChange={setConfirmPassword} error={confirmError} autoComplete="new-password" />
        <Button disabled={processing}>{processing ? 'Đang xử lý...' : 'Đổi mật khẩu'}</Button>
      </div>
    </form>
  );
}
