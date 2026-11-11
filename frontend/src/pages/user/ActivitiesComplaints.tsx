import '../../components/complaints.css';
import { useEffect, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Alert, Button, EmptyState, SkeletonRows } from '../../components/ui';
import { reportApi, type MyReportSummary, type ReportTargetType } from '../../services/reportApi';
import { disputeReasonLabels } from '../../utils/disputeErrors';

type Tone = 'info' | 'warning' | 'success' | 'error' | 'neutral';

const toneClasses: Record<Tone, string> = {
  info: 'bg-info-soft text-info',
  warning: 'bg-warning-soft text-warning',
  success: 'bg-success-soft text-success',
  error: 'bg-error-soft text-error',
  neutral: 'bg-surface-container text-text-secondary',
};

const targetLabels: Record<ReportTargetType, string> = {
  ITEM: 'Bài đăng',
  USER: 'Người dùng',
  REQUEST: 'Giao dịch',
};

const formatDateTime = (value: string) => new Date(value).toLocaleString('vi-VN');

function reasonLabel(report: MyReportSummary) {
  // Khiếu nại giao dịch dùng mã NO_SHOW cho mọi sự cố giao nhận, giống trang Khiếu nại của Admin.
  if (report.targetType === 'REQUEST' && report.reason === 'NO_SHOW') return 'Sự cố giao nhận';
  return disputeReasonLabels[report.reason] ?? report.reason;
}

/** Nhãn cho hồ sơ nhắm vào tôi: ưu tiên báo việc cần làm (giải trình) trước kết quả. */
function againstMeStatus(report: MyReportSummary): [string, Tone] {
  if (report.status === 'RESOLVED') return ['Đã kết luận: có vi phạm', 'error'];
  if (report.status === 'DISMISSED') return ['Đã kết luận: không vi phạm', 'success'];
  if (report.responseWindowOpen) return ['Cần giải trình', 'warning'];
  if (report.respondedAt) return ['Đã giải trình, chờ kết luận', 'info'];
  return ['Quá hạn giải trình, chờ kết luận', 'neutral'];
}

function sentStatus(report: MyReportSummary): [string, Tone] {
  if (report.status === 'RESOLVED') return ['Có cơ sở, đã xử lý', 'success'];
  if (report.status === 'DISMISSED') return ['Không có cơ sở', 'neutral'];
  if (report.status === 'AWAITING_RESPONSE') return ['Chờ bên kia giải trình', 'info'];
  return ['Đang chờ xử lý', 'warning'];
}

/** Khiếu nại giao dịch giải trình ở /me/disputes, báo cáo bài và người dùng ở /me/reports (router.tsx). */
function responsePath(report: MyReportSummary) {
  return report.targetType === 'REQUEST' ? `/me/disputes/${report.id}` : `/me/reports/${report.id}`;
}

function targetTitle(report: MyReportSummary) {
  const fallback = report.targetType === 'USER' ? 'Tài khoản đã bị xoá' : 'Bài đăng đã bị xoá';
  return `${targetLabels[report.targetType]}: ${report.targetName ?? fallback}`;
}

function StatusChip({ status }: { status: [string, Tone] }) {
  const [label, tone] = status;
  return (
    <span
      className={`inline-flex items-center rounded-sm px-2 py-1 text-2xs font-bold ${toneClasses[tone]}`}
    >
      {label}
    </span>
  );
}

function ResolutionNote({ report }: { report: MyReportSummary }) {
  if (!report.resolvedAt) return null;
  return (
    <p className="mt-2 rounded-md bg-surface-low px-3 py-2 text-sm leading-6 text-text-secondary">
      <span className="font-semibold text-text-primary">Kết luận của quản trị viên: </span>
      {report.resolution?.trim() || 'Không có ghi chú.'}
      <span className="mt-0.5 block text-xs text-text-muted">
        {formatDateTime(report.resolvedAt)}
      </span>
    </p>
  );
}

function AgainstMeCard({ report }: { report: MyReportSummary }) {
  return (
    <li data-response-open={report.responseWindowOpen} className="complaint-list-card card p-4 sm:p-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <StatusChip status={againstMeStatus(report)} />
          <p className="mt-2 font-bold">{targetTitle(report)}</p>
          <p className="mt-1 text-sm text-text-secondary">Lý do: {reasonLabel(report)}</p>
          {report.responseDueAt ? (
            <p className="mt-1 text-sm text-text-secondary">
              Hạn giải trình:{' '}
              <span className="font-semibold">{formatDateTime(report.responseDueAt)}</span>
            </p>
          ) : null}
        </div>
        <div className="flex shrink-0 flex-col gap-2 sm:items-end">
          {/* Hết hạn vẫn mở được trang để xem lại ảnh, giải trình đã gửi và kết luận. */}
          <Link to={responsePath(report)}>
            <Button
              size="sm"
              variant={report.responseWindowOpen ? 'primary' : 'outline'}
              className="w-full sm:w-auto"
            >
              {report.responseWindowOpen ? 'Gửi giải trình' : 'Xem hồ sơ'}
            </Button>
          </Link>
          {/* Chỉ khiếu nại giao dịch mới có cuộc trò chuyện chung với đối phương để thương lượng. */}
          {report.requestId ? (
            <Link to={`/requests/${report.requestId}`}>
              <Button size="sm" variant="ghost" className="w-full sm:w-auto">
                Nhắn tin
              </Button>
            </Link>
          ) : null}
        </div>
      </div>
      <ResolutionNote report={report} />
    </li>
  );
}

function SentCard({ report }: { report: MyReportSummary }) {
  return (
    <li className="complaint-list-card card p-4 sm:p-5">
      <div className="flex flex-wrap items-center gap-2">
        <StatusChip status={sentStatus(report)} />
        <span className="text-xs text-text-muted">Gửi lúc {formatDateTime(report.createdAt)}</span>
      </div>
      <p className="mt-2 font-bold">{targetTitle(report)}</p>
      <p className="mt-1 text-sm text-text-secondary">Lý do: {reasonLabel(report)}</p>
      <ResolutionNote report={report} />
    </li>
  );
}

/**
 * Tab "Khiếu nại & Báo cáo" của trang Hoạt động: phần 1 là hồ sơ cần tôi giải trình, phần 2 là báo cáo tôi đã gửi.
 * Hai danh sách tải song song; một bên lỗi không làm mất bên còn lại.
 */
export function ActivitiesComplaints() {
  const [againstMe, setAgainstMe] = useState<MyReportSummary[] | null>(null);
  const [sent, setSent] = useState<MyReportSummary[] | null>(null);
  const [againstMeError, setAgainstMeError] = useState(false);
  const [sentError, setSentError] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    // AbortController huỷ request cũ khi rời tab hoặc bấm Thử lại, tránh ghi đè dữ liệu mới.
    const controller = new AbortController();
    setAgainstMeError(false);
    setSentError(false);
    reportApi
      .listAgainstMe(controller.signal)
      .then(setAgainstMe)
      .catch(() => {
        if (!controller.signal.aborted) setAgainstMeError(true);
      });
    reportApi
      .listMine(controller.signal)
      .then(setSent)
      .catch(() => {
        if (!controller.signal.aborted) setSentError(true);
      });
    return () => controller.abort();
  }, [reloadKey]);

  const retry = () => setReloadKey((key) => key + 1);
  const needResponseCount = againstMe?.filter((report) => report.responseWindowOpen).length ?? 0;

  return (
    <div className="member-complaints space-y-8">
      <section className="complaint-list-section" aria-labelledby="against-me-title">
        <h2 id="against-me-title" className="mb-1 text-lg font-bold">
          Cần tôi giải trình
          {needResponseCount ? (
            <span className="ml-2 text-sm font-semibold text-warning">
              {needResponseCount} hồ sơ đang chờ
            </span>
          ) : null}
        </h2>
        <p className="mb-3 text-sm text-text-muted">
          Báo cáo hoặc khiếu nại về bạn mà quản trị viên đã yêu cầu giải trình. Hãy gửi giải trình
          trước hạn.
        </p>
        <ReportList
          reports={againstMe}
          error={againstMeError}
          onRetry={retry}
          emptyTitle="Không có hồ sơ nào cần giải trình"
          emptyText="Khi có báo cáo hoặc khiếu nại cần bạn giải trình, hồ sơ sẽ hiện ở đây kèm hạn giải trình."
          renderItem={(report) => <AgainstMeCard key={report.id} report={report} />}
        />
      </section>
      <section className="complaint-list-section" aria-labelledby="sent-reports-title">
        <h2 id="sent-reports-title" className="mb-1 text-lg font-bold">
          Báo cáo & Khiếu nại đã gửi
        </h2>
        <p className="mb-3 text-sm text-text-muted">
          Theo dõi báo cáo bài đăng, người dùng và khiếu nại giao dịch bạn đã gửi.
        </p>
        <ReportList
          reports={sent}
          error={sentError}
          onRetry={retry}
          emptyTitle="Bạn chưa gửi báo cáo nào"
          emptyText="Báo cáo bài đăng, người dùng hoặc khiếu nại giao dịch bạn gửi sẽ hiện ở đây."
          renderItem={(report) => <SentCard key={report.id} report={report} />}
        />
      </section>
    </div>
  );
}

function ReportList({
  reports,
  error,
  onRetry,
  emptyTitle,
  emptyText,
  renderItem,
}: {
  reports: MyReportSummary[] | null;
  error: boolean;
  onRetry: () => void;
  emptyTitle: string;
  emptyText: string;
  renderItem: (report: MyReportSummary) => ReactNode;
}) {
  if (error) {
    return (
      <div className="space-y-3">
        <Alert tone="error">Không tải được danh sách hồ sơ.</Alert>
        <Button variant="outline" size="sm" onClick={onRetry}>
          Thử lại
        </Button>
      </div>
    );
  }
  if (!reports) return <SkeletonRows rows={2} label="Đang tải hồ sơ" />;
  if (!reports.length) return <EmptyState title={emptyTitle} text={emptyText} />;
  return <ul className="space-y-3">{reports.map(renderItem)}</ul>;
}
