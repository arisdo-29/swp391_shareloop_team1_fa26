import { Link } from 'react-router-dom';
import { Button, Icon, ItemImage, StatusBadge, TypeBadge } from '../ui';
import type { MessageResponse } from '../../services/chatApi';
import { PrivateImage } from '../PrivateImage';
import { messageOfferStatusLabel, SCHEDULE_SECTION_ID } from '../../utils/chatMessages';
import type { ChatMessageSide } from './conversation';

/**
 * Một tin trong khung chat. Tin hệ thống (chốt lịch, hoàn tất...) là dòng nhỏ giữa khung, kèm liên kết
 * "Xem chi tiết" sang trang Tiến trình giao dịch.
 */
export function ChatMessage({
  entry,
  side,
  progressPath,
  requestId,
  scheduleActor,
  onOpenItem,
  offerActions,
}: {
  entry: MessageResponse;
  side: ChatMessageSide;
  progressPath: string;
  // Id số của giao dịch: ảnh chat riêng tư chỉ đọc được qua /requests/{id}/media/{mediaId} (D2).
  requestId: number | string;
  scheduleActor?: { name: string; isViewer: boolean };
  onOpenItem: (itemId: number) => void;
  // Chỉ trang Tin nhắn truyền vào, cho chủ bài ở đề nghị đổi món đang chờ khi giao dịch còn thương lượng.
  offerActions?: { submitting: boolean; onAccept: () => void; onDecline: () => void };
}) {
  if (entry.moderation && entry.moderation !== 'PASS') {
    return (
      <div className="flex justify-center">
        <span className="max-w-xl rounded-md bg-error-soft px-3 py-2 text-center text-xs text-error">
          {entry.blockedReason || 'Tin nhắn này không vượt qua kiểm duyệt.'}
        </span>
      </div>
    );
  }
  if (entry.kind.toUpperCase() === 'SYSTEM') {
    return (
      <div className="flex flex-wrap items-center justify-center gap-x-1 text-center text-xs text-text-secondary">
        <span>{entry.content}</span>
        <Link
          to={progressPath}
          className="inline-flex min-h-11 items-center px-1 font-semibold text-primary underline-offset-2 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        >
          Xem chi tiết
        </Link>
      </div>
    );
  }
  const timestamp = new Date(entry.createdAt).toLocaleString('vi-VN', {
    hour: '2-digit',
    minute: '2-digit',
    day: '2-digit',
    month: '2-digit',
  });
  if (entry.kind === 'SCHEDULE_SET') {
    const isOwnNotice = scheduleActor?.isViewer ?? false;
    return (
      <div
        data-message-align={isOwnNotice ? 'right' : 'left'}
        className={`flex ${isOwnNotice ? 'justify-end' : 'justify-start'}`}
      >
        <div className="max-w-[90%] sm:max-w-[70%]">
          <Link
            to={`${progressPath}#${SCHEDULE_SECTION_ID}`}
            className={`group flex items-center gap-3 rounded-xl border border-border px-3 py-3 text-text-primary shadow-sm transition hover:border-primary hover:bg-surface-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 ${isOwnNotice ? 'bg-primary-soft' : 'bg-surface'}`}
          >
            <span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-primary-fixed text-primary">
              <Icon name="calendarCheck" className="size-5" weight="duotone" />
            </span>
            <span className="min-w-0 flex-1 break-words">
              <strong className="block text-sm leading-5">
                {isOwnNotice ? 'Bạn' : (scheduleActor?.name || 'Đối phương')} đã lưu lịch hẹn
              </strong>
              <span className="mt-0.5 block text-xs font-semibold text-link group-hover:underline">
                Xem lịch hẹn
              </span>
            </span>
            <Icon name="caretRight" className="ml-1 size-4 shrink-0 text-link" />
          </Link>
          <time dateTime={entry.createdAt} className={`mt-1 block px-1 text-2xs text-text-muted ${isOwnNotice ? 'text-right' : 'text-left'}`}>
            {timestamp}
          </time>
        </div>
      </div>
    );
  }
  const offerLabel = messageOfferStatusLabel(entry.offerStatus);
  const darkBubble = side === 'right';
  const alignment = darkBubble ? 'left' : 'right';
  return (
    <div
      data-message-side={side}
      data-message-align={alignment}
      className={`flex ${darkBubble ? 'justify-start' : 'justify-end'}`}
    >
      <div
        className={`max-w-[82%] rounded-lg px-3.5 py-2.5 text-sm leading-6 sm:max-w-[70%] ${darkBubble ? 'rounded-bl-sm bg-primary text-white' : 'rounded-br-sm bg-surface-low text-text-primary'}`}
      >
        {entry.itemCard ? (
          <button
            type="button"
            className="mb-2 flex w-full items-center gap-3 rounded-md bg-white p-2.5 text-left text-text-primary"
            onClick={() => onOpenItem(entry.itemCard!.id)}
          >
            <ItemImage
              src={entry.itemCard.imageUrl ?? undefined}
              alt={entry.itemCard.title}
              className="size-12 rounded-md object-cover"
            />
            <span className="min-w-0">
              <strong className="block break-words text-sm">{entry.itemCard.title}</strong>
              <span className="mt-1 flex flex-wrap gap-1.5">
                <TypeBadge type={entry.itemCard.offerType === 'GIVE' ? 'gift' : 'trade'} />
                <StatusBadge status={entry.itemCard.status} />
              </span>
            </span>
          </button>
        ) : null}
        {entry.mediaId ? (
          <div className="mb-2">
            <PrivateImage
              path={`/api/v1/requests/${requestId}/media/${entry.mediaId}`}
              alt="Ảnh trong cuộc trò chuyện"
              className="max-h-72 max-w-full rounded-md object-contain"
            />
          </div>
        ) : null}
        {entry.content ? <p className="whitespace-pre-wrap break-words">{entry.content}</p> : null}
        {offerLabel ? (
          <span
            className={`mt-1.5 inline-flex rounded-sm px-2 py-0.5 text-2xs font-semibold ${darkBubble ? 'bg-white/15 text-white' : 'bg-white text-primary'}`}
          >
            {offerLabel}
          </span>
        ) : null}
        {offerActions && entry.offerStatus === 'PENDING' ? (
          <div className="mt-2 flex flex-wrap gap-2">
            <Button size="sm" disabled={offerActions.submitting} onClick={offerActions.onAccept}>
              Đồng ý
            </Button>
            <Button
              size="sm"
              variant="outline"
              disabled={offerActions.submitting}
              onClick={offerActions.onDecline}
            >
              Từ chối
            </Button>
          </div>
        ) : null}
        <span className={`mt-1 block text-2xs ${darkBubble ? 'text-white/70' : 'text-text-muted'}`}>
          {timestamp}
        </span>
      </div>
    </div>
  );
}
