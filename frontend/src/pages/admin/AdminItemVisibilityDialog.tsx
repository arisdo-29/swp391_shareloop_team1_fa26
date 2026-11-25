import { useRef, useState } from 'react';
import { Dialog } from '../../components/Dialog';
import { Button, TextArea } from '../../components/ui';
import { adminItemApi, type AdminItemFullDetail } from '../../services/adminItemApi';
import { adminErrorMessage } from './adminErrors';

const REASON_MIN = 5;
const REASON_MAX = 500;

/**
 * Hộp thoại ẩn hoặc hiện lại một bài, bắt buộc có lý do. Dùng chung cho trang danh sách và trang chi tiết. Backend
 * kiểm lại mọi điều kiện (bài có giao dịch đang chạy, chủ bài bị khoá) và trả câu lỗi tiếng Việt để hiện tại chỗ.
 */
export function AdminItemVisibilityDialog({
  itemId,
  itemTitle,
  mode,
  onClose,
  onDone,
}: {
  itemId: number;
  itemTitle: string;
  mode: 'hide' | 'unhide';
  onClose: () => void;
  onDone: (detail: AdminItemFullDetail) => void;
}) {
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const inFlight = useRef(false);
  const trimmed = reason.trim();
  const hiding = mode === 'hide';

  const submit = async () => {
    if (inFlight.current) return;
    if (trimmed.length < REASON_MIN) {
      setError(`Lý do cần từ ${REASON_MIN} đến ${REASON_MAX} ký tự.`);
      return;
    }
    inFlight.current = true;
    setBusy(true);
    setError('');
    try {
      const detail = hiding
        ? await adminItemApi.hide(itemId, trimmed)
        : await adminItemApi.unhide(itemId, trimmed);
      onDone(detail);
    } catch (caught) {
      setError(adminErrorMessage(caught, 'Không thực hiện được. Vui lòng thử lại.'));
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  };

  return (
    <Dialog title={hiding ? 'Ẩn bài đăng' : 'Hiện lại bài đăng'} busy={busy} onClose={onClose}>
      <p className="mt-3 text-sm text-text-secondary">
        {hiding
          ? `Bài «${itemTitle}» sẽ không còn hiện cho người dùng. Các yêu cầu đang chờ tới bài này tự huỷ, chủ bài nhận thông báo kèm lý do và phí đăng không được hoàn.`
          : `Bài «${itemTitle}» sẽ hiện lại như cũ nếu còn trong hạn hiển thị. Chủ bài nhận thông báo kèm ghi chú.`}
      </p>
      <TextArea
        className="mt-4"
        label={hiding ? 'Lý do ẩn (bắt buộc)' : 'Lý do hiện lại (bắt buộc)'}
        value={reason}
        maxLength={REASON_MAX}
        disabled={busy}
        onChange={(event) => setReason(event.target.value)}
      />
      <p className="mt-1 text-right text-xs text-text-muted">
        {reason.length}/{REASON_MAX}
      </p>
      {error ? (
        <div role="alert" className="mt-3 rounded-md bg-error-soft p-3 text-xs text-error">
          {error}
        </div>
      ) : null}
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="outline" disabled={busy} onClick={onClose}>
          Đóng
        </Button>
        <Button
          variant={hiding ? 'danger' : 'primary'}
          disabled={busy || trimmed.length < REASON_MIN}
          onClick={() => void submit()}
        >
          {busy ? 'Đang xử lý...' : hiding ? 'Ẩn bài' : 'Hiện lại'}
        </Button>
      </div>
    </Dialog>
  );
}
