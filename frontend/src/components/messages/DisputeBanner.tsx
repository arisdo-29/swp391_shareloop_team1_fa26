import { useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Alert, Button } from '../ui';
import { DisputeResponseDialog } from '../DisputeResponseDialog';
import { RefusalNotice } from '../transaction/RefusalNotice';
import type { RequestDispute, RequestResponse } from '../../services/requestApi';
import { formatDeadline } from '../../utils/request';
import {
  disputeHeadline,
  formatRemaining,
  isDisputeConcluded,
  isHandoverIssue,
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
 * Banner trên ô soạn tin (D-04). Khiếu nại và sự cố giao nhận: bên bị khiếu nại còn hạn thấy banner vàng có nút
 * "Giải trình ngay", bên mở thấy banner thông tin. Giao dịch huỷ vì từ chối nhận (GN-07) hiện báo cáo vi phạm, bên bị
 * báo cáo có nút "Gửi giải trình" không bắt buộc. Khiếu nại hay báo cáo đã kết luận thì không hiện.
 */
export function DisputeBanner({ request, partnerName, progressPath, onResponded }: DisputeBannerProps) {
  const [responding, setResponding] = useState<RequestDispute | null>(null);
  const openDisputes = (request.disputes ?? []).filter((dispute) => !isDisputeConcluded(dispute));
  const refusalReport =
    request.refusalReport && !isDisputeConcluded(request.refusalReport) ? request.refusalReport : null;
  if (!openDisputes.length && !refusalReport) return null;

  const progressLink = (
    <Link to={progressPath} className="font-semibold text-primary hover:underline">
      Xem tiến trình
    </Link>
  );

  return (
    <div className="space-y-2">
      {refusalReport ? (
        <RefusalNotice
          report={refusalReport}
          partnerName={partnerName}
          onRespond={setResponding}
          extra={progressLink}
        />
      ) : null}
      {openDisputes.map((dispute) => (
        <BannerRow
          key={dispute.id}
          dispute={dispute}
          partnerName={partnerName}
          progressLink={progressLink}
          onRespond={() => setResponding(dispute)}
        />
      ))}
      {responding ? (
        <DisputeResponseDialog
          disputeId={responding.id}
          reasonLabel={transactionDisputeReasonLabel(responding.reason)}
          isReport={responding.id === refusalReport?.id}
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
  progressLink,
  onRespond,
}: {
  dispute: RequestDispute;
  partnerName: string;
  progressLink: ReactNode;
  onRespond: () => void;
}) {
  if (dispute.openedByMe) {
    const next = isHandoverIssue(dispute)
      ? `Admin sẽ xác minh sau khi ${partnerName} giải trình hoặc hết hạn.`
      : `Admin sẽ kết luận sau khi ${partnerName} giải trình hoặc hết hạn.`;
    return (
      <Alert tone="info">
        {disputeHeadline(dispute, partnerName)} {next} {progressLink}
      </Alert>
    );
  }
  if (dispute.respondedAt) {
    return (
      <Alert tone="success">
        Bạn đã gửi giải trình cho {partnerName}. Admin sẽ xem xét. {progressLink}
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
      <span className="font-semibold">{disputeHeadline(dispute, partnerName)}</span>{' '}
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
