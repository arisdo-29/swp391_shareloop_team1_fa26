import { Alert, Button, Icon } from '../ui';
import type { RequestResponse } from '../../services/requestApi';
import type { DisputeResponse } from '../../services/reportApi';
import { formatDateTime, formatDeadline } from '../../utils/request';
import { completedStepCount, transactionSteps } from '../../utils/transactionSteps';

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

/**
 * Khối "Các bước". Ánh xạ trạng thái sang bước dùng completedStepCount có sẵn; cửa sổ khiếu nại dùng cờ
 * disputeExpired backend trả về, không tự so giờ.
 */
export function StepsCard({
  request,
  onOpenDispute,
  disputeReceipt,
}: {
  request: RequestResponse;
  onOpenDispute: () => void;
  disputeReceipt: DisputeResponse | null;
}) {
  const done = completedStepCount(request.status);
  const canDispute = request.status === 'COMPLETED' && !request.disputeExpired;

  return (
    <section className="card space-y-5 p-5 sm:p-6" aria-labelledby="steps-title">
      <h2 id="steps-title" className="section-title">
        Các bước
      </h2>
      <p className="transaction-status-caption">Tiến trình theo trạng thái giao dịch hiện tại.</p>
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
                    className={`grid size-8 shrink-0 place-items-center rounded-md ${state === 'done' ? 'bg-primary text-on-primary' : 'bg-primary-faint text-text-secondary'}`}
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

      {(request.status === 'COMPLETED' || request.status === 'DISPUTED') && (
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
              <Alert tone="success">Đã gửi khiếu nại #{disputeReceipt.id}</Alert>
              <p className="text-sm leading-6 text-text-secondary">
                Hạn giải trình của bên còn lại: {formatDeadline(disputeReceipt.responseDueAt)}. Quản
                trị viên sẽ xem xét và gửi kết quả qua Thông báo.
              </p>
            </div>
          ) : request.status === 'DISPUTED' ? (
            <div className="space-y-2 text-sm leading-6 text-text-secondary">
              <p className="font-semibold text-warning">Giao dịch đang có khiếu nại</p>
              {request.disputeResponseDueAt && (
                <p>Hạn giải trình ghi nhận: {formatDeadline(request.disputeResponseDueAt)}.</p>
              )}
              <p>Xem yêu cầu giải trình và cập nhật xử lý trong Thông báo.</p>
            </div>
          ) : (
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
                  <Button variant="outline" onClick={onOpenDispute}>
                    Khiếu nại giao dịch
                  </Button>
                </>
              )}
            </div>
          )}
        </section>
      )}
    </section>
  );
}
