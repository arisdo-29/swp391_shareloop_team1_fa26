import { useEffect } from 'react';
import { Avatar, Button } from '../ui';
import { DialogFrame } from '../Dialog';
import { ReputationBadge } from '../ReputationBadge';
import { avatarInitials, requestFallbackTitle, type ApiConversation } from './conversation';

export function SelectRequestDialog({
  conversation,
  participantName,
  submitting,
  error,
  onCancel,
  onConfirm,
}: {
  conversation: ApiConversation;
  participantName: string;
  submitting: boolean;
  error: string;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !submitting) onCancel();
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onCancel, submitting]);

  return (
    <DialogFrame labelledBy="select-request-title">
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
      <div className="mt-4 space-y-2 text-sm leading-6 text-text-secondary">
        <p>
          Bài đăng:{' '}
          <strong className="text-text-primary">
            {conversation.item?.title ?? conversation.request.itemTitle ?? 'Không xác định'}
          </strong>
        </p>
        <p>Gửi lúc: {new Date(conversation.request.createdAt).toLocaleString('vi-VN')}</p>
        {conversation.request.type === 'SWAP' ? (
          <p>
            Món đề xuất:{' '}
            <strong className="text-text-primary">
              {conversation.offeredItem?.title ??
                conversation.request.offeredItemTitle ??
                'Không xác định'}
            </strong>
          </p>
        ) : null}
      </div>
      <p className="mt-4 rounded-md bg-warning-soft p-3 text-xs leading-5 text-warning">
        Khi bạn xác nhận chọn người này để giao dịch, yêu cầu sẽ được chấp nhận và các yêu cầu đang
        chờ khác của cùng bài đăng sẽ tự động bị từ chối.
      </p>
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
