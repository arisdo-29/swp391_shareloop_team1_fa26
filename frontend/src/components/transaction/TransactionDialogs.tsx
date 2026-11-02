import { useState } from 'react';
import { Button, Field, Select, TextArea } from '../ui';
import { DialogFrame } from '../Dialog';
import { requestApi, type RequestResponse } from '../../services/requestApi';
import { mediaApi } from '../../services/mediaApi';
import { openDispute, type ReportReason } from '../../services/reportApi';
import type { ApiError } from '../../services/http';
import { disputeErrorMessage, disputeReasonLabels } from '../../utils/disputeErrors';
import { formatDateTime } from '../../utils/request';

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
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const submit = async () => {
    const trimmed = note.trim();
    if (submitting || trimmed.length < 5) return;
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
        {partnerName} đã xác nhận bàn giao nhưng phía bạn thì chưa xong. Giao dịch sẽ chuyển sang
        khiếu nại để Admin xem xét.
      </p>
      <TextArea
        className="mt-4"
        label="Mô tả sự cố (bắt buộc)"
        value={note}
        maxLength={1000}
        disabled={submitting}
        onChange={(event) => setNote(event.target.value)}
      />
      <p className="mt-1 text-right text-xs text-text-muted">{note.length}/1000</p>
      <Field
        className="mt-4"
        label="Ảnh minh chứng"
        type="file"
        accept="image/*"
        multiple
        disabled={submitting}
        onChange={(event) => setEvidence(Array.from(event.target.files ?? []))}
      />
      {evidence.length ? (
        <p className="mt-2 text-xs text-text-muted">Đã chọn {evidence.length} ảnh.</p>
      ) : null}
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
}: Omit<DialogBaseProps, 'currentUserId'> & { onSubmitted: (notice: string) => void }) {
  const [reason, setReason] = useState<ReportReason | ''>('');
  const [note, setNote] = useState('');
  const [evidence, setEvidence] = useState<File[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const submit = async () => {
    if (!reason || submitting || request.status !== 'COMPLETED') return;
    setSubmitting(true);
    setError('');
    try {
      const evidenceMediaIds = evidence.length ? await uploadEvidence(evidence) : undefined;
      const response = await openDispute(request.id, {
        reason,
        note: note.trim() || undefined,
        evidenceMediaIds,
      });
      onSubmitted(
        `Khiếu nại của bạn đã được gửi. Đối tác có 48 giờ để giải trình (hạn: ${formatDateTime(response.responseDueAt)}).`,
      );
    } catch (caught) {
      setError(disputeErrorMessage(caught));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <DialogFrame labelledBy="dispute-title" busy={submitting} onClose={onClose}>
      <h2 id="dispute-title" className="text-xl font-bold">
        Gửi khiếu nại giao dịch
      </h2>
      <p className="mt-1 text-sm text-text-muted">{itemTitle}</p>
      <div className="mt-3 rounded-md bg-warning-soft p-3 text-xs text-warning">
        Lưu ý: Thời hạn gửi khiếu nại là 24 giờ kể từ lúc hoàn tất giao dịch. Sau khi bạn gửi khiếu
        nại, đối tác sẽ có 48 giờ để gửi giải trình kèm minh chứng đối chất.
      </div>
      <Select
        className="mt-5"
        label="Lý do"
        value={reason}
        disabled={submitting}
        onChange={(event) => setReason(event.target.value as ReportReason | '')}
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
        label="Nội dung (không bắt buộc)"
        value={note}
        maxLength={500}
        disabled={submitting}
        onChange={(event) => setNote(event.target.value)}
      />
      <p className="mt-1 text-right text-xs text-text-muted">{note.length}/500</p>
      <Field
        className="mt-4"
        label="Minh chứng"
        type="file"
        accept="image/*"
        multiple
        disabled={submitting}
        onChange={(event) => setEvidence(Array.from(event.target.files ?? []))}
      />
      {evidence.length ? (
        <p className="mt-2 text-xs text-text-muted">Đã chọn {evidence.length} ảnh.</p>
      ) : null}
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
