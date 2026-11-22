import type { ApiConversation } from './conversation';

/**
 * Tóm tắt yêu cầu người gửi đã gửi, hiện khi chat chưa mở (đang chờ chọn hoặc không được chọn).
 * Bài không tải được thì dùng tên món backend đã kèm trong yêu cầu.
 */
export function RequestSummary({ conversation }: { conversation: ApiConversation }) {
  const { request } = conversation;
  const itemTitle = conversation.item?.title ?? request.itemTitle?.trim() ?? 'món đồ';
  const offeredTitle = conversation.offeredItem?.title ?? request.offeredItemTitle?.trim() ?? '';
  const hasOfferedItem = Boolean(request.offeredItemId);
  const sentAt = new Date(request.createdAt).toLocaleString('vi-VN');

  return (
    <div className="mx-4 mb-2 rounded-md bg-surface-low px-3 py-2 text-xs leading-5 text-text-secondary">
      <p>
        {hasOfferedItem ? (
          <>
            Bạn đã đề nghị đổi{' '}
            <strong className="text-text-primary">{offeredTitle || 'món của bạn'}</strong> lấy{' '}
            <strong className="text-text-primary">{itemTitle}</strong>
          </>
        ) : (
          <>
            Bạn đã xin nhận <strong className="text-text-primary">{itemTitle}</strong>
          </>
        )}
      </p>
      <p className="text-text-muted">Gửi lúc {sentAt}</p>
    </div>
  );
}
