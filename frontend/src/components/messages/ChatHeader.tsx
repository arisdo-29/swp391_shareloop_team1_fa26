import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Avatar, Button, ItemImage, StatusBadge, TypeBadge } from '../ui';
import { ReputationBadge } from '../ReputationBadge';
import { requestStatusLabel } from '../../utils/request';
import { transactionProgressPath, userProfilePath } from '../../utils/chatMessages';
import { conditionLabel } from '../../utils/formatting';
import { productDetailFromRequestPath } from '../../utils/productDetailNavigation';
import type { PinnedItemResponse } from '../../services/chatApi';
import type { ItemResponse } from '../../services/itemApi';
import { CompactProgress } from './CompactProgress';
import {
  avatarInitials,
  conversationImage,
  conversationTitle,
  itemPrimaryImage,
  isDonorView,
  partnerAvatarUrl,
  partnerIdOf,
  partnerRoleLabel,
  requestParticipantName,
  type ApiConversation,
} from './conversation';

interface ChatHeaderProps {
  conversation: ApiConversation;
  pinnedItems?: Array<Pick<PinnedItemResponse, 'id' | 'imageUrl'>>;
  currentUserId: number;
  canReport: boolean;
  onBack: () => void;
  onReport: () => void;
}

function SwapItemCard({
  label,
  item,
  fallbackTitle,
  itemId,
  ownerFallback,
  requestId,
  fallbackImage,
}: {
  label: string;
  item?: ItemResponse;
  fallbackTitle?: string | null;
  itemId?: number;
  ownerFallback: string;
  requestId: number;
  fallbackImage?: string | null;
}) {
  const navigate = useNavigate();
  const title = item?.title ?? fallbackTitle?.trim() ?? '';
  const detailPath = itemId ? productDetailFromRequestPath(itemId, requestId) : null;
  const openDetail = () => {
    if (detailPath) navigate(detailPath);
  };

  return (
    <article className="flex min-w-0 flex-1 flex-col rounded-md bg-white p-2 ring-1 ring-border/80">
      <p className="text-xs font-semibold text-primary">{label}</p>
      {title ? (
        <div className="mt-1 flex min-w-0 flex-1 items-start gap-2.5">
          <button
            type="button"
            className="shrink-0 rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            aria-label={`Mở chi tiết ${title}`}
            disabled={!detailPath}
            onClick={openDetail}
          >
            <ItemImage
              src={itemPrimaryImage(item, fallbackImage)}
              alt={title}
              className="size-12 rounded-md object-cover"
            />
          </button>
          <div className="flex min-w-0 flex-1 flex-col self-stretch">
            <button
              type="button"
              className="line-clamp-1 text-left text-xs font-bold text-text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
              disabled={!detailPath}
              onClick={openDetail}
            >
              {title}
            </button>
            <p className="truncate text-xs text-text-secondary">
              {item ? `${conditionLabel[item.condition]} · ` : ''}
              {item?.donorName?.trim() || ownerFallback}
            </p>
            {detailPath ? (
              <Button className="mt-1 self-start !min-h-7 !px-2 !py-0.5" size="sm" onClick={openDetail}>
                Xem chi tiết
              </Button>
            ) : null}
          </div>
        </div>
      ) : (
        <p className="mt-2 rounded-md bg-surface-low p-3 text-xs leading-5 text-text-muted">
          Chưa có thông tin món đồ đề xuất cho yêu cầu này.
        </p>
      )}
    </article>
  );
}

/** Đầu khung chat: người bên kia, vai, Xem hồ sơ, Báo cáo, rồi một thẻ món đồ kèm tiến trình thu gọn. */
export function ChatHeader({
  conversation,
  pinnedItems = [],
  currentUserId,
  canReport,
  onBack,
  onReport,
}: ChatHeaderProps) {
  const navigate = useNavigate();
  // Ẩn khung món đồ + tiến trình để vùng tin nhắn bên dưới rộng hơn.
  const [detailsHidden, setDetailsHidden] = useState(false);
  const { request } = conversation;
  const participantName = requestParticipantName(conversation, currentUserId);
  const partnerId = partnerIdOf(conversation, currentUserId);
  const viewerIsDonor = isDonorView(conversation, currentUserId);
  const title = conversationTitle(conversation);
  const isSwap = request.type === 'SWAP';
  const pinnedImageFor = (itemId?: number) =>
    itemId === undefined
      ? undefined
      : pinnedItems.find((pinnedItem) => pinnedItem.id === itemId)?.imageUrl;

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
          <Button
            variant="outline"
            size="sm"
            aria-expanded={!detailsHidden}
            onClick={() => setDetailsHidden((hidden) => !hidden)}
          >
            {detailsHidden ? 'Hiện món đồ' : 'Ẩn món đồ'}
          </Button>
          {canReport ? (
            <Button variant="ghost" size="sm" onClick={onReport}>
              Báo cáo
            </Button>
          ) : null}
        </div>
      </header>

      <div
        hidden={detailsHidden}
        className="mx-4 mb-2 rounded-md p-2 ring-1 ring-border/80"
      >
        {isSwap ? (
          <div className="grid min-w-0 gap-2 md:grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] md:items-stretch">
            <SwapItemCard
              label={viewerIsDonor ? 'Món đồ của bạn' : 'Món đồ của chủ bài'}
              item={conversation.item}
              fallbackTitle={request.itemTitle}
              itemId={request.itemId}
              fallbackImage={pinnedImageFor(request.itemId) ?? request.itemImage}
              ownerFallback={request.donorName?.trim() || 'Chủ bài đăng'}
              requestId={request.id}
            />
            <div
              aria-label="Trao đổi hai món đồ"
              className="mx-auto grid size-7 place-items-center self-center rounded-full bg-primary-faint text-sm font-bold text-primary"
            >
              ↔
            </div>
            <SwapItemCard
              label={viewerIsDonor ? 'Món đồ bạn sẽ nhận' : 'Món đồ bạn đề xuất'}
              item={conversation.offeredItem}
              fallbackTitle={request.offeredItemTitle}
              itemId={request.offeredItemId}
              fallbackImage={pinnedImageFor(request.offeredItemId)}
              ownerFallback={request.receiverName?.trim() || 'Người đề nghị trao đổi'}
              requestId={request.id}
            />
          </div>
        ) : (
          <button
            type="button"
            onClick={() => navigate(productDetailFromRequestPath(request.itemId, request.id))}
            className="flex min-h-11 min-w-0 w-full items-center gap-3 rounded-md text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            <ItemImage
              src={conversationImage(conversation)}
              alt={title}
              className="h-11 w-14 shrink-0 rounded-md object-cover"
            />
            <span className="min-w-0 flex-1">
              <strong className="block truncate text-sm">{title}</strong>
              <span className="mt-1 flex flex-wrap gap-1.5">
                <TypeBadge type="gift" />
                <StatusBadge status={request.status} label={requestStatusLabel[request.status]} />
              </span>
            </span>
          </button>
        )}
      </div>

      {/* Tiến trình tách riêng khỏi khung món đồ: ẩn món đồ vẫn thấy tiến trình. */}
      <div className="mx-4 mb-2 flex flex-wrap items-center justify-between gap-2 rounded-md p-2 ring-1 ring-border/80">
        <CompactProgress status={request.status} />
        <Button
          size="sm"
          className="!min-h-7 !px-2.5 !py-0.5"
          onClick={() => navigate(transactionProgressPath(request.id))}
        >
          Xem tiến trình
        </Button>
      </div>
    </div>
  );
}
