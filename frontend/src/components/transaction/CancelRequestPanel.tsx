import { useState } from 'react';
import { Alert, Button } from '../ui';
import { requestApi, type RequestResponse } from '../../services/requestApi';
import type { ApiError } from '../../services/http';
import { formatDeadline } from '../../utils/request';

interface CancelRequestPanelProps {
  request: RequestResponse;
  currentUserId: number;
  partnerName: string;
  onUpdated: (request: RequestResponse) => void;
  onOpenReject: () => void;
}

// Lý do ghép từ nhãn của mã và chữ người dùng nhập thêm, backend đã trả nhãn tiếng Việt.
function reasonText(request: RequestResponse) {
  const parts = [request.cancelRequestLabel, request.cancelRequestReason].filter(Boolean);
  return parts.join('. ');
}

/**
 * Thẻ đề nghị huỷ đang chờ phản hồi (GN-06). Người đề nghị thấy dòng chờ và nút rút lại, bên kia thấy hai nút Đồng ý
 * và Không đồng ý. Hạn phản hồi do backend tính, ở đây chỉ hiện.
 */
export function CancelRequestPanel({
  request,
  currentUserId,
  partnerName,
  onUpdated,
  onOpenReject,
}: CancelRequestPanelProps) {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  if (!request.cancelRequestedAt) return null;

  const requesterIsMe = request.cancelRequestedBy === currentUserId;
  const deadline = request.cancelRequestExpiresAt ? formatDeadline(request.cancelRequestExpiresAt) : '';
  const deadlineText = deadline ? `, hạn ${deadline}` : '';

  const run = async (action: () => Promise<RequestResponse>) => {
    if (submitting) return;
    setSubmitting(true);
    setError('');
    try {
      onUpdated(await action());
    } catch (caught) {
      setError((caught as ApiError).message || 'Không xử lý được đề nghị huỷ. Vui lòng tải lại.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-3" data-testid="cancel-request-panel">
      {requesterIsMe ? (
        <>
          <Alert tone="warning">
            Đang chờ {partnerName} phản hồi đề nghị huỷ{deadlineText}. Lý do của bạn: {reasonText(request)}.
          </Alert>
          <Button
            variant="outline"
            disabled={submitting}
            onClick={() => void run(() => requestApi.withdrawCancelRequest(request.id, currentUserId))}
          >
            Rút lại đề nghị
          </Button>
          <p className="text-xs leading-5 text-text-secondary">
            Rút lại thì giao dịch tiếp tục như thường.
          </p>
        </>
      ) : (
        <>
          <Alert tone="warning">
            {partnerName} đề nghị huỷ giao dịch vì {reasonText(request)}. Bạn có đồng ý không?
            {deadline ? ` Hạn phản hồi ${deadline}.` : ''}
          </Alert>
          <div className="flex flex-wrap gap-2">
            <Button
              disabled={submitting}
              onClick={() => void run(() => requestApi.acceptCancelRequest(request.id, currentUserId))}
            >
              Đồng ý huỷ
            </Button>
            <Button variant="warning" disabled={submitting} onClick={onOpenReject}>
              Không đồng ý
            </Button>
          </div>
          <p className="text-xs leading-5 text-text-secondary">
            Đồng ý hoặc im lặng thì giao dịch huỷ và không bên nào bị trừ sao. Không đồng ý thì giao dịch chuyển sang
            sự cố giao nhận, chờ Admin xác minh.
          </p>
        </>
      )}
      {error ? (
        <div role="alert">
          <Alert tone="error">{error}</Alert>
        </div>
      ) : null}
    </div>
  );
}
