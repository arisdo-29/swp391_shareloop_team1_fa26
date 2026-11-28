import { useState } from 'react';
import { Alert, Button, Field } from '../ui';
import { DialogFrame } from '../Dialog';
import { requestApi, type RequestResponse } from '../../services/requestApi';
import type { ApiError } from '../../services/http';
import { formatDateTime, formatDeadline } from '../../utils/request';
import { isBeforeMeetingTime } from '../../utils/handoverOutcome';
import { anyLegShipped } from '../../utils/shipping';
import { usePublicConfig } from '../../utils/usePublicConfig';
import {
  maximumMeetingTimeValue,
  minimumMeetingTimeValue,
  parseLocalMeetingTime,
  scheduleApiErrorMessage,
  validateExchangeShippingMeeting,
  validateLocalMeetingTime,
  validateMeetingPlace,
} from '../../utils/meetingSchedule';

interface ReschedulePanelProps {
  request: RequestResponse;
  currentUserId: number;
  partnerName: string;
  onUpdated: (request: RequestResponse) => void;
}

/**
 * Dời lịch sau khi đã chốt (GN-11): nút đề nghị cùng số lần còn lại, thẻ chờ phản hồi và hai nút Đồng ý hoặc Từ chối
 * cho bên kia. Hạn phản hồi và số lần đã dời do backend trả, ở đây chỉ hiện.
 */
export function ReschedulePanel({ request, currentUserId, partnerName, onUpdated }: ReschedulePanelProps) {
  const [dialogOpen, setDialogOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const config = usePublicConfig();

  const open = request.status === 'LOGISTICS_CONFIRMED' || request.status === 'AWAITING_HANDOVER';
  if (!open || !request.contactRevealedAt) return null;

  const pending = Boolean(request.rescheduleProposedAt);
  const proposerIsMe = request.rescheduleProposedBy === currentUserId;
  const usedCount = request.rescheduleCount ?? 0;
  const remaining = Math.max(0, config.reschedule_max_count - usedCount);
  const someoneCompleted = Boolean(request.donorHandoverAt || request.receiverHandoverAt);
  const beforeHandoverStart = request.deliveryMethod === 'SHIPPING'
    ? !anyLegShipped(request)
    : isBeforeMeetingTime(request.meetingTime);
  const canPropose =
    !pending &&
    !request.cancelRequestedAt &&
    !someoneCompleted &&
    beforeHandoverStart &&
    remaining > 0;

  const run = async (action: () => Promise<RequestResponse>) => {
    if (submitting) return;
    setSubmitting(true);
    setError('');
    try {
      onUpdated(await action());
    } catch (caught) {
      setError((caught as ApiError).message || 'Không xử lý được đề nghị dời lịch. Vui lòng tải lại.');
    } finally {
      setSubmitting(false);
    }
  };

  const proposedText = request.rescheduleMeetingTime ? formatDateTime(request.rescheduleMeetingTime) : '';
  const placeText = request.rescheduleMeetingPlace ? ` tại ${request.rescheduleMeetingPlace}` : '';
  const deadline = request.rescheduleExpiresAt ? formatDeadline(request.rescheduleExpiresAt) : '';

  return (
    <div className="space-y-3" data-testid="reschedule-panel">
      {pending && proposerIsMe ? (
        <Alert tone="warning">
          Đang chờ {partnerName} phản hồi đề nghị dời lịch sang {proposedText}
          {placeText}
          {deadline ? `, hạn ${deadline}` : ''}. Hết hạn thì giữ lịch cũ.
        </Alert>
      ) : null}
      {pending && !proposerIsMe ? (
        <>
          <Alert tone="warning">
            {partnerName} đề nghị dời lịch sang {proposedText}
            {placeText}. Bạn có đồng ý không?{deadline ? ` Hạn phản hồi ${deadline}.` : ''}
          </Alert>
          <div className="flex flex-wrap gap-2">
            <Button
              disabled={submitting}
              onClick={() => void run(() => requestApi.acceptReschedule(request.id, currentUserId))}
            >
              Đồng ý dời lịch
            </Button>
            <Button
              variant="outline"
              disabled={submitting}
              onClick={() => void run(() => requestApi.rejectReschedule(request.id, currentUserId))}
            >
              Từ chối
            </Button>
          </div>
          <p className="text-xs leading-5 text-text-secondary">
            Đồng ý thì đổi lịch, thông tin liên hệ vẫn mở và hai bên không phải cam kết lại. Từ chối hoặc im lặng thì
            giữ lịch cũ.
          </p>
        </>
      ) : null}
      {canPropose ? (
        <div className="space-y-1">
          <Button variant="outline" disabled={submitting} onClick={() => setDialogOpen(true)}>
            Đề nghị dời lịch (còn {remaining} lần)
          </Button>
          <p className="text-xs leading-5 text-text-secondary">
            Lịch cũ giữ nguyên cho tới khi {partnerName} đồng ý. Mỗi giao dịch dời tối đa {config.reschedule_max_count}{' '}
            lần.
          </p>
        </div>
      ) : null}
      {error ? (
        <div role="alert">
          <Alert tone="error">{error}</Alert>
        </div>
      ) : null}
      {dialogOpen ? (
        <RescheduleDialog
          request={request}
          currentUserId={currentUserId}
          partnerName={partnerName}
          onClose={() => setDialogOpen(false)}
          onDone={(updated) => {
            setDialogOpen(false);
            onUpdated(updated);
          }}
        />
      ) : null}
    </div>
  );
}

function RescheduleDialog({
  request,
  currentUserId,
  partnerName,
  onClose,
  onDone,
}: Omit<ReschedulePanelProps, 'onUpdated'> & { onClose: () => void; onDone: (request: RequestResponse) => void }) {
  const [meetingTime, setMeetingTime] = useState('');
  const [meetingPlace, setMeetingPlace] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const config = usePublicConfig();
  const [now] = useState(() => new Date());
  const isShipping = request.deliveryMethod === 'SHIPPING';
  const isExchangeShipping = isShipping && Boolean(request.offeredItemId);
  const parsed = parseLocalMeetingTime(meetingTime, isExchangeShipping);
  const timeError = meetingTime
    ? (isExchangeShipping && validateExchangeShippingMeeting(parsed, now, config)) ||
      validateLocalMeetingTime(meetingTime, now, config.meeting_max_days_ahead, isExchangeShipping)
    : 'Vui lòng chọn thời gian mới.';
  // Địa điểm mới không bắt buộc, nhưng đã nhập thì phải hợp lệ.
  const placeError = meetingPlace.trim() ? validateMeetingPlace(meetingPlace) : null;

  const submit = async () => {
    if (submitting || timeError || placeError || !parsed) return;
    const freshError = isExchangeShipping ? validateExchangeShippingMeeting(parsed, new Date(), config) : null;
    if (freshError) {
      setError(freshError);
      return;
    }
    setSubmitting(true);
    setError('');
    try {
      onDone(
        await requestApi.proposeReschedule(request.id, currentUserId, {
          meetingTime: parsed.toISOString(),
          meetingPlace: !isShipping && meetingPlace.trim() ? meetingPlace.trim() : undefined,
        }),
      );
    } catch (caught) {
      setError(scheduleApiErrorMessage(caught, 'Không gửi được đề nghị dời lịch.'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <DialogFrame labelledBy="reschedule-title" busy={submitting} onClose={onClose}>
      <h2 id="reschedule-title" className="text-xl font-bold">
        Đề nghị dời lịch
      </h2>
      <p className="mt-3 text-sm leading-6 text-text-secondary">
        {partnerName} được hỏi có đồng ý không. Lịch cũ giữ nguyên cho tới khi họ đồng ý.
      </p>
      <div className="mt-4 space-y-3">
        <Field
          label={isExchangeShipping ? 'Giờ hẹn gửi hàng mới theo giờ Việt Nam' : isShipping ? 'Ngày gửi hàng dự kiến mới' : 'Thời gian mới'}
          type="datetime-local"
          value={meetingTime}
          min={minimumMeetingTimeValue(now, isExchangeShipping)}
          max={maximumMeetingTimeValue(now, config.meeting_max_days_ahead, isExchangeShipping)}
          hint={`Chỉ hẹn trong vòng ${config.meeting_max_days_ahead} ngày tới.`}
          error={meetingTime ? (timeError ?? undefined) : undefined}
          disabled={submitting}
          onChange={(event) => setMeetingTime(event.target.value)}
        />
        {!isShipping ? (
          <Field
            label="Địa điểm mới (không bắt buộc)"
            value={meetingPlace}
            maxLength={255}
            error={placeError ?? undefined}
            disabled={submitting}
            onChange={(event) => setMeetingPlace(event.target.value)}
          />
        ) : null}
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
        <Button disabled={submitting || Boolean(timeError) || Boolean(placeError)} onClick={() => void submit()}>
          {submitting ? 'Đang gửi...' : 'Gửi đề nghị'}
        </Button>
      </div>
    </DialogFrame>
  );
}
