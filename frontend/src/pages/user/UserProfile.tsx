import { useNavigate } from 'react-router-dom';
import { Button, EmptyState, PageHeader } from '../../components/ui';

/** Trang Hồ sơ công khai (giữ chỗ). Đợt sau sẽ gọi GET /users/{id}/profile và /users/{id}/items. */
export function UserProfile() {
  const navigate = useNavigate();

  return (
    <div className="page-shell">
      <PageHeader title="Hồ sơ" />
      <EmptyState
        title="Trang đang được hoàn thiện"
        text="Hồ sơ công khai sẽ sớm hiển thị hạng, uy tín và các món đồ của người dùng."
        action={<Button onClick={() => navigate('/messages')}>Quay lại tin nhắn</Button>}
      />
    </div>
  );
}
