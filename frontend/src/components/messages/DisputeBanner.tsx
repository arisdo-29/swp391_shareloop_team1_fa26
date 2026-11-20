import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Alert, Button } from '../ui';
import { DisputeResponseDialog } from '../DisputeResponseDialog';
import type { RequestDispute, RequestResponse } from '../../services/requestApi';
import { formatDeadline } from '../../utils/request';
import {
  formatRemaining,
  isDisputeConcluded,
  transactionDisputeReasonLabel,
} from '../../utils/disputeView';

interface DisputeBannerProps {
  request: RequestResponse;
  partnerName: string;
  progressPath: string;
  // Gọi sau khi gửi giải trình để trang tải lại giao dịch.
  onResponded: () => void;
}

/**
 * Banner khiếu nại trên ô soạn tin (D-04). Bên bị khiếu nại còn hạn thấy banner vàng có nút "Giải trình ngay", bên
 * khiếu nại thấy banner thông tin không có nút. Khiếu nại đã kết luận thì không hiện.
 */
export function DisputeBanner({ request, partnerName, progressPath, onResponded }: DisputeBannerProps) {
  const [responding, setResponding] = useState<RequestDispute | null>(null);
  const openDisputes = (request.disputes ?? []).filter((dispute) => !isDisputeConcluded(dispute));
  if (!openDisputes.length) return null;

  return (
    <div className="space-y-2">
      {openDisputes.map((dispute) => (
        <BannerRow
          key={dispute.id}
          dispute={dispute}
          partnerName={partnerName}
          progressPath={progressPath}
          onRespond={() => setResponding(dispute)}
        />
      ))}
      {responding ? (
        <DisputeResponseDialog
          disputeId={responding.id}
          reasonLabel={transactionDisputeReasonLabel(responding.reason)}
          deadlineText={
            responding.responseDueAt
              ? `Hạn giải trình: ${formatDeadline(responding.responseDueAt)}. Mỗi khiếu nại chỉ giải trình được một lần.`
              : undefined
          }
          onClose={() => setResponding(null)}
          onSubmitted={() => {
            setResponding(null);
            onResponded();
          }}
        />
      ) : null}
    </div>
  );
}

function BannerRow({
  dispute,
  partnerName,
  progressPath,
  onRespond,
}: {
  dispute: RequestDispute;
  partnerName: string;
  progressPath: string;
  onRespond: () => void;
}) {
  const reason = transactionDisputeReasonLabel(dispute.reason);
  const progressLink = (
    <Link to={progressPath} className="font-semibold text-primary hover:underline">
      Xem tiến trình
    </Link>
  );

  if (dispute.openedByMe) {
    return (
      <Alert tone="info">
        Bạn đã khiếu nại giao dịch này: {reason}. Admin sẽ kết luận sau khi {partnerName} giải trình hoặc hết
        hạn. {progressLink}
      </Alert>
    );
  }
  if (dispute.respondedAt) {
    return (
      <Alert tone="success">
        Bạn đã giải trình khiếu nại của {partnerName}. Admin sẽ xem xét và kết luận. {progressLink}
      </Alert>
    );
  }
  if (!dispute.canRespond) {
    return (
      <Alert tone="warning">
        Đã quá hạn giải trình, Admin sẽ kết luận dựa trên thông tin hiện có. {progressLink}
      </Alert>
    );
  }
  return (
    <Alert tone="warning">
      <span className="font-semibold">
        {partnerName} đã khiếu nại giao dịch này: {reason}.
      </span>{' '}
      {dispute.responseDueAt
        ? `Bạn cần giải trình trước ${formatDeadline(dispute.responseDueAt)} (${formatRemaining(dispute.responseDueAt)}).`
        : 'Bạn cần giải trình để Admin có đủ thông tin.'}
      <span className="mt-2 flex flex-wrap items-center gap-3">
        <Button size="sm" variant="warning" onClick={onRespond}>
          Giải trình ngay
        </Button>
        {progressLink}
      </span>
    </Alert>
  );
}
