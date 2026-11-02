import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Button, EmptyState, ItemImage, SearchField, SkeletonRows, StatusBadge } from '../ui';
import { isRequestUnread, requestStatusLabel } from '../../utils/request';
import { productDetailFromRequestPath } from '../../utils/productDetailNavigation';
import {
  conversationFilterLabels,
  matchesConversationFilter,
  matchesConversationQuery,
  type ConversationFilter,
} from '../../utils/conversationFilter';
import {
  conversationImage,
  conversationTitle,
  requestParticipantName,
  type ApiConversation,
} from './conversation';

const filters: ConversationFilter[] = ['all', 'unread', 'open', 'closed'];

interface ConversationListProps {
  conversations: ApiConversation[];
  currentUserId: number;
  selectedRequestId: number | null;
  loading: boolean;
  error: string;
  className?: string;
  onRetry: () => void;
  onOpen: (requestId: number) => void;
  canSelect: (conversation: ApiConversation) => boolean;
  selectSubmitting: boolean;
  onSelectCandidate: (requestId: number) => void;
}

/** Cột danh sách cuộc trò chuyện: tìm và lọc ngay ở trình duyệt vì API trả đủ mọi cuộc (docs/api/chat.md). */
export function ConversationList({
  conversations,
  currentUserId,
  selectedRequestId,
  loading,
  error,
  className = '',
  onRetry,
  onOpen,
  canSelect,
  selectSubmitting,
  onSelectCandidate,
}: ConversationListProps) {
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState<ConversationFilter>('all');

  // Cuộc đang mở coi như đã đọc, giống dấu chấm đỏ ở từng dòng.
  const isUnread = (conversation: ApiConversation) =>
    conversation.request.id !== selectedRequestId &&
    isRequestUnread(conversation.request.lastMessageAt, conversation.request.id);

  const visibleConversations = conversations.filter(
    (conversation) =>
      matchesConversationFilter(filter, conversation.request.status, isUnread(conversation)) &&
      matchesConversationQuery(query, [
        conversationTitle(conversation),
        requestParticipantName(conversation, currentUserId),
        requestStatusLabel[conversation.request.status],
      ]),
  );

  return (
    <aside
      aria-label="Cuộc trò chuyện"
      className={`${className} min-h-0 flex-col overflow-hidden rounded-lg bg-white ring-1 ring-border/80`}
    >
      <div className="space-y-3 px-4 pb-3 pt-4">
        <div className="flex items-baseline justify-between gap-3">
          <h1 className="text-xl font-bold">Tin nhắn</h1>
          <span className="text-xs text-text-secondary">{conversations.length} cuộc</span>
        </div>
        <SearchField
          type="search"
          aria-label="Tìm món hoặc người"
          placeholder="Tìm món hoặc người"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
        {/* Một hàng; màn hẹp thì cuộn ngang trong hàng tab, không rớt dòng. Đệm 2px để vòng focus không bị cắt. */}
        <div
          role="tablist"
          aria-label="Lọc cuộc trò chuyện"
          className="-mx-0.5 flex flex-nowrap gap-1.5 overflow-x-auto p-0.5"
        >
          {filters.map((value) => (
            <button
              key={value}
              type="button"
              role="tab"
              aria-selected={filter === value}
              onClick={() => setFilter(value)}
              className={`min-h-11 shrink-0 whitespace-nowrap rounded-md px-3 text-xs font-semibold transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${filter === value ? 'bg-primary text-white' : 'text-text-secondary ring-1 ring-border hover:bg-primary-faint'}`}
            >
              {conversationFilterLabels[value]}
            </button>
          ))}
        </div>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto px-2 pb-2">
        {loading ? (
          <SkeletonRows rows={5} label="Đang tải cuộc trò chuyện" />
        ) : error ? (
          <div className="space-y-3 p-3 text-sm text-error">
            <p>{error}</p>
            <Button variant="outline" onClick={onRetry}>
              Thử tải lại
            </Button>
          </div>
        ) : visibleConversations.length ? (
          visibleConversations.map((conversation) => (
            <ConversationRow
              key={conversation.request.id}
              conversation={conversation}
              currentUserId={currentUserId}
              active={conversation.request.id === selectedRequestId}
              unread={isUnread(conversation)}
              onOpen={onOpen}
              canSelect={canSelect(conversation)}
              selectSubmitting={selectSubmitting}
              onSelectCandidate={onSelectCandidate}
            />
          ))
        ) : (
          <div className="p-2">
            <EmptyState
              title={conversations.length ? 'Không có cuộc trò chuyện phù hợp' : 'Chưa có yêu cầu'}
              text={
                conversations.length
                  ? 'Thử đổi bộ lọc hoặc tìm bằng tên món, tên người khác.'
                  : 'Các yêu cầu nhận hoặc trao đổi món đồ sẽ xuất hiện tại đây.'
              }
            />
          </div>
        )}
      </div>
    </aside>
  );
}

function ConversationRow({
  conversation,
  currentUserId,
  active,
  unread,
  onOpen,
  canSelect,
  selectSubmitting,
  onSelectCandidate,
}: {
  conversation: ApiConversation;
  currentUserId: number;
  active: boolean;
  unread: boolean;
  onOpen: (requestId: number) => void;
  canSelect: boolean;
  selectSubmitting: boolean;
  onSelectCandidate: (requestId: number) => void;
}) {
  const { request } = conversation;
  const participantName = requestParticipantName(conversation, currentUserId);
  const shownAt = request.lastMessageAt ?? request.createdAt;
  const itemTitle = conversationTitle(conversation);

  return (
    <div
      className={`relative mb-1 h-[84px] overflow-hidden rounded-md last:mb-0 ${active ? 'bg-primary-faint' : 'bg-white hover:bg-primary-faint'}`}
    >
      <button
        type="button"
        aria-label={`Mở cuộc trò chuyện với ${participantName}`}
        aria-current={active ? 'true' : undefined}
        onClick={() => onOpen(request.id)}
        className="absolute inset-0 rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-primary"
      />
      <div className="pointer-events-none grid h-full min-w-0 grid-cols-[52px_minmax(0,1fr)] items-center gap-2.5 px-2 py-2 text-left">
        <Link
          to={productDetailFromRequestPath(request.itemId, request.id)}
          aria-label={`Mở chi tiết ${itemTitle}`}
          className="pointer-events-auto relative z-10 size-[52px] overflow-hidden rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        >
          <ItemImage
            src={conversationImage(conversation)}
            alt={itemTitle}
            loading="lazy"
            className="size-[52px] rounded-md object-cover"
          />
        </Link>
        <div className="min-w-0">
          <div className="flex min-w-0 items-center gap-2">
            {unread ? (
              <span
                aria-label="Có tin chưa đọc"
                className="size-2 shrink-0 rounded-full bg-error"
              />
            ) : null}
            <strong
              className={`min-w-0 flex-1 truncate text-sm ${unread ? 'font-bold' : 'font-semibold'}`}
            >
              {participantName}
            </strong>
            <span className="shrink-0 text-2xs text-text-secondary">
              {new Date(shownAt).toLocaleDateString('vi-VN')}
            </span>
          </div>
          <p className="mt-0.5 truncate text-xs leading-4 text-text-secondary">{itemTitle}</p>
          <div className="mt-1 flex min-w-0 items-center gap-1.5">
            <StatusBadge status={request.status} label={requestStatusLabel[request.status]} />
            {canSelect ? (
              <button
                type="button"
                disabled={selectSubmitting}
                onClick={() => onSelectCandidate(request.id)}
                className="pointer-events-auto relative z-10 ml-auto shrink-0 rounded-sm bg-primary px-2 py-1 text-2xs font-semibold text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary disabled:cursor-not-allowed disabled:opacity-40"
              >
                Chọn người này
              </button>
            ) : null}
          </div>
        </div>
      </div>
    </div>
  );
}
