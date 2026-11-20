import { Alert, Button, Icon } from '../ui';
import type { RequestDispute, RequestResponse } from '../../services/requestApi';
import type { DisputeResponse } from '../../services/reportApi';
import { formatDateTime, formatDeadline } from '../../utils/request';
import { completedStepCount, transactionSteps } from '../../utils/transactionSteps';
import {
  disputeConcludedText,
  disputeHeadline,
  disputeStatusLabel,
  formatRemaining,
  isDisputeConcluded,
  isResponseOverdue,
} from '../../utils/disputeView';
import { RefusalNotice } from './RefusalNotice';

type StepState = 'done' | 'current' | 'todo';

// Dòng phụ dưới mỗi bước: giờ hẹn ở Chốt lịch, giờ hoàn tất ở Hoàn tất, còn lại theo tình trạng bước.
function stepCaption(index: number, state: StepState, request: RequestResponse) {
  if (index === 1 && request.meetingTime) return `Hẹn ${formatDateTime(request.meetingTime)}`;
  if (index === 3 && request.completedAt) return formatDateTime(request.completedAt);
  if (state === 'done') return 'Đã xong';
  if (state === 'current') return 'Đang thực hiện';
  return 'Chưa tới';
}

/** Kết quả theo trạng thái. Tông màu khớp StatusBadge: đỏ cho huỷ hoặc không được chọn, vàng cho khiếu nại. */
function StatusResult({ request }: { request: RequestResponse }) {
  if (request.status === 'COMPLETED') {
    return (
      <Alert tone="success">
        Giao dịch đã hoàn tất
        {request.completedAt ? ` lúc ${formatDateTime(request.completedAt)}` : ''}.
      </Alert>
    );
  }
  if (request.status === 'REJECTED') {
    return <Alert tone="error">Chủ bài đã chọn người khác cho món này.</Alert>;
  }
  if (request.status === 'CANCELLED') {
    return (
      <Alert tone="error">
        {request.cancelReason
          ? `Yêu cầu này đã bị hủy. Lý do: ${request.cancelReason}`
          : 'Yêu cầu này đã bị hủy.'}
      </Alert>
    );
  }
  return null;
}

/** Khiếu nại do chính mình mở: chỉ thông tin, không có nút. */
function OpenedByMeNotice({ dispute, partnerName }: { dispute: RequestDispute; partnerName: string }) {
  return (
    <div className="space-y-2">
      <Alert tone="info">
        {disputeHeadline(dispute, partnerName)}{' '}
        {dispute.responseDueAt
          ? `${partnerName} có đến ${formatDeadline(dispute.responseDueAt)} để giải trình, sau đó Admin kết luận.`
          : 'Admin sẽ xem xét và kết luận.'}
      </Alert>
      <p className="text-xs font-semibold text-text-secondary">
        Trạng thái: {disputeStatusLabel(dispute)}
      </p>
    </div>
  );
}

/** Khiếu nại nhắm vào mình: nổi bật và cho giải trình ngay khi backend báo canRespond. */
function AgainstMeNotice({
  dispute,
  partnerName,
  onRespond,
}: {
  dispute: RequestDispute;
  partnerName: string;
  onRespond: (dispute: RequestDispute) => void;
}) {
  if (isDisputeConcluded(dispute)) {
    return <Alert tone="info">{disputeConcludedText(dispute, partnerName)}</Alert>;
  }
  if (dispute.respondedAt) {
    return (
      <Alert tone="success">
        Bạn đã giải trình lúc {formatDateTime(dispute.respondedAt)}. Admin sẽ xem xét và kết luận.
      </Alert>
    );
  }
  if (isResponseOverdue(dispute)) {
    return (
      <Alert tone="warning">
        Đã quá hạn giải trình, Admin sẽ kết luận dựa trên thông tin hiện có.
      </Alert>
    );
  }
  return (
    <div className="space-y-3">
      <Alert tone="warning">
        <span className="font-semibold">{disputeHeadline(dispute, partnerName)}</span>{' '}
        {dispute.responseDueAt
          ? `Bạn cần giải trình trước ${formatDeadline(dispute.responseDueAt)} (${formatRemaining(dispute.responseDueAt)}).`
          : 'Bạn cần giải trình để Admin có đủ thông tin.'}
      </Alert>
      {dispute.canRespond ? (
        <Button variant="warning" onClick={() => onRespond(dispute)}>
          Giải trình ngay
        </Button>
      ) : null}
    </div>
  );
}

function DisputeList({
  disputes,
  partnerName,
  onRespond,
}: {
  disputes: RequestDispute[];
  partnerName: string;
  onRespond: (dispute: RequestDispute) => void;
}) {
  return (
    <div className="space-y-3">
      {disputes.map((dispute) =>
        dispute.openedByMe ? (
          <OpenedByMeNotice key={dispute.id} dispute={dispute} partnerName={partnerName} />
        ) : (
          <AgainstMeNotice key={dispute.id} dispute={dispute} partnerName={partnerName} onRespond={onRespond} />
        ),
      )}
    </div>
  );
}

/**
 * Khối "Các bước". Ánh xạ trạng thái sang bước dùng completedStepCount có sẵn; cửa sổ khiếu nại dùng cờ
 * disputeExpired backend trả về, không tự so giờ. Khiếu nại hiện theo vai người xem (D-04).
 */
export function StepsCard({
  request,
  partnerName,
  onOpenDispute,
  onRespondDispute,
  disputeReceipt,
}: {
  request: RequestResponse;
  partnerName: string;
  onOpenDispute: () => void;
  onRespondDispute: (dispute: RequestDispute) => void;
  disputeReceipt: DisputeResponse | null;
}) {
  const done = completedStepCount(request.status);
  const disputes = request.disputes ?? [];
  const alreadyComplained = disputes.some((dispute) => dispute.openedByMe) || Boolean(disputeReceipt);
  const canDispute = request.status === 'COMPLETED' && !request.disputeExpired && !alreadyComplained;
  const refusalReport = request.refusalReport ?? null;
  const showHelp =
    request.status === 'COMPLETED' ||
    request.status === 'DISPUTED' ||
    disputes.length > 0 ||
    refusalReport !== null;

  return (
    <section className="card space-y-5 p-5 sm:p-6" aria-labelledby="steps-title">
      <h2 id="steps-title" className="section-title">
        Các bước
      </h2>
      {done >= 0 ? (
        <ol
          className="grid grid-cols-2 gap-4 sm:grid-cols-4"
          aria-label={`Đã xong ${Math.min(done, transactionSteps.length)} trên ${transactionSteps.length} bước`}
        >
          {transactionSteps.map((label, index) => {
            const state: StepState = index < done ? 'done' : index === done ? 'current' : 'todo';
            return (
              <li
                key={label}
                aria-current={state === 'current' ? 'step' : undefined}
                className="flex min-w-0 flex-col gap-3"
              >
                <span
                  className={`h-1.5 rounded-full ${state === 'done' ? 'bg-primary' : state === 'current' ? 'bg-secondary' : 'bg-border'}`}
                />
                <span className="flex min-w-0 items-start gap-2">
                  <span
                    className={`grid size-8 shrink-0 place-items-center rounded-md ${state === 'done' ? 'bg-primary text-white' : 'bg-primary-faint text-text-secondary'}`}
                  >
                    {state === 'done' ? (
                      <Icon name="check" className="size-4" weight="bold" />
                    ) : (
                      <span className="text-xs font-bold">{index + 1}</span>
                    )}
                  </span>
                  <span className="min-w-0">
                    <span className="block text-sm font-semibold">{label}</span>
                    <span className="block text-xs text-text-secondary">
                      {stepCaption(index, state, request)}
                    </span>
                  </span>
                </span>
              </li>
            );
          })}
        </ol>
      ) : null}

      <StatusResult request={request} />

      {showHelp && (
        <section
          className="space-y-3 rounded-lg border border-border bg-surface-container/40 p-4"
          aria-labelledby="transaction-help-title"
        >
          <h3 id="transaction-help-title" className="flex items-center gap-2 text-sm font-semibold">
            <Icon name="complaints" className="size-4 text-primary" />
            Hỗ trợ giao dịch
          </h3>
          {disputeReceipt ? (
            <div role="status" className="space-y-2">
              <Alert tone="success">Đã gửi khiếu nại giao dịch.</Alert>
              <p className="text-sm leading-6 text-text-secondary">
                {partnerName} có đến {formatDeadline(disputeReceipt.responseDueAt)} để giải trình, sau đó
                Admin kết luận và gửi kết quả qua Thông báo.
              </p>
            </div>
          ) : refusalReport ? (
            <RefusalNotice report={refusalReport} partnerName={partnerName} onRespond={onRespondDispute} />
          ) : disputes.length ? (
            <DisputeList disputes={disputes} partnerName={partnerName} onRespond={onRespondDispute} />
          ) : request.status === 'DISPUTED' ? (
            <div className="space-y-2 text-sm leading-6 text-text-secondary">
              <p className="font-semibold text-warning">Sự cố giao nhận, chờ Admin xác minh</p>
              {request.disputeResponseDueAt && (
                <p>Hạn giải trình ghi nhận: {formatDeadline(request.disputeResponseDueAt)}.</p>
              )}
              <p>Xem yêu cầu giải trình và cập nhật xử lý trong Thông báo.</p>
            </div>
          ) : null}
          {request.status === 'COMPLETED' && !disputeReceipt ? (
            <div className="space-y-2 text-sm leading-6 text-text-secondary">
              <p className="font-semibold">
                {request.disputeExpired
                  ? 'Đã hết hạn khiếu nại'
                  : request.complaintDeadline
                    ? `Hạn gửi khiếu nại: ${formatDeadline(request.complaintDeadline)}`
                    : 'Còn trong hạn khiếu nại'}
              </p>
              {canDispute && (
                <>
                  <p>Gửi khiếu nại nếu có vấn đề với món đồ hoặc việc giao nhận.</p>
                  <Button variant="warning" onClick={onOpenDispute}>
                    Khiếu nại giao dịch
                  </Button>
                </>
              )}
            </div>
          ) : null}
        </section>
      )}
    </section>
  );
}
