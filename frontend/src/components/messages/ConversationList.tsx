import { useState } from 'react';
import { Avatar, Button, EmptyState, SearchField, SkeletonRows, StatusBadge } from '../ui';
import { isRequestUnread, requestStatusLabel } from '../../utils/request';
import {
  conversationFilterLabels,
  matchesConversationFilter,
  matchesConversationQuery,
  type ConversationFilter,
} from '../../utils/conversationFilter';
import {
  avatarInitials,
  conversationTitle,
  partnerAvatarUrl,
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
              title={
                conversations.length ? 'Không có cuộc trò chuyện phù hợp' : 'Chưa có yêu cầu'
              }
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

  return (
    <div className={`rounded-md ${active ? 'bg-primary-soft' : 'hover:bg-primary-faint'}`}>
      <button
        type="button"
        aria-current={active ? 'true' : undefined}
        onClick={() => onOpen(request.id)}
        className="grid min-h-11 w-full grid-cols-[40px_minmax(0,1fr)] gap-3 p-2.5 text-left"
      >
        <span className="relative">
          <Avatar
            user={{
              name: participantName,
              avatarInitials: avatarInitials(participantName),
              avatarUrl: partnerAvatarUrl(conversation, currentUserId),
            }}
          />
          {unread ? (
            <span
              aria-label="Có tin chưa đọc"
              className="absolute -right-0.5 -top-0.5 size-2.5 rounded-full bg-error ring-2 ring-white"
            />
          ) : null}
        </span>
        <span className="min-w-0">
          <span className="flex items-baseline justify-between gap-2">
            <strong className={`truncate text-sm ${unread ? 'font-bold' : 'font-semibold'}`}>
              {participantName}
            </strong>
            <span className="shrink-0 text-2xs text-text-secondary">
              {new Date(shownAt).toLocaleDateString('vi-VN')}
            </span>
          </span>
          <span
            className={`mt-0.5 block truncate text-xs ${unread ? 'font-semibold text-text-primary' : 'text-text-secondary'}`}
          >
            {conversationTitle(conversation)}
          </span>
          <span className="mt-1 flex items-center gap-1.5">
            <StatusBadge status={request.status} label={requestStatusLabel[request.status]} />
            {unread ? (
              <span className="rounded-sm bg-error-soft px-2 py-1 text-2xs font-semibold text-error">
                Mới
              </span>
            ) : null}
          </span>
        </span>
      </button>
      {canSelect ? (
        <div className="px-2.5 pb-2.5 pl-[62px]">
          <Button
            size="sm"
            disabled={selectSubmitting}
            onClick={() => onSelectCandidate(request.id)}
          >
            Chọn người này
          </Button>
        </div>
      ) : null}
    </div>
  );
}
