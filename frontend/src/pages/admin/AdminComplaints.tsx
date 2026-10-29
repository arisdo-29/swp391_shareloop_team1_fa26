import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Alert, Button, EmptyState, PageHeader, Select, TextArea, SkeletonRows } from '../../components/ui';
import { DialogFrame } from '../../components/Dialog';
import {
  reportApi,
  type AdminReportDetail,
  type AdminReportPage,
  type ReportDecision,
  type ReportStatus,
  type ReportTargetType,
  type ReportEvidence,
} from '../../services/reportApi';
import type { ApiError } from '../../services/http';

const statusLabels: Record<ReportStatus, string> = {
  OPEN: 'Chờ xử lý',
  AWAITING_RESPONSE: 'Chờ giải trình',
  RESOLVED: 'Có cơ sở',
  DISMISSED: 'Đã bác bỏ',
};
const targetLabels: Record<ReportTargetType, string> = {
  ITEM: 'Bài đăng',
  USER: 'Người dùng',
  REQUEST: 'Giao dịch',
};
const targetFilterOptions = [
  { value: 'ITEM', label: 'Báo cáo bài đăng' },
  { value: 'USER', label: 'Báo cáo người dùng' },
] satisfies { value: ReportTargetType; label: string }[];
const reasonLabels: Record<string, string> = {
  NOT_AS_DESCRIBED: 'Không đúng mô tả',
  COUNTERFEIT: 'Hàng giả',
  PROHIBITED_ITEM: 'Hàng cấm',
  NO_SHOW: 'Không đến hẹn',
  WRONG_CONTACT_INFO: 'Sai thông tin liên hệ',
  HARASSMENT: 'Quấy rối',
  OTHER: 'Khác',
};
const date = (value: string | null) =>
  value ? new Date(value).toLocaleString('vi-VN') : 'Chưa có';
const concluded = (status: ReportStatus) => status === 'RESOLVED' || status === 'DISMISSED';
const statusTones: Record<ReportStatus, string> = {
  OPEN: 'bg-warning-soft text-warning',
  AWAITING_RESPONSE: 'bg-info-soft text-info',
  RESOLVED: 'bg-error-soft text-error',
  DISMISSED: 'bg-surface-container text-text-secondary',
};

// Cùng kiểu StatusBadge trong ui.tsx nhưng theo bộ trạng thái của hồ sơ khiếu nại.
function ReportStatusBadge({ status }: { status: ReportStatus }) {
  return (
    <span
      className={`inline-flex whitespace-nowrap rounded-sm px-2 py-1 text-2xs font-semibold ${statusTones[status]}`}
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
  return detail.reportedUser ? (detail.reportedUser.fullName ?? 'Người dùng') : 'Không còn xác định được';
}

// Hệ quả của kết luận đã lưu, khớp với xử lý ở backend (ReportService.conclude).
function consequences(detail: AdminReportDetail) {
  const upheld = detail.report.status === 'RESOLVED';
  const parts = [detail.starsDeducted > 0 ? `Trừ ${detail.starsDeducted} sao uy tín` : 'Không trừ sao'];
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
  if (error.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
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
  const [list, setList] = useState<AdminReportPage | null>(null);
  const [listLoading, setListLoading] = useState(true);
  const [listError, setListError] = useState('');
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [detail, setDetail] = useState<AdminReportDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState('');
  const [decision, setDecision] = useState<ReportDecision>('UPHOLD');
  const [note, setNote] = useState('');
  const [confirming, setConfirming] = useState(false);
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
    setConfirming(false);
    setNote('');
    setDecision('UPHOLD');
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

  async function submit() {
    if (
      !detail ||
      concluded(detail.report.status) ||
      submittingRef.current ||
      !confirming ||
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
      if (failure.status === 409) {
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
      setConfirming(false);
    }
  }

  return (
    <section>
      <PageHeader title="Khiếu nại" description="Hồ sơ cần quản trị viên xem xét và kết luận." />
      {notice && (
        <div className="mb-4" role="status">
          <Alert tone={noticeTone}>{notice}</Alert>
        </div>
      )}
      <div className="mb-5 flex flex-wrap gap-3 rounded-lg bg-white p-3 ring-1 ring-border/80">
        <Select
          aria-label="Lọc trạng thái"
          value={status}
          disabled={submitting}
          onChange={(event) => {
            setStatus(event.target.value as ReportStatus | '');
            setPage(0);
            setSelectedId(null);
          }}
        >
          <option value="">Tất cả trạng thái</option>
          {Object.entries(statusLabels).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </Select>
        <Select
          aria-label="Lọc đối tượng"
          value={targetType}
          disabled={submitting}
          onChange={(event) => {
            setTargetType(event.target.value as ReportTargetType | '');
            setPage(0);
            setSelectedId(null);
          }}
        >
          <option value="">Nội dung báo cáo</option>
          {targetFilterOptions.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </Select>
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
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Người gửi</th>
                  <th>Nội dung bị khiếu nại</th>
                  <th>Lý do</th>
                  <th>Ngày gửi</th>
                  <th>Trạng thái</th>
                  <th>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {list.items.map((report) => (
                  <tr key={report.id}>
                    <td>{report.reporterName || 'Người dùng đã xóa'}</td>
                    <td>
                      <span className="text-xs text-text-muted">{targetLabels[report.targetType]}</span>
                      <p className="font-semibold">{report.targetName || 'Không còn xác định được'}</p>
                    </td>
                    <td>{reasonLabels[report.reason] ?? report.reason}</td>
                    <td>{date(report.createdAt)}</td>
                    <td>
                      <ReportStatusBadge status={report.status} />
                    </td>
                    <td>
                      <Button
                        variant="outline"
                        disabled={submitting}
                        onClick={() => {
                          setDetail(null);
                          setDetailError('');
                          setSelectedId(report.id);
                        }}
                      >
                        Xem chi tiết
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="mt-4 flex items-center justify-end gap-3">
            <span className="text-sm">
              Trang {page + 1}/{list.totalPages} · {list.totalElements} hồ sơ
            </span>
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
        </>
      ) : (
        <EmptyState title="Không có khiếu nại" text="Không có hồ sơ phù hợp bộ lọc hiện tại." />
      )}
      {selectedId !== null && (
        <DialogFrame labelledBy="report-detail-title" size="xl">
          <div className="mb-4">
            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-2">
                <h2 id="report-detail-title" className="section-title">
                  Hồ sơ khiếu nại
                </h2>
                {detail && <ReportStatusBadge status={detail.report.status} />}
              </div>
              {detail && (
                <p className="mt-1 text-sm text-text-secondary">Gửi lúc {date(detail.report.createdAt)}</p>
              )}
            </div>
          </div>
          {detailLoading && <p role="status">Đang tải hồ sơ…</p>}
          {detailError && (
            <div role="alert" className="mb-4 space-y-2">
              <Alert tone="warning">{detailError}</Alert>
              <Button disabled={submitting} onClick={() => setReload((value) => value + 1)}>
                Thử tải lại
              </Button>
            </div>
          )}
          {detail && (
            <div className="space-y-4 text-sm">
              <dl className="space-y-2">
                <DetailRow label="Người gửi">{detail.reporter.fullName ?? 'Người dùng'}</DetailRow>
                <DetailRow label="Người bị báo cáo">{reportedUserName(detail)}</DetailRow>
                {detail.relatedItemId !== null && (
                  <DetailRow label="Bài đăng liên quan">
                    {detail.relatedItemTitle ?? 'Bài đăng đã bị xóa'}
                  </DetailRow>
                )}
                <DetailRow label="Giai đoạn">
                  {detail.report.stage === 'BEFORE' ? 'Trước giao dịch' : 'Sau giao dịch'}
                </DetailRow>
              </dl>
              <section className="space-y-3 border-t border-border pt-4">
                <h3 className="font-semibold text-text-primary">Nội dung khiếu nại</h3>
                <div className="rounded-md bg-surface-low p-3">
                  <strong>{reasonLabels[detail.report.reason] ?? detail.report.reason}</strong>
                  <p className="mt-2 whitespace-pre-wrap">{detail.report.description}</p>
                </div>
                {detail.evidence.length ? (
                  <EvidenceGallery
                    reportId={detail.report.id}
                    title="Ảnh bằng chứng của người gửi"
                    evidence={detail.evidence}
                  />
                ) : (
                  <p className="text-text-secondary">Không kèm ảnh bằng chứng.</p>
                )}
              </section>
              <section className="space-y-3 border-t border-border pt-4">
                <h3 className="font-semibold text-text-primary">Giải trình</h3>
                {detail.response ? (
                  <>
                    <div className="rounded-md bg-surface-low p-3">
                      <p className="whitespace-pre-wrap">{detail.response}</p>
                    </div>
                    {detail.respondedAt && (
                      <p className="text-text-secondary">Giải trình lúc {date(detail.respondedAt)}</p>
                    )}
                    {detail.responseEvidence.length > 0 && (
                      <EvidenceGallery
                        reportId={detail.report.id}
                        title="Ảnh bằng chứng của người giải trình"
                        evidence={detail.responseEvidence}
                      />
                    )}
                  </>
                ) : (
                  <p className="text-text-secondary">
                    Chưa có giải trình.
                    {detail.responseDueAt ? ` Hạn giải trình: ${date(detail.responseDueAt)}.` : ''}
                  </p>
                )}
              </section>
              {concluded(detail.report.status) ? (
                <section className="rounded-md bg-surface-low p-4">
                  <h3 className="mb-3 font-semibold text-text-primary">Kết quả xử lý</h3>
                  <dl className="space-y-2">
                    <DetailRow label="Kết luận">
                      {detail.report.status === 'RESOLVED' ? 'Có cơ sở' : 'Không có cơ sở'}
                    </DetailRow>
                    <DetailRow
                      label={detail.report.status === 'RESOLVED' ? 'Người bị xử lý' : 'Người bị báo cáo'}
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
                    {detail.resolvedByName ?? 'Quản trị viên'} kết luận lúc {date(detail.resolvedAt)}
                  </p>
                </section>
              ) : (
                <section className="space-y-4 border-t border-border pt-4">
                  <Select
                    label="Kết luận"
                    value={decision}
                    disabled={submitting || confirming}
                    onChange={(event) => setDecision(event.target.value as ReportDecision)}
                  >
                    <option value="UPHOLD">Có cơ sở</option>
                    <option value="DISMISS">Không có cơ sở (bác bỏ)</option>
                  </Select>
                  <TextArea
                    label="Ghi chú kết luận (tối đa 500 ký tự, không bắt buộc)"
                    value={note}
                    maxLength={500}
                    disabled={submitting || confirming}
                    onChange={(event) => setNote(event.target.value)}
                  />
                  {confirming ? (
                    <div className="space-y-3 rounded-md bg-background p-3">
                      <p>
                        Xác nhận{' '}
                        {decision === 'UPHOLD'
                          ? 'khiếu nại có cơ sở và áp dụng chế tài'
                          : 'bác bỏ khiếu nại'}
                        ? Kết luận được lưu ngay sau khi gửi.
                      </p>
                      <div className="flex gap-2">
                        <Button disabled={submitting} onClick={() => void submit()}>
                          {submitting ? 'Đang gửi…' : 'Xác nhận gửi'}
                        </Button>
                        <Button
                          variant="ghost"
                          disabled={submitting}
                          onClick={() => setConfirming(false)}
                        >
                          Quay lại
                        </Button>
                      </div>
                    </div>
                  ) : null}
                </section>
              )}
            </div>
          )}
          <div className="mt-6 flex items-center justify-between gap-3 border-t border-border pt-4">
            {detail && !concluded(detail.report.status) && !confirming && (
              <Button
                disabled={submitting || note.length > 500}
                onClick={() => setConfirming(true)}
              >
                Kết luận
              </Button>
            )}
            <Button
              variant="ghost"
              className="ml-auto"
              disabled={submitting}
              onClick={() => setSelectedId(null)}
            >
              Đóng
            </Button>
          </div>
        </DialogFrame>
      )}
    </section>
  );
}
