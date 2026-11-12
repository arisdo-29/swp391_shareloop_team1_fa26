import { useEffect, useRef, useState, type ReactNode } from 'react';
import {
  Alert,
  Button,
  EmptyState,
  Icon,
  PageHeader,
  Select,
  SkeletonRows,
  TextArea,
} from '../../components/ui';
import { DialogFrame } from '../../components/Dialog';
import {
  reportApi,
  type AdminReportDetail,
  type AdminReportPage,
  type ReportDecision,
  type ReportStatus,
  type ReportTargetType,
  type ReportEvidence,
  type ReportResponse,
} from '../../services/reportApi';
import type { ApiError } from '../../services/http';
import { formatDeadline } from '../../utils/request';
import { disputeReasonLabels } from '../../utils/disputeErrors';
import { useRealtimeRefresh } from '../../utils/useRealtimeRefresh';

const statusLabels: Record<ReportStatus, string> = {
  OPEN: 'Chờ xử lý',
  AWAITING_RESPONSE: 'Chờ giải trình',
  RESOLVED: 'Đã chấp thuận',
  DISMISSED: 'Đã bác bỏ',
};
const targetLabels: Record<ReportTargetType, string> = {
  ITEM: 'Bài đăng',
  USER: 'Người dùng',
  REQUEST: 'Giao dịch',
};
const filters: { value: ReportStatus | ''; label: string }[] = [
  { value: '', label: 'Tất cả' },
  { value: 'OPEN', label: 'Chờ xử lý' },
  { value: 'AWAITING_RESPONSE', label: 'Chờ giải trình' },
  { value: 'RESOLVED', label: 'Đã chấp thuận' },
  { value: 'DISMISSED', label: 'Đã bác bỏ' },
];
const decisionLabels: Record<ReportDecision, string> = {
  UPHOLD: 'Chấp thuận · Có cơ sở',
  DISMISS: 'Bác bỏ · Không có cơ sở',
};
function reportReasonLabel(report: ReportResponse) {
  if (report.targetType === 'REQUEST' && report.reason === 'NO_SHOW') return 'Sự cố giao nhận';
  return disputeReasonLabels[report.reason] ?? report.reason;
}

const date = (value: string | null) =>
  value ? new Date(value).toLocaleString('vi-VN') : 'Chưa có';
const concluded = (status: ReportStatus) => status === 'RESOLVED' || status === 'DISMISSED';
const statusTones: Record<ReportStatus, string> = {
  OPEN: 'bg-warning-soft text-warning',
  AWAITING_RESPONSE: 'bg-info-soft text-info',
  RESOLVED: 'bg-success-soft text-success',
  DISMISSED: 'bg-surface-container text-text-secondary',
};

// Cùng kiểu StatusBadge trong ui.tsx nhưng theo bộ trạng thái của hồ sơ khiếu nại.
function ReportStatusBadge({ status }: { status: ReportStatus }) {
  return (
    <span
      className={`inline-flex whitespace-nowrap rounded-sm px-2 py-1 text-xs font-semibold ${statusTones[status]}`}
    >
      {statusLabels[status]}
    </span>
  );
}

function DetailRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid gap-0.5 sm:grid-cols-[9rem_1fr] sm:gap-3">
      <dt className="text-text-secondary">{label}</dt>
      <dd className="min-w-0 break-words text-text-primary">{children}</dd>
    </div>
  );
}

function reportedUserName(detail: AdminReportDetail) {
  return detail.reportedUser
    ? (detail.reportedUser.fullName ?? 'Người dùng')
    : 'Không còn xác định được';
}

// Hệ quả của kết luận đã lưu, khớp với xử lý ở backend (ReportService.conclude).
function consequences(detail: AdminReportDetail) {
  const upheld = detail.report.status === 'RESOLVED';
  const parts = [
    detail.starsDeducted > 0 ? `Trừ ${detail.starsDeducted} sao uy tín` : 'Không trừ sao',
  ];
  if (detail.report.targetType === 'ITEM') parts.push(upheld ? 'Ẩn bài đăng' : 'Không ẩn bài');
  if (detail.report.targetType === 'REQUEST') {
    parts.push(
      upheld
        ? 'Giao dịch đang tranh chấp (nếu có) bị huỷ'
        : 'Giao dịch đang tranh chấp (nếu có) trở về Hoàn tất',
    );
  }
  return parts.join(' · ');
}
function errorMessage(error: ApiError) {
  if (error.status === 401) return '';
  if (error.status === 403) return 'Tài khoản hiện tại không có quyền xử lý khiếu nại.';
  if (error.status === 404) return 'Hồ sơ khiếu nại không còn tồn tại.';
  return error.message || 'Không tải được khiếu nại. Vui lòng thử lại.';
}

function EvidenceImage({ reportId, evidence }: { reportId: number; evidence: ReportEvidence }) {
  const [source, setSource] = useState('');
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    if (!evidence.available || !evidence.url) return;
    const controller = new AbortController();
    let objectUrl = '';
    reportApi
      .evidence(reportId, evidence.id, controller.signal)
      .then((blob) => {
        if (controller.signal.aborted) return;
        objectUrl = URL.createObjectURL(blob);
        setSource(objectUrl);
      })
      .catch(() => {
        if (!controller.signal.aborted) setFailed(true);
      });
    return () => {
      controller.abort();
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [reportId, evidence.id, evidence.available, evidence.url]);
  return (
    <div className="rounded-md bg-background p-2">
      {failed || !evidence.available || !evidence.url ? (
        <p className="p-3 text-xs text-text-muted">Ảnh bằng chứng không còn khả dụng.</p>
      ) : source ? (
        <a href={source} target="_blank" rel="noreferrer">
          <img
            src={source}
            alt="Ảnh bằng chứng"
            className="max-h-48 w-full rounded-md object-contain"
            onError={() => setFailed(true)}
          />
        </a>
      ) : (
        <p className="p-3 text-xs">Đang tải ảnh bằng chứng…</p>
      )}
    </div>
  );
}

function EvidenceGallery({
  reportId,
  title,
  evidence,
}: {
  reportId: number;
  title: string;
  evidence: ReportEvidence[];
}) {
  return (
    <div>
      <p className="mb-2 font-semibold text-text-secondary">
        {title} ({evidence.length})
      </p>
      {evidence.length === 0 && (
        <p className="text-sm text-text-secondary">Không có ảnh đính kèm.</p>
      )}
      <div className="grid grid-cols-2 gap-2">
        {evidence.map((entry) => (
          <EvidenceImage
            key={`${reportId}-${entry.id}-${entry.available}`}
            reportId={reportId}
            evidence={entry}
          />
        ))}
      </div>
    </div>
  );
}

export function AdminComplaints() {
  const [status, setStatus] = useState<ReportStatus | ''>('');
  const [targetType, setTargetType] = useState<ReportTargetType | ''>('');
  const [page, setPage] = useState(0);
  const [reload, setReload] = useState(0);
  useRealtimeRefresh(['NOTIFICATION'], () => setReload((value) => value + 1));
  const [list, setList] = useState<AdminReportPage | null>(null);
  const [listLoading, setListLoading] = useState(true);
  const [listError, setListError] = useState('');
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const closeButton = useRef<HTMLButtonElement>(null);
  const opener = useRef<HTMLButtonElement>(null);
  const [detail, setDetail] = useState<AdminReportDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState('');
  const [decision, setDecision] = useState<ReportDecision | ''>('');
  const [note, setNote] = useState('');
  const [decisionStep, setDecisionStep] = useState<'review' | 'choose' | 'confirm'>('review');
  const [submitting, setSubmitting] = useState(false);
  const submittingRef = useRef(false);
  const [notice, setNotice] = useState('');
  const [noticeTone, setNoticeTone] = useState<'success' | 'warning'>('success');

  useEffect(() => {
    const controller = new AbortController();
    setListLoading(true);
    setListError('');
    reportApi
      .list(
        { page, size: 20, status: status || undefined, targetType: targetType || undefined },
        controller.signal,
      )
      .then((result) => {
        if (controller.signal.aborted) return;
        if (page > 0 && page >= result.totalPages) {
          setPage(Math.max(0, result.totalPages - 1));
          return;
        }
        setList(result);
      })
      .catch((error: ApiError) => {
        if (!controller.signal.aborted) setListError(errorMessage(error));
      })
      .finally(() => {
        if (!controller.signal.aborted) setListLoading(false);
      });
    return () => controller.abort();
  }, [page, status, targetType, reload]);

  useEffect(() => {
    if (selectedId === null) return;
    const controller = new AbortController();
    setDetail(null);
    setDetailLoading(true);
    setDetailError('');
    setDecisionStep('review');
    setNote('');
    setDecision('');
    reportApi
      .detail(selectedId, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) setDetail(result);
      })
      .catch((error: ApiError) => {
        if (!controller.signal.aborted) setDetailError(errorMessage(error));
      })
      .finally(() => {
        if (!controller.signal.aborted) setDetailLoading(false);
      });
    return () => controller.abort();
  }, [selectedId, reload]);

  useEffect(() => {
    if (selectedId === null) opener.current?.focus();
    else closeButton.current?.focus();
  }, [selectedId]);

  async function submit() {
    if (
      !detail ||
      concluded(detail.report.status) ||
      detail.responseWindowOpen ||
      submittingRef.current ||
      decisionStep !== 'confirm' ||
      !decision ||
      note.length > 500
    )
      return;
    submittingRef.current = true;
    setSubmitting(true);
    setDetailError('');
    try {
      await reportApi.conclude(detail.report.id, { decision, note: note.trim() });
      setNoticeTone('success');
      setNotice('Đã kết luận khiếu nại.');
      setDetail(null);
      setReload((value) => value + 1);
    } catch (error) {
      const failure = error as ApiError;
      if (failure.code === 'REPORT_RESPONSE_WINDOW_OPEN') {
        // Dữ liệu trên trang đã cũ (mở hồ sơ trước khi nó chuyển sang chờ giải trình): tải lại để thấy hạn giải trình
        setNoticeTone('warning');
        setNotice('Bên bị báo cáo vẫn đang trong hạn giải trình, chưa thể kết luận.');
        setDetail(null);
        setReload((value) => value + 1);
      } else if (failure.status === 409) {
        setNoticeTone('warning');
        setNotice('Hồ sơ đã được quản trị viên khác xử lý. Đang tải lại trạng thái.');
        setDetail(null);
        setReload((value) => value + 1);
      } else {
        setDetailError(errorMessage(failure));
        if (failure.status === 404 || failure.status === 403 || failure.status === 401)
          setDetail(null);
      }
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  }

  // D-07, BC-03: báo cáo bài hoặc người dùng đang Chờ xử lý, chưa có giải trình thì Admin yêu cầu giải trình được.
  // Khiếu nại giao dịch tự chờ giải trình lúc mở nên không có nút này. Backend kiểm lại.
  const canRequestResponse =
    !!detail &&
    detail.report.status === 'OPEN' &&
    detail.report.targetType !== 'REQUEST' &&
    !detail.response;

  async function requestResponse() {
    if (!detail || !canRequestResponse || submittingRef.current) return;
    submittingRef.current = true;
    setSubmitting(true);
    setDetailError('');
    try {
      await reportApi.requestResponse(detail.report.id);
      setNoticeTone('success');
      setNotice('Đã gửi yêu cầu giải trình. Xem hạn phản hồi trong hồ sơ.');
      setDetail(null);
      setReload((value) => value + 1);
    } catch (error) {
      const failure = error as ApiError;
      if (failure.status === 409) {
        setNoticeTone('warning');
        setNotice('Hồ sơ không còn ở trạng thái Chờ xử lý. Đang tải lại trạng thái.');
        setDetail(null);
        setReload((value) => value + 1);
      } else {
        setDetailError(errorMessage(failure));
        if (failure.status === 404 || failure.status === 403 || failure.status === 401)
          setDetail(null);
      }
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  }

  const closeDetail = () => setSelectedId(null);
  const title = detail?.report.targetName;
  const detailIsConcluded = detail && concluded(detail.report.status);

  return (
    <section>
      <PageHeader
        title="Khiếu nại và báo cáo"
        description="Xem xét sự việc, đối chiếu minh chứng và kết luận từng hồ sơ."
      />
      {notice && selectedId === null && (
        <div className="mb-4" role="status">
          <Alert tone={noticeTone}>{notice}</Alert>
        </div>
      )}
      <div className="mb-4 space-y-3 rounded-lg border border-border bg-surface p-4">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div className="min-w-0">
            <p className="label">Trạng thái</p>
            <div className="flex flex-wrap gap-2" role="group" aria-label="Lọc trạng thái">
              {filters.map(({ value, label }) => (
                <Button
                  key={value}
                  size="sm"
                  variant={status === value ? 'primary' : 'outline'}
                  aria-pressed={status === value}
                  disabled={submitting}
                  onClick={() => {
                    setStatus(value);
                    setPage(0);
                    closeDetail();
                  }}
                >
                  {label}
                </Button>
              ))}
            </div>
          </div>
          <Select
            label="Loại hồ sơ"
            value={targetType}
            disabled={submitting}
            onChange={(event) => {
              setTargetType(event.target.value as ReportTargetType | '');
              setPage(0);
              closeDetail();
            }}
          >
            <option value="">Tất cả loại hồ sơ</option>
            <option value="REQUEST">Khiếu nại giao dịch</option>
            <option value="ITEM">Báo cáo bài đăng</option>
            <option value="USER">Báo cáo người dùng</option>
          </Select>
        </div>
        {status === 'AWAITING_RESPONSE' && (
          <p className="text-sm text-text-secondary">
            Mở hồ sơ để xem hạn giải trình. Hồ sơ đã quá hạn có thể được kết luận.
          </p>
        )}
      </div>
      {listLoading ? (
        <SkeletonRows label="Đang tải khiếu nại" />
      ) : listError ? (
        <div role="alert" className="space-y-3">
          <Alert tone="warning">{listError}</Alert>
          <Button onClick={() => setReload((value) => value + 1)}>Thử lại</Button>
        </div>
      ) : list?.items.length ? (
        <>
          <ul
            aria-label="Danh sách hồ sơ"
            className="divide-y divide-border rounded-lg border border-border bg-surface"
          >
            {list.items.map((report) => (
              <li
                key={report.id}
                className="grid gap-3 p-4 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center"
              >
                <div className="min-w-0 space-y-1">
                  <p className="flex flex-wrap items-center gap-x-2 text-xs font-semibold text-text-secondary">
                    <span>{targetLabels[report.targetType]}</span>
                  </p>
                  <h2
                    id={`report-row-${report.id}`}
                    className="break-words text-base font-semibold"
                  >
                    {report.targetName || 'Đối tượng không còn khả dụng'}
                  </h2>
                  <p className="break-words text-sm text-text-secondary">
                    {reportReasonLabel(report)}
                  </p>
                  <p className="break-words text-xs leading-5 text-text-secondary">
                    {report.reporterName || 'Người dùng đã xóa'} · {date(report.createdAt)}
                  </p>
                </div>
                <div className="flex flex-wrap items-center justify-between gap-3 sm:justify-end">
                  <ReportStatusBadge status={report.status} />
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={submitting}
                    aria-describedby={`report-row-${report.id}`}
                    onClick={(event) => {
                      opener.current = event.currentTarget;
                      setNotice('');
                      setDetail(null);
                      setDetailError('');
                      setSelectedId(report.id);
                    }}
                  >
                    Xem chi tiết
                    <Icon name="caretRight" className="size-4" />
                  </Button>
                </div>
              </li>
            ))}
          </ul>
          <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
            <span className="text-sm text-text-secondary">
              {list.totalElements} hồ sơ · Trang {page + 1}/{list.totalPages}
            </span>
            <div className="flex gap-2">
              <Button
                variant="outline"
                disabled={page === 0 || submitting}
                onClick={() => setPage((value) => value - 1)}
              >
                Trước
              </Button>
              <Button
                variant="outline"
                disabled={page + 1 >= list.totalPages || submitting}
                onClick={() => setPage((value) => value + 1)}
              >
                Sau
              </Button>
            </div>
          </div>
        </>
      ) : (
        <EmptyState title="Không có hồ sơ" text="Không có hồ sơ phù hợp với bộ lọc hiện tại." />
      )}
      {selectedId !== null && (
        <DialogFrame
          labelledBy="report-detail-title"
          size="xl"
          className="md:max-w-5xl"
          busy={submitting}
          onClose={closeDetail}
        >
          <header className="mb-5 flex items-start justify-between gap-3 border-b border-border pb-4">
            <div className="min-w-0">
              <p className="mb-1 text-xs font-semibold text-text-secondary">
                Chi tiết hồ sơ
                {detail ? ` · ${targetLabels[detail.report.targetType]}` : ''}
              </p>
              <div className="flex flex-wrap items-center gap-2">
                <h2 id="report-detail-title" className="section-title break-words">
                  {title || 'Hồ sơ khiếu nại'}
                </h2>
                {detail && <ReportStatusBadge status={detail.report.status} />}
              </div>
              {detail && (
                <p className="mt-1 text-sm text-text-secondary">
                  Gửi lúc {date(detail.report.createdAt)}
                </p>
              )}
            </div>
            <button
              ref={closeButton}
              type="button"
              aria-label="Đóng hồ sơ"
              disabled={submitting}
              onClick={closeDetail}
              className="grid size-11 shrink-0 place-items-center rounded-md border border-border text-text-primary hover:bg-primary-faint disabled:opacity-40"
            >
              <Icon name="close" className="size-5" />
            </button>
          </header>
          {notice && (
            <div className="mb-4" role="status">
              <Alert tone={noticeTone}>{notice}</Alert>
            </div>
          )}
          {detailLoading && <SkeletonRows label="Đang tải hồ sơ" />}
          {detailError && (
            <div role="alert" className="mb-4 space-y-2">
              <Alert tone="warning">{detailError}</Alert>
              {!detail && (
                <Button onClick={() => setReload((value) => value + 1)}>Thử tải lại</Button>
              )}
            </div>
          )}
          {detail && (
            <div className="space-y-4 text-sm">
              {detail.responseWindowOpen && (
                <Alert tone="info">
                  {detail.responseDueAt
                    ? `Hạn giải trình: ${formatDeadline(detail.responseDueAt)}. `
                    : ''}
                  Chỉ kết luận được khi bên bị báo cáo đã giải trình hoặc đã hết hạn.
                </Alert>
              )}
              <dl className="grid gap-3 rounded-lg bg-surface-container/50 p-4 sm:grid-cols-2">
                <div className="min-w-0">
                  <dt className="label">Người gửi</dt>
                  <dd className="break-words font-semibold">
                    {detail.reporter.fullName ?? 'Người dùng'}
                  </dd>
                </div>
                <div className="min-w-0">
                  <dt className="label">Người bị báo cáo</dt>
                  <dd className="break-words font-semibold">{reportedUserName(detail)}</dd>
                </div>
                {detail.relatedItemId !== null && (
                  <div className="min-w-0">
                    <dt className="label">Bài đăng liên quan</dt>
                    <dd className="break-words">
                      {detail.relatedItemTitle ?? 'Bài đăng đã bị xóa'}
                    </dd>
                  </div>
                )}
              </dl>
              <div className="grid gap-4 md:grid-cols-2">
                <section className="min-w-0 space-y-3 rounded-lg border border-border p-4">
                  <h3 className="font-semibold">Nội dung người gửi</h3>
                  <p className="font-semibold text-primary">{reportReasonLabel(detail.report)}</p>
                  <p className="whitespace-pre-wrap break-words leading-6">
                    {detail.report.description}
                  </p>
                  <EvidenceGallery
                    reportId={detail.report.id}
                    title="Ảnh người gửi"
                    evidence={detail.evidence}
                  />
                </section>
                <section className="min-w-0 space-y-3 rounded-lg border border-border p-4">
                  <h3 className="font-semibold">Giải trình của bên bị báo cáo</h3>
                  {detail.response ? (
                    <>
                      <p className="whitespace-pre-wrap break-words leading-6">{detail.response}</p>
                      {detail.respondedAt && (
                        <p className="text-xs text-text-secondary">
                          Gửi lúc {date(detail.respondedAt)}
                        </p>
                      )}
                      <EvidenceGallery
                        reportId={detail.report.id}
                        title="Ảnh giải trình"
                        evidence={detail.responseEvidence}
                      />
                    </>
                  ) : (
                    <p className="text-text-secondary">Chưa có giải trình.</p>
                  )}
                </section>
              </div>
              {detailIsConcluded ? (
                <section className="rounded-lg border border-border bg-surface-container/50 p-4">
                  <h3 className="mb-3 font-semibold">Kết quả xử lý</h3>
                  <dl className="space-y-2">
                    <DetailRow label="Kết luận">
                      {detail.report.status === 'RESOLVED' ? 'Có cơ sở' : 'Không có cơ sở'}
                    </DetailRow>
                    <DetailRow
                      label={
                        detail.report.status === 'RESOLVED' ? 'Người bị xử lý' : 'Người bị báo cáo'
                      }
                    >
                      {reportedUserName(detail)}
                    </DetailRow>
                    <DetailRow label="Hệ quả">{consequences(detail)}</DetailRow>
                    {detail.resolution?.trim() && (
                      <DetailRow label="Ghi chú">
                        <span className="whitespace-pre-wrap">{detail.resolution}</span>
                      </DetailRow>
                    )}
                  </dl>
                  <p className="mt-3 border-t border-border pt-3 text-text-secondary">
                    {detail.resolvedByName ?? 'Quản trị viên'} kết luận lúc{' '}
                    {date(detail.resolvedAt)}
                  </p>
                </section>
              ) : (
                <section className="space-y-4 rounded-lg border border-border bg-surface-container/50 p-4">
                  <h3 className="font-semibold">Bước tiếp theo</h3>
                  {detail.responseWindowOpen ? (
                    <p className="text-text-secondary">
                      Cập nhật hồ sơ để kiểm tra xem bên bị báo cáo đã gửi giải trình chưa.
                    </p>
                  ) : detail.report.status === 'AWAITING_RESPONSE' ? (
                    <Alert tone="warning">
                      {detail.responseDueAt && detail.responseWindowOpen === false
                        ? `Đã hết hạn giải trình lúc ${formatDeadline(detail.responseDueAt)}.`
                        : 'Hồ sơ đang chờ giải trình. Kiểm tra lại trạng thái trước khi kết luận.'}
                    </Alert>
                  ) : (
                    <p className="text-text-secondary">
                      {canRequestResponse
                        ? 'Yêu cầu bên bị báo cáo giải trình nếu cần thêm thông tin.'
                        : 'Đối chiếu thông tin từ hai bên trước khi đưa ra kết luận.'}
                    </p>
                  )}
                  {decisionStep === 'review' && (
                    <div className="flex flex-wrap gap-2">
                      {canRequestResponse && (
                        <Button disabled={submitting} onClick={() => void requestResponse()}>
                          Yêu cầu giải trình
                        </Button>
                      )}
                      <Button
                        variant={canRequestResponse ? 'outline' : 'primary'}
                        disabled={submitting || detail.responseWindowOpen}
                        onClick={() => setDecisionStep('choose')}
                      >
                        Kết luận
                      </Button>
                      {detail.responseWindowOpen && (
                        <Button variant="outline" onClick={() => setReload((value) => value + 1)}>
                          Cập nhật hồ sơ
                        </Button>
                      )}
                    </div>
                  )}
                  {decisionStep === 'choose' && (
                    <>
                      <fieldset className="grid gap-2 sm:grid-cols-2">
                        <legend className="label">Chọn kết luận</legend>
                        {(Object.keys(decisionLabels) as ReportDecision[]).map((value) => (
                          <label
                            key={value}
                            className={`flex cursor-pointer items-center gap-2 rounded-md border p-3 font-semibold ${decision === value ? 'border-primary bg-primary-faint' : 'border-border bg-surface'}`}
                          >
                            <input
                              type="radio"
                              name="report-decision"
                              value={value}
                              checked={decision === value}
                              onChange={() => setDecision(value)}
                              className="accent-primary"
                            />
                            {decisionLabels[value]}
                          </label>
                        ))}
                      </fieldset>
                      <TextArea
                        label="Ghi chú kết luận (không bắt buộc)"
                        hint="Tối đa 500 ký tự."
                        value={note}
                        maxLength={500}
                        onChange={(event) => setNote(event.target.value)}
                      />
                      <div className="flex flex-wrap gap-2">
                        <Button variant="outline" onClick={() => setDecisionStep('review')}>
                          Quay lại
                        </Button>
                        <Button disabled={!decision} onClick={() => setDecisionStep('confirm')}>
                          Kiểm tra kết luận
                        </Button>
                      </div>
                    </>
                  )}
                  {decisionStep === 'confirm' && decision && (
                    <div className="space-y-3 rounded-md border border-border bg-surface p-4">
                      <p className="font-semibold">{decisionLabels[decision]}</p>
                      <p>
                        {decision === 'UPHOLD'
                          ? 'Hệ thống áp dụng chế tài theo loại hồ sơ và quy định hiện hành.'
                          : 'Hệ thống đóng hồ sơ và giữ nguyên sao uy tín.'}
                      </p>
                      {note.trim() && (
                        <p className="whitespace-pre-wrap break-words text-text-secondary">
                          {note.trim()}
                        </p>
                      )}
                      <div className="flex flex-wrap gap-2">
                        <Button
                          variant="outline"
                          disabled={submitting}
                          onClick={() => setDecisionStep('choose')}
                        >
                          Quay lại
                        </Button>
                        <Button disabled={submitting} onClick={() => void submit()}>
                          {submitting ? 'Đang gửi…' : 'Xác nhận gửi'}
                        </Button>
                      </div>
                    </div>
                  )}
                </section>
              )}
            </div>
          )}
        </DialogFrame>
      )}
    </section>
  );
}
