import { useRef, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ReportEvidenceField } from '../../components/ReportEvidenceField';
import { Alert, Button, Icon, TextArea } from '../../components/ui';
import { mediaApi } from '../../services/mediaApi';
import { reportApi, respondDispute } from '../../services/reportApi';
import { disputeErrorMessage } from '../../utils/disputeErrors';

export function ReportResponse({ kind = 'report' }: { kind?: 'report' | 'dispute' }) {
  const { reportId } = useParams();
  return <ResponseForm key={`${kind}-${reportId}`} reportId={Number(reportId)} kind={kind} />;
}

function ResponseForm({ reportId, kind }: { reportId: number; kind: 'report' | 'dispute' }) {
  const isDispute = kind === 'dispute';
  const title = isDispute ? 'Giải trình khiếu nại giao dịch' : 'Giải trình báo cáo';
  const maxLength = isDispute ? 1000 : 2000;
  const validReportId = Number.isSafeInteger(reportId) && reportId > 0;
  const [response, setResponse] = useState('');
  const [evidenceFiles, setEvidenceFiles] = useState<File[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);
  const inFlight = useRef(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const content = response.trim();
    if (!validReportId || !content || content.length > maxLength || inFlight.current) return;
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
      setSuccess(true);
    } catch (caught) {
      setError(disputeErrorMessage(caught));
    } finally {
      inFlight.current = false;
      setSubmitting(false);
    }
  }

  return (
    <div className="page-shell py-6 sm:py-10">
      <div className="mx-auto max-w-2xl">
        <Link
          to="/activities"
          className="inline-flex min-h-11 items-center gap-2 text-sm font-semibold text-primary hover:underline"
        >
          <Icon name="caretLeft" className="size-4" />
          Về Hoạt động
        </Link>
        <section className="card mt-3 overflow-hidden" aria-labelledby="response-title">
          <header className="border-b border-border bg-surface-container/50 p-5 sm:p-7">
            <p className="mb-2 flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-primary">
              <Icon name="complaints" className="size-4" />
              Hồ sơ {validReportId ? `#${reportId}` : ''}
            </p>
            <h1 id="response-title" className="page-title">
              {title}
            </h1>
            <p className="mt-2 text-sm text-text-secondary">
              Trình bày sự việc từ phía bạn để quản trị viên xem xét.
            </p>
          </header>
          {!validReportId ? (
            <div className="p-5 sm:p-7">
              <Alert tone="error">Đường dẫn hồ sơ không hợp lệ. Hãy mở lại từ thông báo.</Alert>
            </div>
          ) : success ? (
            <div className="space-y-4 p-5 sm:p-7" role="status">
              <Alert tone="success">Giải trình đã được gửi đến quản trị viên.</Alert>
              <p className="text-sm text-text-secondary">
                Quản trị viên sẽ xem nội dung và minh chứng trước khi kết luận.
              </p>
            </div>
          ) : (
            <form onSubmit={submit} className="space-y-5 p-5 sm:p-7">
              <Alert tone="info">
                Xem hạn giải trình trong thông báo dẫn đến trang này. Hồ sơ chỉ nhận giải trình
                trong thời hạn đó.
              </Alert>
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
          )}
        </section>
      </div>
    </div>
  );
}
