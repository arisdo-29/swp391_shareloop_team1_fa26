import { useState } from 'react';
import { Alert, Button } from '../ui';
import { requestApi, type RequestResponse } from '../../services/requestApi';
import type { ApiError } from '../../services/http';
import { canConfirmHandover, canRefuseHandover } from '../../utils/request';
import { usePublicConfig } from '../../utils/usePublicConfig';
import {
  cancelRequestConsequence,
  handoverFailedConsequence,
  isBeforeMeetingTime,
  REFUSE_HANDOVER_CONSEQUENCE,
} from '../../utils/handoverOutcome';
import { CancelRequestPanel } from './CancelRequestPanel';

interface HandoverCardProps {
  request: RequestResponse;
  currentUserId: number;
  viewerIsDonor: boolean;
  partnerName: string;
  onUpdated: (request: RequestResponse) => void;
  onOpenFailed: () => void;
  onOpenRefuse: () => void;
  onOpenCancelRequest: () => void;
  onOpenRejectCancel: () => void;
}

// Dòng giải thích dưới nút "Đã giao nhận xong". Giao hàng: người gửi bấm khi đã gửi, người nhận bấm khi đã nhận.
function completeHint(isShipping: boolean, viewerIsDonor: boolean, partnerName: string) {
  if (isShipping) {
    return viewerIsDonor
      ? 'Bạn đã gửi hàng. Giao dịch hoàn tất khi người nhận xác nhận đã nhận.'
      : 'Bạn đã nhận được hàng.';
  }
  return `Bạn đã giao nhận xong. Giao dịch hoàn tất khi ${partnerName} cũng xác nhận.`;
}

/**
 * Khối "Giao nhận": chỉ hiện sau khi đã chốt lịch (đã trao liên lạc) và giao dịch đang ở bước giao nhận.
 * Trước giờ hẹn chỉ có "Đề nghị huỷ giao dịch", từ giờ hẹn trở đi mới có ba nút kết quả (GN-06, GN-07).
 */
export function HandoverCard({
  request,
  currentUserId,
  viewerIsDonor,
  partnerName,
  onUpdated,
  onOpenFailed,
  onOpenRefuse,
  onOpenCancelRequest,
  onOpenRejectCancel,
}: HandoverCardProps) {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const config = usePublicConfig();

  if (!canConfirmHandover(request.status) || !request.contactRevealedAt) return null;

  const currentHandoverAt = viewerIsDonor ? request.donorHandoverAt : request.receiverHandoverAt;
  const otherHandoverAt = viewerIsDonor ? request.receiverHandoverAt : request.donorHandoverAt;
  const isShipping = request.deliveryMethod === 'SHIPPING';
  const showRefuse = canRefuseHandover(viewerIsDonor, request.offeredItemId);
  const hasPendingCancel = Boolean(request.cancelRequestedAt);
  const beforeMeeting = isBeforeMeetingTime(request.meetingTime);
  // Gặp trực tiếp: trước giờ hẹn chưa có nút hoàn tất. Giao hàng giữ nút hoàn tất cho tới khi có thẻ giao hàng riêng.
  const showComplete = !beforeMeeting || isShipping;

  const confirmHandover = async () => {
    if (submitting || currentHandoverAt) return;
    setSubmitting(true);
    setError('');
    try {
      onUpdated(await requestApi.confirmHandover(request.id, currentUserId, {}));
    } catch (caught) {
      setError((caught as ApiError).message || 'Không xác nhận được việc giao nhận.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <section className="card space-y-3 p-5 sm:p-6" aria-labelledby="handover-title">
      <h2 id="handover-title" className="section-title">
        Giao nhận
      </h2>
      {hasPendingCancel ? (
        <CancelRequestPanel
          request={request}
          currentUserId={currentUserId}
          partnerName={partnerName}
          onUpdated={onUpdated}
          onOpenReject={onOpenRejectCancel}
        />
      ) : currentHandoverAt ? (
        <div className="space-y-2">
          <Button disabled>Bạn đã xác nhận hoàn tất</Button>
          <p className="text-xs leading-5 text-text-secondary">Đang chờ {partnerName} xác nhận.</p>
        </div>
      ) : (
        <>
          {otherHandoverAt ? (
            <Alert tone="warning">
              {partnerName} đã xác nhận hoàn tất giao nhận. Hãy chọn kết quả phía bạn.
            </Alert>
          ) : null}
          <p className="text-xs font-semibold text-warning">
            Sau khi hoàn tất, bạn chỉ có {config.complaint_window_hours} giờ để khiếu nại.
          </p>
          <ul className="space-y-3">
            {showComplete ? (
              <li className="space-y-1">
                <Button disabled={submitting} onClick={() => void confirmHandover()}>
                  Đã giao nhận xong
                </Button>
                <p className="text-xs leading-5 text-text-secondary">
                  {completeHint(isShipping, viewerIsDonor, partnerName)}
                </p>
              </li>
            ) : null}
            {beforeMeeting ? (
              <li className="space-y-1">
                <Button variant="outline" disabled={submitting} onClick={onOpenCancelRequest}>
                  Đề nghị huỷ giao dịch
                </Button>
                <p className="text-xs leading-5 text-text-secondary">
                  {cancelRequestConsequence(partnerName, config.cancel_request_response_hours)}
                </p>
              </li>
            ) : (
              <>
                <li className="space-y-1">
                  <Button variant="outline" disabled={submitting} onClick={onOpenFailed}>
                    Giao nhận không thành công
                  </Button>
                  <p className="text-xs leading-5 text-text-secondary">
                    {handoverFailedConsequence(
                      Boolean(otherHandoverAt),
                      partnerName,
                      config.response_window_hours,
                      config.cancel_request_response_hours,
                    )}
                  </p>
                </li>
                {showRefuse ? (
                  <li className="space-y-1">
                    <Button variant="warning" disabled={submitting} onClick={onOpenRefuse}>
                      Từ chối giao nhận
                    </Button>
                    <p className="text-xs leading-5 text-text-secondary">{REFUSE_HANDOVER_CONSEQUENCE}</p>
                  </li>
                ) : null}
              </>
            )}
          </ul>
        </>
      )}
      {error ? (
        <div role="alert">
          <Alert tone="error">{error}</Alert>
        </div>
      ) : null}
    </section>
  );
}
