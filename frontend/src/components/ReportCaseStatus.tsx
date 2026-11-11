import type { ReportStatus } from '../services/reportApi';

const labels: Record<ReportStatus, string> = {
  OPEN: 'Chờ xử lý',
  AWAITING_RESPONSE: 'Chờ giải trình',
  RESOLVED: 'Đã chấp thuận',
  DISMISSED: 'Đã bác bỏ',
};

/** Chỉ trình bày trạng thái backend, không quyết định quyền hoặc hành động. */
export function ReportCaseStatus({ status }: { status?: ReportStatus }) {
  return status ? (
    <span className="complaint-case-status" data-status={status}>
      {labels[status]}
    </span>
  ) : null;
}
