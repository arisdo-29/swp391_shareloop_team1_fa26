import { Alert, Button, Icon } from '../ui';
import type { RequestResponse } from '../../services/requestApi';
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
  if (request.status === 'DISPUTED') {
    return (
      <Alert tone="warning">
        Giao dịch đang có khiếu nại. Admin sẽ xem xét và báo kết quả cho hai bên.
        {request.disputeResponseDueAt ? (
          <> chờ chủ bài giải trình trước {formatDeadline(request.disputeResponseDueAt)}</>
        ) : null}
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
}: {
  request: RequestResponse;
  onOpenDispute: () => void;
}) {
  const done = completedStepCount(request.status);
  const canDispute = request.status === 'COMPLETED' && !request.disputeExpired;

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

      {request.status === 'COMPLETED' ? (
        <div className="space-y-2 text-sm">
          <p>
            <span className="label inline">Khiếu nại: </span>
            {request.disputeExpired
              ? 'Đã hết hạn khiếu nại'
              : request.complaintDeadline
                ? `Còn khiếu nại được đến ${formatDeadline(request.complaintDeadline)}`
                : 'Còn trong hạn khiếu nại'}
          </p>
          {canDispute ? (
            <>
              <p className="text-xs text-text-secondary">
                Chỉ khiếu nại khi món đồ có vấn đề. Đối tác sẽ có 48 giờ để giải trình.
              </p>
              <Button variant="outline" onClick={onOpenDispute}>
                Khiếu nại giao dịch
              </Button>
            </>
          ) : null}
        </div>
      ) : null}
    </section>
  );
}
