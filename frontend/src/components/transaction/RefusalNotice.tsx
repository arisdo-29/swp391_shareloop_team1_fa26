import type { ReactNode } from 'react';
import { Alert, Button } from '../ui';
import type { RequestDispute } from '../../services/requestApi';
import { isDisputeConcluded } from '../../utils/disputeView';

/**
 * Thông báo giao dịch đã huỷ vì một bên từ chối nhận (GN-07). Đây là báo cáo vi phạm, không phải khiếu nại: bên bị báo
 * cáo được gửi giải trình nhưng không bắt buộc và không có hạn. Dùng ở trang Tiến trình và khung chat.
 */
export function RefusalNotice({
  report,
  partnerName,
  onRespond,
  extra,
}: {
  report: RequestDispute;
  partnerName: string;
  onRespond: (report: RequestDispute) => void;
  // Nội dung thêm ở cuối (ví dụ đường dẫn "Xem tiến trình" trong khung chat)
  extra?: ReactNode;
}) {
  if (report.openedByMe) {
    return (
      <Alert tone="info">
        {isDisputeConcluded(report)
          ? 'Bạn đã từ chối nhận và gửi báo cáo. Admin đã xem xét báo cáo này.'
          : 'Bạn đã từ chối nhận và gửi báo cáo. Admin đang xem xét.'}{' '}
        {extra}
      </Alert>
    );
  }
  if (isDisputeConcluded(report)) {
    return (
      <Alert tone="info">
        Admin đã xem xét báo cáo từ chối nhận của {partnerName}. {extra}
      </Alert>
    );
  }
  return (
    <Alert tone="warning">
      <span className="font-semibold">
        {partnerName} đã từ chối nhận vì món không đúng mô tả.
      </span>{' '}
      Món của bạn đang tạm ẩn chờ Admin xem xét.
      {report.respondedAt ? ' Bạn đã gửi giải trình.' : null}
      <span className="mt-2 flex flex-wrap items-center gap-3">
        {report.canRespond ? (
          <Button size="sm" variant="warning" onClick={() => onRespond(report)}>
            Gửi giải trình
          </Button>
        ) : null}
        {extra}
      </span>
    </Alert>
  );
}
