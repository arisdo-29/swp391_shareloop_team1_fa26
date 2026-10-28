import { useRef, useState } from 'react';
import type { ApiError } from '../services/http';
import { reportApi, type ReportReason, type ReportTarget } from '../services/reportApi';
import { Button, IconButton, Select, TextArea } from './ui';
import { DialogFrame } from './Dialog';
import { mediaApi } from '../services/mediaApi';

const reportReasonLabels: Record<ReportReason, string> = {
  NOT_AS_DESCRIBED: 'Không đúng mô tả',
  COUNTERFEIT: 'Hàng giả hoặc hàng nhái',
  PROHIBITED_ITEM: 'Mặt hàng bị cấm',
  NO_SHOW: 'Không đến theo lịch hẹn',
  WRONG_CONTACT_INFO: 'Thông tin liên hệ không đúng',
  HARASSMENT: 'Quấy rối',
  OTHER: 'Lý do khác',
};

const REPORT_DESCRIPTION_MAX_LENGTH = 2000;

function reportErrorMessage(error: ApiError) {
  if (error.status === 400) return 'Thông tin báo cáo chưa hợp lệ. Vui lòng kiểm tra lại.';
  if (error.status === 404) return 'Không tìm thấy đối tượng cần báo cáo.';
  if (error.status === 409) return 'Bạn không thể tự báo cáo mình hoặc đã báo cáo đối tượng này trước đó.';
  if (error.status === 401 || error.status === 403) return 'Phiên đăng nhập không còn hợp lệ. Vui lòng đăng nhập lại.';
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
    if (!reason || submitInFlight.current) return;
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
    <DialogFrame>
      <div className="flex items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold">Gửi báo cáo vi phạm</h2>
          <p className="mt-1 text-sm text-text-muted">{contextLabel} sẽ được chuyển đến quản trị viên xem xét.</p>
        </div>
        <IconButton icon="close" label="Đóng" onClick={onClose} disabled={submitting} />
      </div>
      <Select
        className="mt-5"
        label="Lý do"
        value={reason}
        onChange={(event) => setReason(event.target.value as ReportReason | '')}
        disabled={submitting}
      >
        <option value="">Chọn lý do báo cáo</option>
        {Object.entries(reportReasonLabels).map(([value, label]) => (
          <option key={value} value={value}>{label}</option>
        ))}
      </Select>
      <TextArea
        className="mt-4"
        label="Mô tả chi tiết (không bắt buộc)"
        value={description}
        maxLength={REPORT_DESCRIPTION_MAX_LENGTH}
        onChange={(event) => setDescription(event.target.value)}
        disabled={submitting}
      />
      <p className="mt-1 text-right text-xs text-text-muted">{description.length}/{REPORT_DESCRIPTION_MAX_LENGTH}</p>
      <label className="mt-4 block">
        <span className="label">Ảnh minh chứng (không bắt buộc)</span>
        <input
          className="input"
          type="file"
          accept="image/*"
          multiple
          disabled={submitting}
          onChange={(event) => setEvidenceFiles(Array.from(event.target.files ?? []))}
        />
      </label>
      {evidenceFiles.length ? (
        <p className="mt-2 text-xs text-text-muted">Đã chọn {evidenceFiles.length} ảnh minh chứng.</p>
      ) : null}
      {error ? <div className="mt-3 rounded-md bg-red-50 p-3 text-xs text-error">{error}</div> : null}
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="ghost" onClick={onClose} disabled={submitting}>Hủy</Button>
        <Button onClick={() => void submit()} disabled={submitting || !reason}>
          {submitting ? 'Đang gửi...' : 'Gửi báo cáo'}
        </Button>
      </div>
    </DialogFrame>
  );
}
