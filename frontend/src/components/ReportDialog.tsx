import './complaints.css';
import { useRef, useState } from 'react';
import type { ApiError } from '../services/http';
import { reportApi, type ReportReason, type ReportTarget } from '../services/reportApi';
import { Alert, Button, IconButton, Select, TextArea } from './ui';
import { ReportEvidenceField } from './ReportEvidenceField';
import { DialogFrame } from './Dialog';
import { mediaApi } from '../services/mediaApi';
import { disputeReasonLabels } from '../utils/disputeErrors';

const REPORT_DESCRIPTION_MAX_LENGTH = 2000;

function reportErrorMessage(error: ApiError) {
  if (error.status === 400) return 'Thông tin báo cáo chưa hợp lệ. Vui lòng kiểm tra lại.';
  if (error.status === 404) return 'Không tìm thấy đối tượng cần báo cáo.';
  if (error.status === 409)
    return 'Bạn không thể tự báo cáo mình hoặc đã báo cáo đối tượng này trước đó.';
  if (error.status === 401 || error.status === 403)
    return 'Phiên đăng nhập không còn hợp lệ. Vui lòng đăng nhập lại.';
  if (error.status === 0) return 'Không kết nối được máy chủ. Vui lòng thử lại.';
  return 'Không gửi được báo cáo. Vui lòng thử lại.';
}

interface ReportDialogProps {
  target: ReportTarget;
  contextLabel: string;
  onClose: () => void;
  onSuccess: () => void;
}

export function ReportDialog({ target, contextLabel, onClose, onSuccess }: ReportDialogProps) {
  const [reason, setReason] = useState<ReportReason | ''>('');
  const [description, setDescription] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [evidenceFiles, setEvidenceFiles] = useState<File[]>([]);
  const submitInFlight = useRef(false);

  const submit = async () => {
    if (!reason || !description.trim() || submitInFlight.current) return;
    submitInFlight.current = true;
    setSubmitting(true);
    setError('');
    try {
      const uploads = evidenceFiles.length
        ? await Promise.all(evidenceFiles.map((file) => mediaApi.upload(file)))
        : [];
      await reportApi.create({
        ...target,
        reason,
        description: description.trim(),
        evidenceMediaIds: uploads.length ? uploads.map((entry) => entry.mediaId) : undefined,
      });
      onSuccess();
    } catch (caught) {
      setError(reportErrorMessage(caught as ApiError));
    } finally {
      submitInFlight.current = false;
      setSubmitting(false);
    }
  };

  return (
    <DialogFrame className="complaint-create-dialog" labelledBy="report-dialog-title" busy={submitting} onClose={onClose}>
      <div className="flex items-center justify-between gap-4">
        <div className="min-w-0">
          <p className="complaint-eyebrow">GỬI PHẢN ÁNH</p>
          <h2 id="report-dialog-title" className="text-xl font-bold">
            Gửi báo cáo vi phạm
          </h2>
          <p className="mt-1 text-sm text-text-muted">{contextLabel}</p>
        </div>
        <IconButton icon="close" label="Đóng" onClick={onClose} disabled={submitting} />
      </div>
      <div className="mt-4">
        <Alert tone="info">Quản trị viên sẽ xem nội dung và ảnh đính kèm.</Alert>
      </div>
      <Select
        className="mt-5"
        label="Lý do"
        value={reason}
        onChange={(event) => setReason(event.target.value as ReportReason | '')}
        disabled={submitting}
      >
        <option value="">Chọn lý do báo cáo</option>
        {Object.entries(disputeReasonLabels).map(([value, label]) => (
          <option key={value} value={value}>
            {label}
          </option>
        ))}
      </Select>
      <TextArea
        className="mt-4"
        label="Mô tả chi tiết (bắt buộc)"
        hint="Cho biết nội dung nào vi phạm và vì sao bạn báo cáo."
        required
        value={description}
        maxLength={REPORT_DESCRIPTION_MAX_LENGTH}
        onChange={(event) => setDescription(event.target.value)}
        disabled={submitting}
      />
      <p className="mt-1 text-right text-xs text-text-muted">
        {description.length}/{REPORT_DESCRIPTION_MAX_LENGTH}
      </p>
      <div className="mt-4">
        <ReportEvidenceField
          files={evidenceFiles}
          onChange={setEvidenceFiles}
          disabled={submitting}
        />
      </div>
      {error ? (
        <div className="mt-3 rounded-md bg-error-soft p-3 text-xs text-error">{error}</div>
      ) : null}
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="ghost" onClick={onClose} disabled={submitting}>
          Hủy
        </Button>
        <Button
          onClick={() => void submit()}
          disabled={submitting || !reason || !description.trim()}
        >
          {submitting ? 'Đang gửi...' : 'Gửi báo cáo'}
        </Button>
      </div>
    </DialogFrame>
  );
}
