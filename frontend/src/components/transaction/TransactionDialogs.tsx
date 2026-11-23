import { usePublicConfig } from '../../utils/usePublicConfig';
import { useRef, useState } from 'react';
import { Alert, Button, Select, TextArea } from '../ui';
import { ReportEvidenceField } from '../ReportEvidenceField';
import { DialogFrame } from '../Dialog';
import { requestApi, type CancelRequestCode, type RequestResponse } from '../../services/requestApi';
import { mediaApi } from '../../services/mediaApi';
import { openDispute, type DisputeResponse, type ReportReason } from '../../services/reportApi';
import type { ApiError } from '../../services/http';
import { disputeErrorMessage, disputeReasonLabels } from '../../utils/disputeErrors';
import { canConfirmHandover, formatDeadline } from '../../utils/request';
import { shippingFailedConsequence } from '../../utils/shipping';
import {
  afterMeetingReasons,
  BEFORE_MEETING_REASONS,
  cancelRequestConsequence,
  handoverFailedConsequence,
  REFUSE_HANDOVER_CONSEQUENCE,
  REFUSE_HANDOVER_REASON_LABEL,
} from '../../utils/handoverOutcome';

// Các hộp thoại của trang Tiến trình giao dịch, chuyển từ bảng tiến trình cũ ở trang Tin nhắn.
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
      'Giao dịch ở trạng thái hiện tại không thể hủy hoặc đã thay đổi. Vui lòng tải lại.',
    VALIDATION_FAILED: 'Lý do hủy phải từ 5 đến 500 ký tự.',
  };
  return (
    (apiError.code && messages[apiError.code]) ||
    'Không hủy được giao dịch lúc này. Vui lòng thử lại.'
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
        Hủy giao dịch
      </h2>
      <p className="mt-1 text-sm text-text-muted">{itemTitle}</p>
      <p className="mt-3 text-sm leading-6 text-text-secondary">
        Bên còn lại sẽ nhận được lý do này. Bài đăng mở lại cho người khác, không mất phí đăng lần
        nữa.
      </p>
      <TextArea
        className="mt-4"
        label="Lý do hủy (bắt buộc)"
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
        <Button variant="outline" disabled={submitting} onClick={onClose}>
          Giữ giao dịch
        </Button>
        <Button disabled={submitting || reason.trim().length < 5} onClick={() => void submit()}>
          {submitting ? 'Đang hủy...' : 'Hủy giao dịch'}
        </Button>
      </div>
    </DialogFrame>
  );
}

const HANDOVER_FAILED_LIMIT = 500;
const REFUSE_REASON_LIMIT = 1000;

function handoverOutcomeError(error: unknown) {
  const apiError = error as Partial<ApiError>;
  const messages: Record<string, string> = {
    REQUEST_FORBIDDEN: 'Bạn không phải một bên của giao dịch này.',
    REQUEST_NOT_FOUND: 'Không tìm thấy giao dịch này.',
    REQUEST_REFUSE_NOT_ALLOWED: 'Chỉ bên nhận món đồ mới được từ chối giao nhận.',
    REQUEST_INVALID_STATE_TRANSITION: 'Giao dịch đã thay đổi trạng thái. Vui lòng tải lại.',
    REQUEST_BEFORE_MEETING_TIME: 'Chưa tới giờ hẹn. Bạn có thể đề nghị huỷ giao dịch.',
    REQUEST_CANCEL_REQUEST_PENDING: 'Đã có một đề nghị huỷ đang chờ phản hồi.',
    REQUEST_CANCEL_REQUEST_NOT_FOUND: 'Đề nghị huỷ không còn chờ phản hồi. Vui lòng tải lại.',
    REQUEST_CANCEL_REASON_INVALID: 'Lý do không hợp lệ. Lý do khác cần nhập ít nhất 5 ký tự.',
  };
  return (apiError.code && messages[apiError.code]) || disputeErrorMessage(error);
}

interface HandoverOutcomeProps extends DialogBaseProps {
  partnerName: string;
  viewerIsDonor: boolean;
  onDone: (request: RequestResponse) => void;
  onStale: () => void;
}

export function HandoverFailedDialog({
  request,
  currentUserId,
  itemTitle,
  partnerName,
  viewerIsDonor,
  onClose,
  onDone,
  onStale,
}: HandoverOutcomeProps) {
  const [code, setCode] = useState<CancelRequestCode | ''>('');
  const [note, setNote] = useState('');
  const [evidence, setEvidence] = useState<File[]>([]);
  const inFlight = useRef(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const config = usePublicConfig();
  const partnerConfirmed = Boolean(viewerIsDonor ? request.receiverHandoverAt : request.donorHandoverAt);
  const reasons = afterMeetingReasons(request.deliveryMethod === 'SHIPPING');
  const noteRequired = code === 'OTHER';
  const noteReady = !noteRequired || note.trim().length >= 5;
  const canSubmit = canConfirmHandover(request.status) && Boolean(code) && noteReady &&
    note.length <= HANDOVER_FAILED_LIMIT;

  const submit = async () => {
    if (inFlight.current || !canSubmit || !code) return;
    inFlight.current = true;
    setSubmitting(true);
    setError('');
    try {
      const evidenceMediaIds = evidence.length ? await uploadEvidence(evidence) : undefined;
      onDone(
        await requestApi.handoverFailed(request.id, currentUserId, {
          code,
          reason: note.trim() || undefined,
          evidenceMediaIds,
        }),
      );
    } catch (caught) {
      setError(handoverOutcomeError(caught));
      if ((caught as ApiError).status === 409) onStale();
    } finally {
      inFlight.current = false;
      setSubmitting(false);
    }
  };

  return (
    <DialogFrame labelledBy="handover-failed-title" busy={submitting} onClose={onClose}>
      <h2 id="handover-failed-title" className="text-xl font-bold">
        Giao nhận không thành công
      </h2>
      <p className="mt-1 text-sm text-text-muted">{itemTitle}</p>
      <p className="mt-3 text-sm leading-6 text-text-secondary">
        {request.deliveryMethod === 'SHIPPING'
          ? shippingFailedConsequence(
              request,
              viewerIsDonor,
              partnerName,
              config.response_window_hours,
              config.cancel_request_response_hours,
            )
          : handoverFailedConsequence(
              partnerConfirmed,
              partnerName,
              config.response_window_hours,
              config.cancel_request_response_hours,
            )}
      </p>
      <Select className="mt-4" label="Lý do giao nhận không thành công" value={code}
        disabled={submitting} onChange={(event) => setCode(event.target.value as CancelRequestCode | '')}>
        <option value="">Chọn lý do</option>
        {reasons.map((option) => (
          <option key={option.code} value={option.code}>
            {option.label}
          </option>
        ))}
      </Select>
      <TextArea
        className="mt-4"
        label={noteRequired ? 'Mô tả sự cố (bắt buộc)' : 'Mô tả thêm (không bắt buộc)'}
        hint={noteRequired ? 'Nhập ít nhất 5 ký tự.' : undefined}
        value={note}
        maxLength={HANDOVER_FAILED_LIMIT}
        disabled={submitting}
        onChange={(event) => setNote(event.target.value)}
      />
      <p className="mt-1 text-right text-xs text-text-muted">{note.length}/{HANDOVER_FAILED_LIMIT}</p>
      <div className="mt-4">
        <ReportEvidenceField files={evidence} onChange={setEvidence} disabled={submitting} />
      </div>
      {error ? (
        <div role="alert" className="mt-4 rounded-md bg-error-soft p-3 text-xs text-error">
          {error}
        </div>
      ) : null}
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="outline" disabled={submitting} onClick={onClose}>
          Đóng
        </Button>
        <Button disabled={submitting || !canSubmit} onClick={() => void submit()}>
          {submitting ? 'Đang xử lý...' : partnerConfirmed ? 'Gửi khiếu nại' : 'Gửi đề nghị huỷ'}
        </Button>
      </div>
    </DialogFrame>
  );
}

/** Hộp "Đề nghị huỷ giao dịch" dùng trước giờ hẹn (GN-06). Giao dịch chưa huỷ cho tới khi bên kia phản hồi. */
export function CancelRequestDialog({
  request,
  currentUserId,
  itemTitle,
  partnerName,
  onClose,
  onDone,
  onStale,
}: HandoverOutcomeProps) {
  const [code, setCode] = useState<CancelRequestCode | ''>('');
  const [note, setNote] = useState('');
  const inFlight = useRef(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const config = usePublicConfig();
  const noteRequired = code === 'OTHER';
  const canSubmit = canConfirmHandover(request.status) && Boolean(code) &&
    (!noteRequired || note.trim().length >= 5);

  const submit = async () => {
    if (inFlight.current || !canSubmit || !code) return;
    inFlight.current = true;
    setSubmitting(true);
    setError('');
    try {
      onDone(
        await requestApi.requestCancel(request.id, currentUserId, { code, reason: note.trim() || undefined }),
      );
    } catch (caught) {
      setError(handoverOutcomeError(caught));
      if ((caught as ApiError).status === 409) onStale();
    } finally {
      inFlight.current = false;
      setSubmitting(false);
    }
  };

  return (
    <DialogFrame labelledBy="cancel-request-title" busy={submitting} onClose={onClose}>
      <h2 id="cancel-request-title" className="text-xl font-bold">
        Đề nghị huỷ giao dịch
      </h2>
      <p className="mt-1 text-sm text-text-muted">{itemTitle}</p>
      <p className="mt-3 text-sm leading-6 text-text-secondary">
        {cancelRequestConsequence(partnerName, config.cancel_request_response_hours)}
      </p>
      <Select className="mt-4" label="Lý do đề nghị huỷ" value={code}
        disabled={submitting} onChange={(event) => setCode(event.target.value as CancelRequestCode | '')}>
        <option value="">Chọn lý do</option>
        {BEFORE_MEETING_REASONS.map((option) => (
          <option key={option.code} value={option.code}>
            {option.label}
          </option>
        ))}
      </Select>
      <TextArea
        className="mt-4"
        label={noteRequired ? 'Mô tả lý do (bắt buộc)' : 'Mô tả thêm (không bắt buộc)'}
        hint={noteRequired ? 'Nhập ít nhất 5 ký tự.' : undefined}
        value={note}
        maxLength={HANDOVER_FAILED_LIMIT}
        disabled={submitting}
        onChange={(event) => setNote(event.target.value)}
      />
      {error ? (
        <div role="alert" className="mt-4 rounded-md bg-error-soft p-3 text-xs text-error">
          {error}
        </div>
      ) : null}
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="outline" disabled={submitting} onClick={onClose}>
          Đóng
        </Button>
        <Button variant="danger" disabled={submitting || !canSubmit} onClick={() => void submit()}>
          {submitting ? 'Đang gửi...' : 'Gửi đề nghị huỷ'}
        </Button>
      </div>
    </DialogFrame>
  );
}

/** Hộp "Không đồng ý" với đề nghị huỷ của bên kia: giải trình bắt buộc, ảnh không bắt buộc (GN-06). */
export function RejectCancelRequestDialog({
  request,
  currentUserId,
  itemTitle,
  partnerName,
  onClose,
  onDone,
  onStale,
}: HandoverOutcomeProps) {
  const [explanation, setExplanation] = useState('');
  const [evidence, setEvidence] = useState<File[]>([]);
  const inFlight = useRef(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const trimmed = explanation.trim();
  const canSubmit = trimmed.length >= 5 && trimmed.length <= HANDOVER_FAILED_LIMIT;

  const submit = async () => {
    if (inFlight.current || !canSubmit) return;
    inFlight.current = true;
    setSubmitting(true);
    setError('');
    try {
      const evidenceMediaIds = evidence.length ? await uploadEvidence(evidence) : undefined;
      onDone(
        await requestApi.rejectCancelRequest(request.id, currentUserId, {
          explanation: trimmed,
          evidenceMediaIds,
        }),
      );
    } catch (caught) {
      setError(handoverOutcomeError(caught));
      if ((caught as ApiError).status === 409) onStale();
    } finally {
      inFlight.current = false;
      setSubmitting(false);
    }
  };

  return (
    <DialogFrame labelledBy="reject-cancel-title" busy={submitting} onClose={onClose}>
      <h2 id="reject-cancel-title" className="text-xl font-bold">
        Không đồng ý huỷ giao dịch
      </h2>
      <p className="mt-1 text-sm text-text-muted">{itemTitle}</p>
      <p className="mt-3 text-sm leading-6 text-text-secondary">
        Giao dịch chuyển sang sự cố giao nhận và chờ Admin xác minh. Lời giải trình của bạn được gửi kèm lý do của{' '}
        {partnerName}.
      </p>
      <TextArea
        className="mt-4"
        label="Giải trình (bắt buộc)"
        hint="Nhập ít nhất 5 ký tự."
        value={explanation}
        maxLength={HANDOVER_FAILED_LIMIT}
        disabled={submitting}
        onChange={(event) => setExplanation(event.target.value)}
      />
      <div className="mt-4">
        <ReportEvidenceField files={evidence} onChange={setEvidence} disabled={submitting} />
      </div>
      {error ? (
        <div role="alert" className="mt-4 rounded-md bg-error-soft p-3 text-xs text-error">
          {error}
        </div>
      ) : null}
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="outline" disabled={submitting} onClick={onClose}>
          Đóng
        </Button>
        <Button variant="warning" disabled={submitting || !canSubmit} onClick={() => void submit()}>
          {submitting ? 'Đang gửi...' : 'Gửi giải trình'}
        </Button>
      </div>
    </DialogFrame>
  );
}

export function RefuseHandoverDialog({
  request,
  currentUserId,
  itemTitle,
  partnerName,
  onClose,
  onDone,
  onStale,
}: HandoverOutcomeProps) {
  const [reason, setReason] = useState('');
  const [evidence, setEvidence] = useState<File[]>([]);
  const inFlight = useRef(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const trimmed = reason.trim();
  const canSubmit = canConfirmHandover(request.status) && trimmed.length >= 5 &&
    trimmed.length <= REFUSE_REASON_LIMIT;

  const submit = async () => {
    if (inFlight.current || !canSubmit) return;
    inFlight.current = true;
    setSubmitting(true);
    setError('');
    try {
      const evidenceMediaIds = evidence.length ? await uploadEvidence(evidence) : undefined;
      onDone(
        await requestApi.refuseHandover(request.id, currentUserId, { reason: trimmed, evidenceMediaIds }),
      );
    } catch (caught) {
      setError(handoverOutcomeError(caught));
      if ((caught as ApiError).status === 409) onStale();
    } finally {
      inFlight.current = false;
      setSubmitting(false);
    }
  };

  return (
    <DialogFrame labelledBy="refuse-handover-title" busy={submitting} onClose={onClose}>
      <h2 id="refuse-handover-title" className="text-xl font-bold">
        Báo cáo vi phạm
      </h2>
      <p className="mt-1 text-sm text-text-muted">{itemTitle}</p>
      <p className="mt-3 text-sm leading-6 text-text-secondary">{REFUSE_HANDOVER_CONSEQUENCE}</p>
      <dl className="mt-4 text-sm">
        <dt className="font-semibold">Lý do</dt>
        <dd className="mt-1 text-text-secondary">{REFUSE_HANDOVER_REASON_LABEL}</dd>
      </dl>
      <TextArea
        className="mt-4"
        label="Mô tả (bắt buộc)"
        hint={`Nêu rõ món đồ của ${partnerName} khác mô tả ở điểm nào. Nhập ít nhất 5 ký tự.`}
        value={reason}
        maxLength={REFUSE_REASON_LIMIT}
        disabled={submitting}
        onChange={(event) => setReason(event.target.value)}
      />
      <p className="mt-1 text-right text-xs text-text-muted">
        {reason.length}/{REFUSE_REASON_LIMIT}
      </p>
      <div className="mt-4">
        <ReportEvidenceField files={evidence} onChange={setEvidence} disabled={submitting} />
      </div>
      {error ? (
        <div role="alert" className="mt-4 rounded-md bg-error-soft p-3 text-xs text-error">
          {error}
        </div>
      ) : null}
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="outline" disabled={submitting} onClick={onClose}>
          Đóng
        </Button>
        <Button variant="warning" disabled={submitting || !canSubmit} onClick={() => void submit()}>
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
        <Button variant="outline" disabled={submitting} onClick={onClose}>
          Hủy
        </Button>
        <Button disabled={submitting || !reason} onClick={() => void submit()}>
          {submitting ? 'Đang gửi...' : 'Gửi khiếu nại'}
        </Button>
      </div>
    </DialogFrame>
  );
}
