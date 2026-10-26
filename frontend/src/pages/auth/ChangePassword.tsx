import { ChangePasswordForm } from '../../components/ChangePasswordForm';
import { PageHeader } from '../../components/ui';

export function ChangePassword() {
  return (
    <div className="page-shell max-w-xl">
      <PageHeader title="Đổi mật khẩu" description="Bảo vệ tài khoản bằng mật khẩu mới." />
      <ChangePasswordForm />
    </div>
  );
}
