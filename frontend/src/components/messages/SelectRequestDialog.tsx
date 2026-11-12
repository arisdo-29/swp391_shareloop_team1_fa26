import { useEffect } from 'react';
import type { ItemResponse } from '../../services/itemApi';
import { Avatar, Button, ItemImage } from '../ui';
import { DialogFrame } from '../Dialog';
import { ReputationBadge } from '../ReputationBadge';
import { avatarInitials, requestFallbackTitle, type ApiConversation } from './conversation';
import { conditionLabel } from '../../utils/formatting';
import { toFeItemRequest } from '../../services/requestApi';
import { offerConversion, requestOfferLabel } from '../../utils/requestOffer';
import { ConversionTopUpNotice, OfferConversionWarning } from '../OfferConversionNotice';

export function SelectRequestDialog({
  conversation,
  participantName,
  submitting,
  error,
  needsTopUp = false,
  onCancel,
  onConfirm,
  onViewItem,
}: {
  conversation: ApiConversation;
  participantName: string;
  submitting: boolean;
  error: string;
  // Backend trả 402 khi chủ bài thiếu Credit để chuyển bài sang Trao đổi.
  needsTopUp?: boolean;
  onCancel: () => void;
  onConfirm: () => void;
  onViewItem?: (itemId: number) => void;
}) {
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !submitting) onCancel();
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onCancel, submitting]);

  const { request } = conversation;
  const itemTitle = conversation.item?.title ?? request.itemTitle?.trim() ?? 'Không xác định';
  const offeredTitle =
    conversation.offeredItem?.title ?? request.offeredItemTitle?.trim() ?? 'Không xác định';
  // Đổi sang ItemRequest để dùng chung luật chú thích và cảnh báo với trang Hoạt động.
  const feRequest = toFeItemRequest(request);
  const conversion = offerConversion(feRequest);
  // Yêu cầu có kèm món thì hiện 2 thẻ. Giao dịch SWAP luôn có món; nếu dữ liệu thiếu id vẫn hiện thẻ chờ.
  const showsTwoItems = requestOfferLabel(feRequest) === 'Trao đổi' || request.type === 'SWAP';

  return (
    <DialogFrame className="chat-action-dialog" labelledBy="select-request-title">
      <h2 id="select-request-title" className="text-xl font-bold">
        Xác nhận chọn người giao dịch
      </h2>
      <div className="mt-4 flex items-center gap-3 rounded-md bg-surface-low p-3">
        <Avatar
          user={{
            name: participantName,
            avatarInitials: avatarInitials(participantName),
            avatarUrl: undefined,
          }}
        />
        <div className="min-w-0 flex-1">
          <strong className="block truncate text-sm">{participantName}</strong>
          <div className="mt-1">
            <ReputationBadge
              trustStars={
                conversation.request.receiverTrustStars ?? conversation.request.trustStars
              }
              rank={conversation.request.receiverRank ?? conversation.request.rank}
              asDescribedRate={
                conversation.request.receiverAsDescribedRate ?? conversation.request.asDescribedRate
              }
            />
          </div>
          <p className="mt-1 text-xs text-text-muted">
            {requestFallbackTitle(conversation.request)}
          </p>
        </div>
      </div>
      <div className="mt-4 text-sm leading-6 text-text-secondary">
        <p>Gửi lúc: {new Date(request.createdAt).toLocaleString('vi-VN')}</p>
        {showsTwoItems ? (
          <div className="mt-3 grid items-stretch gap-3 sm:grid-cols-2">
            <SelectionItemCard
              label="Bài đăng"
              item={conversation.item}
              itemId={request.itemId}
              title={itemTitle}
              image={conversation.item?.images[0] ?? request.itemImage ?? undefined}
              onViewItem={onViewItem}
            />
            <SelectionItemCard
              label="Món đề xuất"
              item={conversation.offeredItem}
              itemId={request.offeredItemId}
              title={offeredTitle}
              image={conversation.offeredItem?.images[0]}
              onViewItem={onViewItem}
            />
          </div>
        ) : (
          <p className="mt-2">
            Bài đăng: <strong className="text-text-primary">{itemTitle}</strong>
          </p>
        )}
      </div>
      <p className="mt-4 rounded-md bg-warning-soft p-3 text-xs leading-5 text-warning">
        Khi bạn xác nhận chọn người này để giao dịch, yêu cầu sẽ được chấp nhận và các yêu cầu đang
        chờ khác của cùng bài đăng sẽ tự động bị từ chối.
      </p>
      <OfferConversionWarning conversion={conversion} />
      {needsTopUp ? (
        <ConversionTopUpNotice fee={conversion?.to === 'SWAP' ? conversion.fee : null} />
      ) : null}
      {error ? (
        <p role="alert" className="mt-4 rounded-md bg-error-soft p-3 text-sm text-error">
          {error}
        </p>
      ) : null}
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="ghost" autoFocus disabled={submitting} onClick={onCancel}>
          Hủy
        </Button>
        <Button disabled={submitting} onClick={onConfirm}>
          {submitting ? 'Đang chọn...' : 'Xác nhận chọn'}
        </Button>
      </div>
    </DialogFrame>
  );
}

function SelectionItemCard({
  label,
  item,
  itemId,
  title,
  image,
  onViewItem,
}: {
  label: string;
  item?: ItemResponse;
  itemId?: number;
  title: string;
  image?: string;
  onViewItem?: (itemId: number) => void;
}) {
  const canView = Boolean(itemId && onViewItem);
  const viewItem = () => {
    if (itemId && onViewItem) onViewItem(itemId);
  };

  return (
    <article
      aria-label={label}
      className="flex min-w-0 gap-3 rounded-md bg-surface p-3 ring-1 ring-border"
    >
      <button
        type="button"
        aria-label={`Xem chi tiết ${title}`}
        disabled={!canView}
        onClick={viewItem}
        className="size-20 shrink-0 overflow-hidden rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary disabled:cursor-default"
      >
        <ItemImage src={image} alt={title} className="size-20 object-cover" />
      </button>
      <div className="flex min-w-0 flex-1 flex-col">
        <p className="text-xs font-semibold text-primary">{label}</p>
        <button
          type="button"
          disabled={!canView}
          onClick={viewItem}
          className="mt-1 line-clamp-2 text-left text-sm font-semibold leading-5 text-text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary disabled:cursor-default"
        >
          {title}
        </button>
        <p className="mt-1 text-xs text-text-secondary">
          Tình trạng: {item ? conditionLabel[item.condition] : 'Không xác định'}
        </p>
        <Button
          variant="outline"
          size="sm"
          className="mt-2 w-full"
          disabled={!canView}
          onClick={viewItem}
        >
          Xem chi tiết
        </Button>
      </div>
    </article>
  );
}
