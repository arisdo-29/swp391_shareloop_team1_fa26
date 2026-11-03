import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useAppSelector } from '../../app/hooks';
import { selectCurrentUser } from '../../app/store';
import { Alert, Button, EmptyState, LoadingState, StatusBadge } from '../../components/ui';
import { ReportDialog } from '../../components/ReportDialog';
import { StepsCard } from '../../components/transaction/StepsCard';
import { ScheduleCard } from '../../components/transaction/ScheduleCard';
import { HandoverCard } from '../../components/transaction/HandoverCard';
import { HandoverMediaCard } from '../../components/transaction/HandoverMediaCard';
import { ItemSideCard, PartnerCard } from '../../components/transaction/SideCards';
import { PendingCard } from '../../components/transaction/PendingCard';
import {
  CancelTransactionDialog,
  DisputeDialog,
  HandoverIssueDialog,
} from '../../components/transaction/TransactionDialogs';
import {
  conversationImage,
  conversationTitle,
  isDonorView,
  partnerAvatarUrl,
  partnerIdOf,
  partnerRoleLabel,
  requestParticipantName,
  type ApiConversation,
} from '../../components/messages/conversation';
import { toBackendUserId, type RequestResponse } from '../../services/requestApi';
import { canCancelTransaction, requestStatusLabel } from '../../utils/request';
import { requestConversationPath } from '../../utils/chatMessages';
import { useTransactionDetail } from './useTransactionDetail';

type OpenDialog = 'cancel' | 'issue' | 'dispute' | 'report' | null;

/** Trang Tiến trình giao dịch (route /messages/:conversationId/progress, conversationId là id Request). */
export function TransactionProgress() {
  const user = useAppSelector(selectCurrentUser);
  const currentUserId = toBackendUserId(user?.id);
  const { conversationId } = useParams();
  if (!currentUserId) {
    return (
      <div className="page-shell">
        <EmptyState title="Chưa đăng nhập" text="Vui lòng đăng nhập lại để xem giao dịch." />
      </div>
    );
  }
  return <TransactionProgressView requestId={Number(conversationId)} currentUserId={currentUserId} />;
}

function TransactionProgressView({
  requestId,
  currentUserId,
}: {
  requestId: number;
  currentUserId: number;
}) {
  const navigate = useNavigate();
  const { request, offeredItem, loading, error, applyUpdate, reload } = useTransactionDetail(
    requestId,
    currentUserId,
  );
  const [openDialog, setOpenDialog] = useState<OpenDialog>(null);
  const [notice, setNotice] = useState('');

  if (loading && !request) {
    return (
      <div className="page-shell">
        <LoadingState />
      </div>
    );
  }
  if (error === 'forbidden' || (!request && !error)) {
    return (
      <div className="page-shell">
        <EmptyState
          title="Không xem được giao dịch này"
          text="Chỉ hai bên của giao dịch mới xem được tiến trình. Giao dịch có thể không tồn tại."
          action={<Button onClick={() => navigate('/messages')}>Về trang Tin nhắn</Button>}
        />
      </div>
    );
  }
  if (!request) {
    return (
      <div className="page-shell">
        <Alert tone="error">Không tải được giao dịch. Vui lòng thử lại.</Alert>
        <Button className="mt-4" variant="outline" onClick={reload}>
          Thử tải lại
        </Button>
      </div>
    );
  }

  const conversation: ApiConversation = { request, offeredItem: offeredItem ?? undefined };
  const viewerIsDonor = isDonorView(conversation, currentUserId);
  const partnerName = requestParticipantName(conversation, currentUserId);
  const partnerId = partnerIdOf(conversation, currentUserId);
  const title = conversationTitle(conversation);
  const finishAction = (updated: RequestResponse, message: string) => {
    applyUpdate(updated);
    setOpenDialog(null);
    setNotice(message);
  };

  return (
    <div className="page-shell py-6 sm:py-8">
      <Button variant="ghost" onClick={() => navigate(requestConversationPath(request.id))}>
        Quay lại tin nhắn
      </Button>
      <div className="mb-6 mt-2 flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h1 className="page-title">Tiến trình giao dịch</h1>
          <p className="mt-1 truncate text-sm text-text-secondary">{title}</p>
        </div>
        <StatusBadge status={request.status} label={requestStatusLabel[request.status]} />
      </div>
      {notice ? (
        <div role="status" className="mb-4">
          <Alert tone="success">{notice}</Alert>
        </div>
      ) : null}

      <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_360px]">
        <div className="min-w-0 space-y-5">
          <StepsCard request={request} onOpenDispute={() => setOpenDialog('dispute')} />
          <PendingCard
            request={request}
            conversation={conversation}
            currentUserId={currentUserId}
            viewerIsDonor={viewerIsDonor}
            partnerName={partnerName}
            onUpdated={applyUpdate}
          />
          <ScheduleCard
            request={request}
            currentUserId={currentUserId}
            viewerIsDonor={viewerIsDonor}
            partnerName={partnerName}
            onUpdated={applyUpdate}
          />
          <HandoverCard
            request={request}
            currentUserId={currentUserId}
            viewerIsDonor={viewerIsDonor}
            partnerName={partnerName}
            onUpdated={applyUpdate}
            onOpenIssue={() => setOpenDialog('issue')}
            onNotice={setNotice}
          />
          <HandoverMediaCard request={request} />
          {canCancelTransaction(request.status) ? (
            <section className="space-y-2">
              <Button variant="ghost" onClick={() => setOpenDialog('cancel')}>
                Huỷ giao dịch
              </Button>
              <p className="text-xs leading-5 text-text-secondary">
                Cần nêu lý do. Bài đăng mở lại cho người khác, không mất phí đăng lần nữa.
              </p>
            </section>
          ) : null}
        </div>
        <aside className="min-w-0 space-y-5">
          <ItemSideCard
            request={request}
            title={title}
            image={conversationImage(conversation)}
            offeredItem={offeredItem}
          />
          <PartnerCard
            request={request}
            roleLabel={partnerRoleLabel(conversation, currentUserId)}
            name={partnerName}
            avatarUrl={partnerAvatarUrl(conversation, currentUserId)}
            partnerId={partnerId}
            canReport={partnerId !== undefined && partnerId !== currentUserId}
            onReport={() => setOpenDialog('report')}
          />
        </aside>
      </div>

      {openDialog === 'cancel' ? (
        <CancelTransactionDialog
          request={request}
          currentUserId={currentUserId}
          itemTitle={title}
          onClose={() => setOpenDialog(null)}
          onStale={reload}
          onCancelled={(updated) =>
            finishAction(updated, 'Đã huỷ giao dịch. Bài đăng được mở lại cho người khác.')
          }
        />
      ) : null}
      {openDialog === 'issue' ? (
        <HandoverIssueDialog
          request={request}
          currentUserId={currentUserId}
          itemTitle={title}
          partnerName={partnerName}
          viewerIsDonor={viewerIsDonor}
          onClose={() => setOpenDialog(null)}
          onStale={reload}
          onReported={(updated) =>
            finishAction(updated, 'Đã gửi báo cáo. Admin sẽ xem xét và báo kết quả cho hai bên.')
          }
        />
      ) : null}
      {openDialog === 'dispute' ? (
        <DisputeDialog
          request={request}
          itemTitle={title}
          onClose={() => setOpenDialog(null)}
          onSubmitted={(message) => {
            setOpenDialog(null);
            setNotice(message);
            reload();
          }}
        />
      ) : null}
      {openDialog === 'report' && partnerId !== undefined ? (
        <ReportDialog
          target={{ targetType: 'USER', targetUserId: partnerId, requestId: request.id }}
          contextLabel="Báo cáo người dùng trong yêu cầu này"
          onClose={() => setOpenDialog(null)}
          onSuccess={() => {
            setOpenDialog(null);
            setNotice('Báo cáo đã được gửi đến quản trị viên để xem xét.');
          }}
        />
      ) : null}
    </div>
  );
}
