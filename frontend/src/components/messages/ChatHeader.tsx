import { useNavigate } from 'react-router-dom';
import { Avatar, Button, ItemImage, StatusBadge, TypeBadge } from '../ui';
import { ReputationBadge } from '../ReputationBadge';
import { requestStatusLabel } from '../../utils/request';
import { transactionProgressPath, userProfilePath } from '../../utils/chatMessages';
import { CompactProgress } from './CompactProgress';
import {
  avatarInitials,
  conversationImage,
  conversationTitle,
  isDonorView,
  partnerAvatarUrl,
  partnerIdOf,
  partnerRoleLabel,
  requestParticipantName,
  type ApiConversation,
} from './conversation';

interface ChatHeaderProps {
  conversation: ApiConversation;
  currentUserId: number;
  canReport: boolean;
  onBack: () => void;
  onReport: () => void;
  // Mở bảng tiến trình cũ ở màn hẹp; bỏ khi trang Tiến trình giao dịch thay thế bảng này.
  onOpenDetails: () => void;
}

/** Đầu khung chat: người bên kia, vai, Xem hồ sơ, Báo cáo, rồi một thẻ món đồ kèm tiến trình thu gọn. */
export function ChatHeader({
  conversation,
  currentUserId,
  canReport,
  onBack,
  onReport,
  onOpenDetails,
}: ChatHeaderProps) {
  const navigate = useNavigate();
  const { request } = conversation;
  const participantName = requestParticipantName(conversation, currentUserId);
  const partnerId = partnerIdOf(conversation, currentUserId);
  const viewerIsDonor = isDonorView(conversation, currentUserId);
  const title = conversationTitle(conversation);

  return (
    <div className="shrink-0 border-b border-border">
      <header className="flex flex-wrap items-center gap-x-3 gap-y-2 px-4 py-3">
        <Button variant="outline" size="sm" className="lg:hidden" onClick={onBack}>
          Quay lại
        </Button>
        <Avatar
          user={{
            name: participantName,
            avatarInitials: avatarInitials(participantName),
            avatarUrl: partnerAvatarUrl(conversation, currentUserId),
          }}
        />
        <div className="min-w-0 flex-1">
          <h2 className="truncate text-base font-bold">{participantName}</h2>
          <p className="text-xs text-text-secondary">{partnerRoleLabel(conversation, currentUserId)}</p>
          {viewerIsDonor ? (
            <div className="mt-0.5">
              <ReputationBadge
                trustStars={request.receiverTrustStars ?? request.trustStars}
                rank={request.receiverRank ?? request.rank}
                asDescribedRate={request.receiverAsDescribedRate ?? request.asDescribedRate}
                compact
              />
            </div>
          ) : null}
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {partnerId !== undefined ? (
            <Button
              variant="outline"
              size="sm"
              onClick={() => navigate(userProfilePath(partnerId))}
            >
              Xem hồ sơ
            </Button>
          ) : null}
          {canReport ? (
            <Button variant="ghost" size="sm" onClick={onReport}>
              Báo cáo
            </Button>
          ) : null}
          <Button variant="ghost" size="sm" className="xl:hidden" onClick={onOpenDetails}>
            Thao tác
          </Button>
        </div>
      </header>

      <div className="mx-4 mb-3 flex flex-wrap items-center gap-3 rounded-md p-2.5 ring-1 ring-border/80">
        <button
          type="button"
          onClick={() => navigate(`/items/${request.itemId}`)}
          className="flex min-h-11 min-w-0 flex-1 basis-60 items-center gap-3 rounded-md text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        >
          <ItemImage
            src={conversationImage(conversation)}
            alt={title}
            className="h-11 w-14 shrink-0 rounded-md object-cover"
          />
          <span className="min-w-0 flex-1">
            <strong className="block truncate text-sm">{title}</strong>
            <span className="mt-1 flex flex-wrap gap-1.5">
              <TypeBadge type={request.type === 'SWAP' ? 'trade' : 'gift'} />
              <StatusBadge status={request.status} label={requestStatusLabel[request.status]} />
            </span>
          </span>
        </button>
        <div className="flex flex-wrap items-center gap-3">
          <CompactProgress status={request.status} />
          <Button size="sm" onClick={() => navigate(transactionProgressPath(request.id))}>
            Xem tiến trình
          </Button>
        </div>
      </div>
    </div>
  );
}
