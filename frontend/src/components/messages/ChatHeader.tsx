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
    <article className="flex min-w-0 flex-1 flex-col rounded-md bg-white p-3 ring-1 ring-border/80">
      <p className="text-xs font-semibold text-primary">{label}</p>
      {title ? (
        <div className="mt-2 flex min-w-0 flex-1 items-start gap-3">
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
              className="size-20 rounded-md object-cover"
            />
          </button>
          <div className="flex min-w-0 flex-1 flex-col self-stretch">
            <button
              type="button"
              className="line-clamp-2 text-left text-sm font-bold text-text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
              disabled={!detailPath}
              onClick={openDetail}
            >
              {title}
            </button>
            {item ? (
              <p className="mt-1 text-xs font-medium text-text-secondary">
                {conditionLabel[item.condition]}
              </p>
            ) : null}
            <p className="mt-1 truncate text-xs text-text-secondary">
              Chủ sở hữu: {item?.donorName?.trim() || ownerFallback}
            </p>
            {detailPath ? (
              <Button className="mt-auto self-start" size="sm" onClick={openDetail}>
                Xem chi tiết
              </Button>
            ) : null}