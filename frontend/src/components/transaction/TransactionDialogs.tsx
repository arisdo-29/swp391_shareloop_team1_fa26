import { useRef, useState } from 'react';
import { Alert, Button, Select, TextArea } from '../ui';
import { ReportEvidenceField } from '../ReportEvidenceField';
import { DialogFrame } from '../Dialog';
import { requestApi, type RequestResponse } from '../../services/requestApi';
import { mediaApi } from '../../services/mediaApi';
import { openDispute, type DisputeResponse, type ReportReason } from '../../services/reportApi';
import type { ApiError } from '../../services/http';
import { disputeErrorMessage, disputeReasonLabels } from '../../utils/disputeErrors';
import { formatDeadline } from '../../utils/request';

// Ba hộp thoại của trang Tiến trình giao dịch, chuyển từ bảng tiến trình cũ ở trang Tin nhắn.
// Mỗi hộp thoại tự giữ ô nhập và tự gọi API; xong thì báo Request mới (hoặc lời nhắn) về trang.

async function uploadEvidence(files: File[]) {
  const uploaded = await Promise.all(files.map((file) => mediaApi.upload(file)));
  return uploaded.map((entry) => entry.mediaId);
}

function cancelReasonError(error: unknown) {
  const apiError = error as Partial<ApiError>;
  const messages: Record<string, string> = {
    REQUEST_FORBIDDEN: 'Bạn không phải một bên của giao dịch này.',
    REQUEST_NOT_FOUND: 'Không tìm thấy giao dịch này.',
    REQUEST_INVALID_STATE_TRANSITION:
      'Giao dịch ở trạng thái hiện tại không thể huỷ hoặc đã thay đổi. Vui lòng tải lại.',
    VALIDATION_FAILED: 'Lý do huỷ phải từ 5 đến 500 ký tự.',
  };
  return (
    (apiError.code && messages[apiError.code]) ||
    'Không huỷ được giao dịch lúc này. Vui lòng thử lại.'
  );
}

interface DialogBaseProps {
  request: RequestResponse;
  currentUserId: number;
  itemTitle: string;
  onClose: () => void;
}

export function CancelTransactionDialog({
  request,
  currentUserId,
  itemTitle,
  onClose,
  onCancelled,
  onStale,
}: DialogBaseProps & { onCancelled: (request: RequestResponse) => void; onStale: () => void }) {
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const submit = async () => {
    const trimmed = reason.trim();
    if (submitting || trimmed.length < 5) return;
    setSubmitting(true);
    setError('');
    try {
      onCancelled(await requestApi.cancelTransaction(request.id, currentUserId, trimmed));
    } catch (caught) {
      setError(cancelReasonError(caught));
      if ((caught as ApiError).status === 409) onStale();
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <DialogFrame labelledBy="cancel-transaction-title" busy={submitting} onClose={onClose}>
      <h2 id="cancel-transaction-title" className="text-xl font-bold">
        Huỷ giao dịch
      </h2>
      <p className="mt-1 text-sm text-text-muted">{itemTitle}</p>
      <p className="mt-3 text-sm leading-6 text-text-secondary">
        Bên còn lại sẽ nhận được lý do này. Bài đăng mở lại cho người khác, không mất phí đăng lần
        nữa.
      </p>
      <TextArea
        className="mt-4"
        label="Lý do huỷ (bắt buộc)"
        value={reason}
        maxLength={500}
        disabled={submitting}
        onChange={(event) => setReason(event.target.value)}
      />
      <p className="mt-1 text-right text-xs text-text-muted">{reason.length}/500</p>
      {error ? (
        <div role="alert" className="mt-4 rounded-md bg-error-soft p-3 text-xs text-error">
          {error}
        </div>
      ) : null}
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="ghost" disabled={submitting} onClick={onClose}>
          Giữ giao dịch
        </Button>
        <Button disabled={submitting || reason.trim().length < 5} onClick={() => void submit()}>
          {submitting ? 'Đang huỷ...' : 'Huỷ giao dịch'}
        </Button>
      </div>
    </DialogFrame>
  );
}

export function HandoverIssueDialog({
  request,
  currentUserId,
  itemTitle,
  partnerName,
  viewerIsDonor,
  onClose,
  onReported,
  onStale,
}: DialogBaseProps & {
  partnerName: string;
  viewerIsDonor: boolean;
  onReported: (request: RequestResponse) => void;
  onStale: () => void;
}) {
  const [note, setNote] = useState('');
  const [evidence, setEvidence] = useState<File[]>([]);
  const inFlight = useRef(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const submit = async () => {
    const trimmed = note.trim();
    if (inFlight.current || trimmed.length < 5) return;
    inFlight.current = true;
    setSubmitting(true);
    setError('');
    try {
      const evidenceMediaIds = evidence.length ? await uploadEvidence(evidence) : undefined;
      onReported(
        await requestApi.reportHandoverIssue(request.id, currentUserId, {
          note: trimmed,
          evidenceMediaIds,
        }),
      );
    } catch (caught) {
      const apiError = caught as ApiError;
      setError(
        apiError.code === 'REQUEST_INVALID_STATE_TRANSITION'
          ? 'Chỉ báo được khi đối phương đã xác nhận bàn giao còn bạn thì chưa. Vui lòng tải lại.'
          : disputeErrorMessage(caught),
      );
      if (apiError.status === 409) onStale();
    } finally {
      inFlight.current = false;
      setSubmitting(false);
    }
  };

  const title = viewerIsDonor ? 'Tôi chưa giao được' : 'Tôi chưa nhận được';
  return (
    <DialogFrame labelledBy="handover-issue-title" busy={submitting} onClose={onClose}>
      <h2 id="handover-issue-title" className="text-xl font-bold">
        {title}
      </h2>
      <p className="mt-1 text-sm text-text-muted">{itemTitle}</p>
      <p className="mt-3 text-sm leading-6 text-text-secondary">
        {partnerName} đã xác nhận bàn giao. Nếu bạn gửi báo cáo, giao dịch sẽ chuyển sang khiếu nại
        để quản trị viên xem xét.
      </p>
      <TextArea
        className="mt-4"
        label="Mô tả sự cố (bắt buộc)"
        hint="Nhập ít nhất 5 ký tự. Hãy nêu thời điểm và điều đã xảy ra."
        value={note}
        maxLength={1000}
        disabled={submitting}
        onChange={(event) => setNote(event.target.value)}
      />
      <p className="mt-1 text-right text-xs text-text-muted">{note.length}/1000</p>
      <div className="mt-4">
        <ReportEvidenceField files={evidence} onChange={setEvidence} disabled={submitting} />
      </div>
      {error ? (
        <div role="alert" className="mt-4 rounded-md bg-error-soft p-3 text-xs text-error">
          {error}
        </div>
      ) : null}
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="ghost" disabled={submitting} onClick={onClose}>
          Đóng
        </Button>
        <Button disabled={submitting || note.trim().length < 5} onClick={() => void submit()}>
          {submitting ? 'Đang gửi...' : 'Gửi báo cáo'}
        </Button>
      </div>
    </DialogFrame>
  );
}

export function DisputeDialog({
  request,
  itemTitle,
  onClose,
  onSubmitted,
}: Omit<DialogBaseProps, 'currentUserId'> & { onSubmitted: (receipt: DisputeResponse) => void }) {
  const [reason, setReason] = useState<ReportReason | ''>('');
  const [note, setNote] = useState('');
  const [evidence, setEvidence] = useState<File[]>([]);
  const inFlight = useRef(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [noteError, setNoteError] = useState('');

  const submit = async () => {
    if (!reason || inFlight.current || request.status !== 'COMPLETED' || request.disputeExpired)
      return;
    const trimmedNote = note.trim();
    if (reason === 'OTHER' && !trimmedNote) {
      setNoteError('Vui lòng nhập lý do khiếu nại.');
      return;
    }
    inFlight.current = true;
    setSubmitting(true);
    setError('');
    try {
      const evidenceMediaIds = evidence.length ? await uploadEvidence(evidence) : undefined;
      const response = await openDispute(request.id, {
        reason,
        note: trimmedNote || undefined,
        evidenceMediaIds,
      });
      onSubmitted(response);
    } catch (caught) {
      setError(disputeErrorMessage(caught));
    } finally {
      inFlight.current = false;
      setSubmitting(false);
    }
  };

  return (
    <DialogFrame labelledBy="dispute-title" busy={submitting} onClose={onClose}>
      <h2 id="dispute-title" className="text-xl font-bold">
        Gửi khiếu nại giao dịch
      </h2>
      <p className="mt-1 text-sm text-text-muted">{itemTitle}</p>
      <div className="mt-4">
        <Alert tone="info">
          {request.complaintDeadline && (
            <strong className="block">Hạn gửi {formatDeadline(request.complaintDeadline)}</strong>
          )}
          Sau khi gửi, bên còn lại có thể giải trình để quản trị viên xem xét thông tin từ hai bên.
        </Alert>
      </div>
      <Select
        className="mt-5"
        label="Lý do"
        value={reason}
        disabled={submitting}
        onChange={(event) => {
          const nextReason = event.target.value as ReportReason | '';
          setReason(nextReason);
          if (nextReason !== 'OTHER') setNoteError('');
        }}
      >
        <option value="">Chọn lý do khiếu nại</option>
        {Object.entries(disputeReasonLabels).map(([value, label]) => (
          <option key={value} value={value}>
            {label}
          </option>
        ))}
      </Select>
      <TextArea
        className="mt-4"
        label={reason === 'OTHER' ? 'Nội dung (bắt buộc)' : 'Nội dung (không bắt buộc)'}
        hint="Mô tả điều đã xảy ra và thời điểm để quản trị viên dễ xác minh."
        required={reason === 'OTHER'}
        value={note}
        maxLength={500}
        disabled={submitting}
        error={noteError || undefined}
        onChange={(event) => {
          const nextNote = event.target.value;
          setNote(nextNote);
          if (nextNote.trim()) setNoteError('');
        }}
      />
      <p className="mt-1 text-right text-xs text-text-muted">{note.length}/500</p>
      <div className="mt-4">
        <ReportEvidenceField files={evidence} onChange={setEvidence} disabled={submitting} />
      </div>
      {error ? (
        <div role="alert" className="mt-4 rounded-md bg-error-soft p-3 text-xs text-error">
          {error}
        </div>
      ) : null}
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="ghost" disabled={submitting} onClick={onClose}>
          Hủy
        </Button>
        <Button disabled={submitting || !reason} onClick={() => void submit()}>
          {submitting ? 'Đang gửi...' : 'Gửi khiếu nại'}
        </Button>
      </div>
    </DialogFrame>
  );
}
