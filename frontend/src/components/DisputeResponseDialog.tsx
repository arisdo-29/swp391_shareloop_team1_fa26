import { useRef, useState, type FormEvent } from 'react';
import { DialogFrame } from './Dialog';
import { ReportEvidenceField } from './ReportEvidenceField';
import { Alert, Button, TextArea } from './ui';
import { mediaApi } from '../services/mediaApi';
import { reportApi, respondDispute } from '../services/reportApi';
import { disputeErrorMessage } from '../utils/disputeErrors';

const MAX_RESPONSE_LENGTH = 2000;

interface DisputeResponseFormProps {
  reportId: number;
  // 'dispute' gửi qua /disputes/{id}/response, 'report' gửi qua /reports/{id}/response.
  kind: 'report' | 'dispute';
  onSubmitted: () => void;
  onCancel?: () => void;
}

/**
 * Form giải trình dùng chung cho trang /me/disputes/{id}, trang Tiến trình và khung chat (BC-08). Tối đa 2000 ký tự,
 * ảnh minh chứng tuỳ chọn. Backend kiểm hạn 48 giờ và chỉ cho giải trình một lần.
 */
export function DisputeResponseForm({ reportId, kind, onSubmitted, onCancel }: DisputeResponseFormProps) {
  const [response, setResponse] = useState('');
  const [evidenceFiles, setEvidenceFiles] = useState<File[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const inFlight = useRef(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const content = response.trim();
    if (!content || content.length > MAX_RESPONSE_LENGTH || inFlight.current) return;
    inFlight.current = true;
    setSubmitting(true);
    setError('');
    try {
      const uploads = await Promise.all(evidenceFiles.map((file) => mediaApi.upload(file)));
      const evidenceMediaIds = uploads.length ? uploads.map((entry) => entry.mediaId) : undefined;
      if (kind === 'dispute') {
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
          maxLength={MAX_RESPONSE_LENGTH}
          disabled={submitting}
          required
          onChange={(event) => setResponse(event.target.value)}
        />
        <p className="mt-1 text-right text-xs text-text-secondary">
          {response.length}/{MAX_RESPONSE_LENGTH}
        </p>
      </div>
      <ReportEvidenceField files={evidenceFiles} onChange={setEvidenceFiles} disabled={submitting} />
      {error && (
        <div role="alert">
          <Alert tone="error">{error}</Alert>
        </div>
      )}
      <div className="flex justify-end gap-2 border-t border-border pt-4">
        {onCancel ? (
          <Button variant="outline" disabled={submitting} onClick={onCancel}>
            Đóng
          </Button>
        ) : null}
        <Button type="submit" className="w-full sm:w-auto" disabled={submitting || !response.trim()}>
          {submitting ? 'Đang gửi…' : 'Gửi giải trình'}
        </Button>
      </div>
    </form>
  );
}

/** Hộp thoại giải trình mở ngay ở trang Tiến trình hoặc khung chat, không phải chuyển sang /me/disputes/{id}. */
export function DisputeResponseDialog({
  disputeId,
  reasonLabel,
  deadlineText,
  onClose,
  onSubmitted,
}: {
  disputeId: number;
  reasonLabel: string;
  deadlineText?: string;
  onClose: () => void;
  onSubmitted: () => void;
}) {
  return (
    <DialogFrame labelledBy="dispute-response-title" onClose={onClose}>
      <h2 id="dispute-response-title" className="text-xl font-bold">
        Giải trình khiếu nại
      </h2>
      <p className="mt-1 text-sm text-text-muted">Lý do khiếu nại: {reasonLabel}</p>
      {deadlineText ? (
        <p className="mt-3 text-sm leading-6 text-text-secondary">{deadlineText}</p>
      ) : null}
      <div className="mt-4">
        <DisputeResponseForm reportId={disputeId} kind="dispute" onSubmitted={onSubmitted} onCancel={onClose} />
      </div>
    </DialogFrame>
  );
}
