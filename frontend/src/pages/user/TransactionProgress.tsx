import { useNavigate, useParams } from 'react-router-dom';
import { Button, EmptyState, PageHeader } from '../../components/ui';
import { requestConversationPath } from '../../utils/chatMessages';

/**
 * Trang Tiến trình giao dịch (giữ chỗ). Đợt sau sẽ dựng đủ các bước, lịch giao nhận và ảnh giao nhận; trong lúc
 * chờ, mọi thao tác vẫn nằm ở bảng "Tiến trình giao dịch" bên phải màn Tin nhắn.
 */
export function TransactionProgress() {
  const navigate = useNavigate();
  const { requestId } = useParams();
  const id = Number(requestId);
  const backPath = Number.isSafeInteger(id) && id > 0 ? requestConversationPath(id) : '/messages';

  return (
    <div className="page-shell">
      <PageHeader title="Tiến trình giao dịch" />
      <EmptyState
        title="Trang đang được hoàn thiện"
        text="Bạn vẫn xem và thao tác giao dịch ở bảng Tiến trình giao dịch trong màn Tin nhắn."
        action={<Button onClick={() => navigate(backPath)}>Quay lại tin nhắn</Button>}
      />
    </div>
  );
}
