import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ReportEvidenceField } from '../../components/ReportEvidenceField';
import { Alert, Button, Icon, SkeletonRows, TextArea } from '../../components/ui';
import { mediaApi } from '../../services/mediaApi';
import {
  reportApi,
  respondDispute,
  type ReportEvidence,
  type RespondentReportDetail,
} from '../../services/reportApi';
import { disputeErrorMessage, disputeReasonLabels } from '../../utils/disputeErrors';

type Kind = 'report' | 'dispute';

const formatDateTime = (value: string) => new Date(value).toLocaleString('vi-VN');

/** Thời gian còn lại tới hạn, dạng "còn 1 ngày 3 giờ" hoặc "còn 45 phút". Chỉ để hiển thị, server mới quyết định hạn. */
function remainingText(dueAt: string) {
  const minutes = Math.max(0, Math.floor((new Date(dueAt).getTime() - Date.now()) / 60_000));
  const days = Math.floor(minutes / 1440);
  const hours = Math.floor((minutes % 1440) / 60);
  if (days) return `còn ${days} ngày ${hours} giờ`;
  if (hours) return `còn ${hours} giờ ${minutes % 60} phút`;
  return `còn ${minutes} phút`;
}

function reasonLabel(detail: RespondentReportDetail) {
  // Khiếu nại giao dịch dùng mã NO_SHOW cho mọi sự cố giao nhận, giống trang Khiếu nại của Admin.
  if (detail.targetType === 'REQUEST' && detail.reason === 'NO_SHOW') return 'Sự cố giao nhận';
  return disputeReasonLabels[detail.reason] ?? detail.reason;
}

export function ReportResponse({ kind = 'report' }: { kind?: Kind }) {
  const { reportId } = useParams();
  return <ResponsePage key={`${kind}-${reportId}`} reportId={Number(reportId)} kind={kind} />;
}

function ResponsePage({ reportId, kind }: { reportId: number; kind: Kind }) {
  const isDispute = kind === 'dispute';
  const validReportId = Number.isSafeInteger(reportId) && reportId > 0;
  const [detail, setDetail] = useState<RespondentReportDetail | null>(null);
  const [loadError, setLoadError] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);
  const [submitted, setSubmitted] = useState(false);

  useEffect(() => {
    if (!validReportId) return;
    // AbortController huỷ request cũ khi rời trang hoặc bấm Thử lại.
    const controller = new AbortController();
    setLoadError(false);
    reportApi
      .respondentView(reportId, controller.signal)
      .then(setDetail)
      .catch(() => {
        if (!controller.signal.aborted) setLoadError(true);
      });
    return () => controller.abort();
  }, [reportId, validReportId, reloadKey]);

  return (
    <div className="page-shell py-6 sm:py-10">
      <div className="mx-auto max-w-2xl">
        <Link
          to="/activities?tab=complaints"
          className="inline-flex min-h-11 items-center gap-2 text-sm font-semibold text-primary hover:underline"
        >
          <Icon name="caretLeft" className="size-4" />
          Về Khiếu nại & Báo cáo
        </Link>
        <section className="card mt-3 overflow-hidden" aria-labelledby="response-title">
          <header className="border-b border-border bg-surface-container/50 p-5 sm:p-7">
            <p className="mb-2 flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-primary">
              <Icon name="complaints" className="size-4" />
              {isDispute ? 'Khiếu nại giao dịch' : 'Báo cáo vi phạm'}
            </p>
            <h1 id="response-title" className="page-title">
              {isDispute ? 'Giải trình khiếu nại giao dịch' : 'Giải trình báo cáo'}
            </h1>
            {detail?.targetName ? (
              <p className="mt-2 text-sm font-semibold text-text-primary">{detail.targetName}</p>
            ) : null}
          </header>
          <div className="space-y-6 p-5 sm:p-7">
            {!validReportId ? (
              <Alert tone="error">Đường dẫn hồ sơ không hợp lệ. Hãy mở lại từ thông báo.</Alert>
            ) : loadError ? (
              <div className="space-y-3">
                <Alert tone="error">
                  Không tải được hồ sơ, hoặc hồ sơ này không cần bạn giải trình.
                </Alert>
                <Button variant="outline" size="sm" onClick={() => setReloadKey((key) => key + 1)}>
                  Thử lại
                </Button>
              </div>
            ) : !detail ? (
              <SkeletonRows rows={3} label="Đang tải hồ sơ" />
            ) : (
              <>
                <DeadlineNotice detail={detail} submitted={submitted} />
                <ComplaintDetails detail={detail} />
                {detail.responseWindowOpen && !submitted ? (
                  <ResponseForm
                    reportId={reportId}
                    kind={kind}
                    onSubmitted={() => setSubmitted(true)}
                  />
                ) : (
                  <SubmittedResponse detail={detail} />
                )}
              </>
            )}
          </div>
        </section>
      </div>
    </div>
  );
}

/** Hạn giải trình hiện ngay trên trang để người dùng không phải quay lại thông báo xem hạn. */
function DeadlineNotice({
  detail,
  submitted,
}: {
  detail: RespondentReportDetail;
  submitted: boolean;
}) {
  if (submitted) {
    return (
      <div role="status" className="space-y-1">
        <Alert tone="success">Giải trình đã được gửi đến quản trị viên.</Alert>
        <p className="text-sm text-text-secondary">
          Quản trị viên sẽ xem nội dung và minh chứng trước khi kết luận.
        </p>
      </div>
    );
  }
  if (detail.resolvedAt) {
    return (
      <Alert tone="info">
        Hồ sơ đã được quản trị viên kết luận nên không nhận thêm giải trình.
      </Alert>
    );
  }
  if (detail.respondedAt) {
    return (
      <Alert tone="success">
        Bạn đã gửi giải trình lúc {formatDateTime(detail.respondedAt)}. Hồ sơ đang chờ quản trị viên
        kết luận.
      </Alert>
    );
  }
  if (!detail.responseWindowOpen) {
    return (
      <Alert tone="warning">
        Đã hết hạn giải trình lúc {formatDateTime(detail.responseDueAt)}. Hồ sơ sẽ được kết luận
        theo thông tin hiện có.
      </Alert>
    );
  }
  return (
    <Alert tone="warning">
      <span className="font-semibold">Hạn giải trình: {formatDateTime(detail.responseDueAt)}</span>{' '}
      ({remainingText(detail.responseDueAt)}). Quá hạn thì hồ sơ không nhận giải trình nữa.
    </Alert>
  );
}

/** Nội dung bên kia phản ánh: lý do, mô tả và ảnh. Không có tên người báo cáo. */
function ComplaintDetails({ detail }: { detail: RespondentReportDetail }) {
  return (
    <section aria-labelledby="complaint-detail-title" className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 id="complaint-detail-title" className="text-base font-bold">
          Nội dung bị phản ánh
        </h2>
        {detail.requestId ? (
          <Link to={`/requests/${detail.requestId}`}>
            <Button variant="outline" size="sm">
              Nhắn tin với đối phương
            </Button>
          </Link>
        ) : null}
      </div>
      <dl className="space-y-2 text-sm">
        <div>
          <dt className="font-semibold text-text-secondary">Lý do</dt>
          <dd>{reasonLabel(detail)}</dd>
        </div>
        <div>
          <dt className="font-semibold text-text-secondary">Mô tả</dt>
          <dd className="whitespace-pre-line leading-6">
            {detail.description?.trim() || 'Không có mô tả.'}
          </dd>
        </div>
      </dl>
      <EvidenceGallery
        reportId={detail.id}
        title="Ảnh bên kia gửi kèm"
        evidence={detail.evidence}
      />
    </section>
  );
}

function SubmittedResponse({ detail }: { detail: RespondentReportDetail }) {
  return (
    <div className="space-y-3">
      {detail.response ? (
        <section aria-labelledby="my-response-title" className="space-y-2">
          <h2 id="my-response-title" className="text-base font-bold">
            Giải trình của bạn
          </h2>
          <p className="whitespace-pre-line text-sm leading-6">{detail.response}</p>
          <EvidenceGallery
            reportId={detail.id}
            title="Ảnh bạn gửi kèm"
            evidence={detail.responseEvidence}
          />
        </section>
      ) : null}
      {detail.resolvedAt ? (
        <p className="rounded-md bg-surface-low px-3 py-2 text-sm leading-6 text-text-secondary">
          <span className="font-semibold text-text-primary">Kết luận của quản trị viên: </span>
          {detail.resolution?.trim() || 'Không có ghi chú.'}
        </p>
      ) : null}
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
      <p className="mb-2 text-sm font-semibold text-text-secondary">
        {title} ({evidence.length})
      </p>
      {evidence.length ? (
        <ul className="grid grid-cols-2 gap-2 sm:grid-cols-3">
          {evidence.map((entry) => (
            <EvidenceImage key={entry.id} reportId={reportId} evidence={entry} />
          ))}
        </ul>
      ) : (
        <p className="text-sm text-text-muted">Không có ảnh đính kèm.</p>
      )}
    </div>
  );
}

/** Ảnh riêng tư phải tải kèm token rồi hiện qua object URL; bấm ảnh để mở cỡ lớn ở tab mới. */
function EvidenceImage({ reportId, evidence }: { reportId: number; evidence: ReportEvidence }) {
  const [source, setSource] = useState('');
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    if (!evidence.available || typeof URL.createObjectURL !== 'function') return;
    const controller = new AbortController();
    let objectUrl = '';
    reportApi
      .respondentEvidence(reportId, evidence.id, controller.signal)
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
  }, [reportId, evidence.id, evidence.available]);

  return (
    <li className="overflow-hidden rounded-md border border-border bg-background">
      {failed || !evidence.available ? (
        <p className="grid aspect-square place-items-center p-3 text-center text-xs text-text-muted">
          Ảnh không còn khả dụng.
        </p>
      ) : source ? (
        <a href={source} target="_blank" rel="noreferrer" aria-label="Mở ảnh cỡ lớn">
          <img src={source} alt="Ảnh minh chứng" className="aspect-square w-full object-cover" />
        </a>
      ) : (
        <p className="grid aspect-square place-items-center text-xs text-text-muted">
          Đang tải ảnh…
        </p>
      )}
    </li>
  );
}

function ResponseForm({
  reportId,
  kind,
  onSubmitted,
}: {
  reportId: number;
  kind: Kind;
  onSubmitted: () => void;
}) {
  const isDispute = kind === 'dispute';
  const maxLength = isDispute ? 1000 : 2000;
  const [response, setResponse] = useState('');
  const [evidenceFiles, setEvidenceFiles] = useState<File[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const inFlight = useRef(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const content = response.trim();
    if (!content || content.length > maxLength || inFlight.current) return;
    inFlight.current = true;
    setSubmitting(true);
    setError('');
    try {
      const uploads = await Promise.all(evidenceFiles.map((file) => mediaApi.upload(file)));
      const evidenceMediaIds = uploads.length ? uploads.map((entry) => entry.mediaId) : undefined;
      if (isDispute) {
        await respondDispute(reportId, { note: content, evidenceMediaIds });
      } else {
        await reportApi.respond(reportId, { response: content, evidenceMediaIds });
      }
      onSubmitted();
    } catch (caught) {
      setError(disputeErrorMessage(caught));
    } finally {
      inFlight.current = false;
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={submit} className="space-y-5 border-t border-border pt-5">
      <div>
        <TextArea
          label="Nội dung giải trình (bắt buộc)"
          hint="Nêu thời điểm, việc đã xảy ra và ảnh nào giúp làm rõ."
          placeholder="Ví dụ: Tôi đã giao món đồ vào..."
          value={response}
          maxLength={maxLength}
          disabled={submitting}
          required
          onChange={(event) => setResponse(event.target.value)}
        />
        <p className="mt-1 text-right text-xs text-text-secondary">
          {response.length}/{maxLength}
        </p>
      </div>
      <ReportEvidenceField
        files={evidenceFiles}
        onChange={setEvidenceFiles}
        disabled={submitting}
      />
      {error && (
        <div role="alert">
          <Alert tone="error">{error}</Alert>
        </div>
      )}
      <div className="flex justify-end border-t border-border pt-4">
        <Button
          type="submit"
          className="w-full sm:w-auto"
          disabled={submitting || !response.trim()}
        >
          {submitting ? 'Đang gửi…' : 'Gửi giải trình'}
        </Button>
      </div>
    </form>
  );
}
