import { useNavigate } from 'react-router-dom';
import { Avatar, Button, ItemImage, TypeBadge } from '../ui';
import type { ItemResponse } from '../../services/itemApi';
import type { RequestResponse } from '../../services/requestApi';
import { requestConversationPath, userProfilePath } from '../../utils/chatMessages';
import { avatarInitials } from '../messages/conversation';

const conditionLabel: Record<ItemResponse['condition'], string> = {
  NEW: 'Mới',
  LIKE_NEW: 'Ít sử dụng',
  GOOD: 'Đã dùng nhiều',
  DEFECTIVE: 'Có lỗi',
};

/** Thẻ "Món đồ" ở cột bên, kèm món mang đổi khi là giao dịch Trao đổi. */
export function ItemSideCard({
  request,
  title,
  image,
  offeredItem,
}: {
  request: RequestResponse;
  title: string;
  image?: string;
  offeredItem: ItemResponse | null;
}) {
  const navigate = useNavigate();
  const offeredTitle = offeredItem?.title ?? request.offeredItemTitle;

  return (
    <section className="card space-y-4 p-5 sm:p-6" aria-labelledby="item-title">
      <h2 id="item-title" className="section-title">
        Món đồ
      </h2>
      <div className="flex items-center gap-3">
        <ItemImage src={image} alt={title} className="size-16 shrink-0 rounded-md object-cover" />
        <div className="min-w-0">
          <p className="line-clamp-2 text-sm font-semibold">{title}</p>
          <div className="mt-1">
            <TypeBadge type={request.type === 'SWAP' ? 'trade' : 'gift'} />
          </div>
        </div>
      </div>
      {request.type === 'SWAP' && (offeredItem || request.offeredItemId) ? (
        <div className="rounded-md p-3 text-sm ring-1 ring-border">
          <p className="label">Món mang đổi</p>
          <p className="truncate font-semibold">{offeredTitle ?? 'Không tải được chi tiết món'}</p>
          {offeredItem ? (
            <p className="text-xs text-text-secondary">{conditionLabel[offeredItem.condition]}</p>
          ) : null}
          {request.offeredItemId ? (
            <Button
              variant="ghost"
              size="sm"
              className="mt-1"
              onClick={() => navigate(`/items/${request.offeredItemId}`)}
            >
              Xem món mang đổi
            </Button>
          ) : null}
        </div>
      ) : null}
      <Button
        variant="outline"
        className="w-full"
        onClick={() => navigate(`/items/${request.itemId}?from=messages`)}
      >
        Xem bài đăng
      </Button>
    </section>
  );
}

/** Thẻ đối tác: tiêu đề là vai của người bên kia, có Nhắn tin, Xem hồ sơ và Báo cáo. */
export function PartnerCard({
  request,
  roleLabel,
  name,
  avatarUrl,
  partnerId,
  canReport,
  onReport,
}: {
  request: RequestResponse;
  roleLabel: string;
  name: string;
  avatarUrl?: string;
  partnerId?: number;
  canReport: boolean;
  onReport: () => void;
}) {
  const navigate = useNavigate();
  return (
    <section className="card space-y-4 p-5 sm:p-6" aria-labelledby="partner-title">
      <h2 id="partner-title" className="section-title">
        {roleLabel}
      </h2>
      <div className="flex items-center gap-3">
        <Avatar user={{ name, avatarInitials: avatarInitials(name), avatarUrl }} />
        <span className="min-w-0 truncate text-sm font-semibold">{name}</span>
      </div>
      <div className="flex flex-wrap gap-2">
        <Button onClick={() => navigate(requestConversationPath(request.id))}>Nhắn tin</Button>
        {partnerId !== undefined ? (
          <Button variant="outline" onClick={() => navigate(userProfilePath(partnerId))}>
            Xem hồ sơ
          </Button>
        ) : null}
        {canReport ? (
          <Button variant="ghost" onClick={onReport}>
            Báo cáo
          </Button>
        ) : null}
      </div>
    </section>
  );
}
