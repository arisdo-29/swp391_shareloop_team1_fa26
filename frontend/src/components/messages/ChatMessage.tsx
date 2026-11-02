import { Link } from 'react-router-dom';
import { ItemImage, StatusBadge, TypeBadge } from '../ui';
import type { MessageResponse } from '../../services/chatApi';
import { resolveMediaUrl } from '../../services/mediaUrl';
import { messageOfferStatusLabel } from '../../utils/chatMessages';

/**
 * Một tin trong khung chat. Tin hệ thống (chốt lịch, hoàn tất...) là dòng nhỏ giữa khung, kèm liên kết
 * "Xem chi tiết" sang trang Tiến trình giao dịch.
 */
export function ChatMessage({
  entry,
  mine,
  progressPath,
  onOpenItem,
}: {
  entry: MessageResponse;
  mine: boolean;
  progressPath: string;
  onOpenItem: (itemId: number) => void;
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
  const offerLabel = messageOfferStatusLabel(entry.offerStatus);
  return (
    <div className={`flex ${mine ? 'justify-end' : 'justify-start'}`}>
      <div
        className={`max-w-[82%] rounded-lg px-3.5 py-2.5 text-sm leading-6 sm:max-w-[70%] ${mine ? 'rounded-br-sm bg-primary text-white' : 'rounded-bl-sm bg-surface-low text-text-primary'}`}
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
          <a
            href={resolveMediaUrl(`/api/v1/media/${entry.mediaId}`)}
            target="_blank"
            rel="noreferrer"
            className="mb-2 block"
          >
            <img
              src={resolveMediaUrl(`/api/v1/media/${entry.mediaId}`)}
              alt="Ảnh trong cuộc trò chuyện"
              className="max-h-72 max-w-full rounded-md object-contain"
            />
          </a>
        ) : null}
        {entry.content ? <p className="whitespace-pre-wrap break-words">{entry.content}</p> : null}
        {offerLabel ? (
          <span
            className={`mt-1.5 inline-flex rounded-sm px-2 py-0.5 text-2xs font-semibold ${mine ? 'bg-white/15 text-white' : 'bg-white text-primary'}`}
          >
            {offerLabel}
          </span>
        ) : null}
        <span className={`mt-1 block text-2xs ${mine ? 'text-white/70' : 'text-text-muted'}`}>
          {new Date(entry.createdAt).toLocaleString('vi-VN', {
            hour: '2-digit',
            minute: '2-digit',
            day: '2-digit',
            month: '2-digit',
          })}
        </span>
      </div>
    </div>
  );
}
