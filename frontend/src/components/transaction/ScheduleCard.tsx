import { useEffect, useState } from 'react';
import { Alert, Button, Field } from '../ui';
import {
  requestApi,
  type RequestContactResponse,
  type RequestResponse,
} from '../../services/requestApi';
import type { ApiError } from '../../services/http';
import { canSchedule, formatDateTime } from '../../utils/request';
import {
  canConfirmMeetingSchedule,
  minimumMeetingTimeValue,
  parseLocalMeetingTime,
  scheduleApiErrorMessage,
  toLocalMeetingTimeValue,
  validateLocalMeetingTime,
  validateMeetingPlace,
  validateStoredMeetingTime,
} from '../../utils/meetingSchedule';

interface ScheduleCardProps {
  request: RequestResponse;
  currentUserId: number;
  viewerIsDonor: boolean;
  partnerName: string;
  onUpdated: (request: RequestResponse) => void;
}

/**
 * Khối "Lịch giao nhận". Chưa chốt lịch thì là form chọn và xác nhận lịch; đã chốt (contactRevealedAt) thì chỉ
 * hiện thời gian, địa điểm và nút xem số điện thoại.
 */
export function ScheduleCard(props: ScheduleCardProps) {
  const { request } = props;
  const editable = canSchedule(request.status) && !request.contactRevealedAt;
  if (!editable && !request.meetingTime && !request.meetingPlace) return null;

  return (
    <section className="card space-y-4 p-5 sm:p-6" aria-labelledby="meeting-title">
      <h2 id="meeting-title" className="section-title">
        Lịch giao nhận
      </h2>
      {editable ? <ScheduleForm {...props} /> : <ScheduleSummary {...props} />}
    </section>
  );
}

function ScheduleSummary({ request, currentUserId }: ScheduleCardProps) {
  const [contact, setContact] = useState<RequestContactResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const loadContact = async () => {
    setLoading(true);
    setError('');
    try {
      setContact(await requestApi.getContact(request.id, currentUserId));
    } catch (caught) {
      setError((caught as ApiError).message || 'Chưa thể xem thông tin liên lạc.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <dl className="grid gap-3 text-sm sm:grid-cols-2">
        <div>
          <dt className="label">Thời gian</dt>
          <dd>{request.meetingTime ? formatDateTime(request.meetingTime) : 'Chưa có'}</dd>
        </div>
        <div>
          <dt className="label">
            {request.deliveryMethod === 'SHIPPING' ? 'Địa chỉ nhận hàng' : 'Địa điểm'}
          </dt>
          <dd className="break-words">{request.meetingPlace || 'Chưa có'}</dd>
        </div>
      </dl>
      {request.contactRevealedAt ? (
        contact ? (
          <div className="rounded-md px-3.5 py-3 text-sm leading-6 ring-1 ring-border">
            <strong className="block">Liên hệ {contact.fullName}</strong>
            <span className="block">{contact.phone}</span>
            <span className="block text-text-secondary">{contact.email}</span>
          </div>
        ) : (
          <Button variant="outline" disabled={loading} onClick={() => void loadContact()}>
            Xem số điện thoại
          </Button>
        )
      ) : null}
      {error ? <Alert tone="error">{error}</Alert> : null}
    </>
  );
}

function ScheduleForm({
  request,
  currentUserId,
  viewerIsDonor,
  partnerName,
  onUpdated,
}: ScheduleCardProps) {
  const [deliveryMethod, setDeliveryMethod] = useState<'IN_PERSON' | 'SHIPPING'>('IN_PERSON');
  const [meetingPlace, setMeetingPlace] = useState('');
  const [meetingTime, setMeetingTime] = useState('');
  const [agreeToWaiver, setAgreeToWaiver] = useState(false);
  const [now, setNow] = useState(() => new Date());
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  // Lịch đã lưu đổi (do mình lưu hoặc bên kia lưu) thì đưa form về đúng lịch đó.
  useEffect(() => {
    setMeetingTime(request.meetingTime ? toLocalMeetingTimeValue(request.meetingTime) : '');
    setMeetingPlace(request.meetingPlace ?? '');
    setDeliveryMethod(request.deliveryMethod === 'SHIPPING' ? 'SHIPPING' : 'IN_PERSON');
    setAgreeToWaiver(false);
  }, [request.meetingTime, request.meetingPlace, request.deliveryMethod]);

  // Cập nhật mốc "bây giờ" mỗi 30 giây để kiểm giờ hẹn không lùi về quá khứ.
  useEffect(() => {
    const intervalId = window.setInterval(() => setNow(new Date()), 30_000);
    return () => window.clearInterval(intervalId);
  }, []);

  const currentPartyConfirmedAt = viewerIsDonor
    ? request.donorScheduleOkAt
    : request.receiverScheduleOkAt;
  const otherPartyConfirmedAt = viewerIsDonor
    ? request.receiverScheduleOkAt
    : request.donorScheduleOkAt;
  const placeError = validateMeetingPlace(meetingPlace);
  const timeError = validateLocalMeetingTime(meetingTime, now);
  const hasUnsavedSchedule =
    meetingPlace.trim() !== (request.meetingPlace ?? '').trim() ||
    meetingTime !== (request.meetingTime ? toLocalMeetingTimeValue(request.meetingTime) : '') ||
    deliveryMethod !== request.deliveryMethod;
  const savedScheduleValid =
    !validateMeetingPlace(request.meetingPlace ?? '') &&
    !validateStoredMeetingTime(request.meetingTime, now);
  const canConfirm =
    request.status === 'AWAITING_LOGISTICS' &&
    canConfirmMeetingSchedule({
      meetingPlace,
      localMeetingTime: meetingTime,
      storedMeetingTime: request.meetingTime,
      agreed: agreeToWaiver,
      hasUnsavedChanges: hasUnsavedSchedule,
      now,
    }) &&
    savedScheduleValid &&
    !currentPartyConfirmedAt &&
    !submitting;

  const saveSchedule = async () => {
    const checkNow = new Date();
    const parsedMeetingTime = parseLocalMeetingTime(meetingTime);
    const validationError =
      validateMeetingPlace(meetingPlace) || validateLocalMeetingTime(meetingTime, checkNow);
    if (validationError || !parsedMeetingTime) {
      setNow(checkNow);
      setError(validationError || 'Thời gian giao nhận không hợp lệ.');
      return;
    }
    setSubmitting(true);
    setError('');
    try {
      onUpdated(
        await requestApi.updateSchedule(request.id, currentUserId, {
          deliveryMethod,
          meetingPlace: meetingPlace.trim(),
          meetingTime: parsedMeetingTime.toISOString(),
          agreeToWaiver,
        }),
      );
    } catch (caught) {
      setError(scheduleApiErrorMessage(caught, 'Không cập nhật được lịch giao nhận.'));
    } finally {
      setSubmitting(false);
    }
  };

  const confirmSchedule = async () => {
    if (!canConfirm) return;
    setSubmitting(true);
    setError('');
    try {
      onUpdated(await requestApi.confirmLogistics(request.id, currentUserId, { agreeToWaiver }));
    } catch (caught) {
      setError(scheduleApiErrorMessage(caught, 'Không xác nhận được lịch giao nhận.'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-3">
      <p className="text-xs leading-5 text-text-secondary">
        Một bên lưu lịch, cả hai cùng xác nhận. Khi đó hai bên mới thấy số điện thoại của nhau.
      </p>
      {otherPartyConfirmedAt ? (
        <Alert tone="info">{partnerName} đã xác nhận lịch đang lưu.</Alert>
      ) : null}
      <fieldset className="grid grid-cols-2 gap-2">
        <legend className="label">Hình thức</legend>
        {(
          [
            ['IN_PERSON', 'Gặp trực tiếp', 'Hẹn tại chỗ công cộng'],
            ['SHIPPING', 'Giao hàng', 'Gửi qua đơn vị vận chuyển'],
          ] as const
        ).map(([value, label, hint]) => (
          <button
            key={value}
            type="button"
            aria-pressed={deliveryMethod === value}
            disabled={submitting}
            onClick={() => setDeliveryMethod(value)}
            className={`flex min-h-11 flex-col gap-0.5 rounded-md border p-2.5 text-left transition disabled:opacity-40 ${deliveryMethod === value ? 'border-primary bg-primary-faint' : 'border-border hover:bg-primary-faint'}`}
          >
            <strong className="text-sm">{label}</strong>
            <span className="text-2xs text-text-secondary">{hint}</span>
          </button>
        ))}
      </fieldset>
      <div className="grid gap-3 sm:grid-cols-2">
        <Field
          label={deliveryMethod === 'SHIPPING' ? 'Địa chỉ nhận hàng' : 'Địa điểm gặp'}
          value={meetingPlace}
          maxLength={255}
          error={meetingPlace ? (placeError ?? undefined) : undefined}
          disabled={submitting}
          onChange={(event) => {
            setMeetingPlace(event.target.value);
            setError('');
          }}
        />
        <Field
          label="Thời gian"
          type="datetime-local"
          value={meetingTime}
          min={minimumMeetingTimeValue(now)}
          error={meetingTime ? (timeError ?? undefined) : undefined}
          disabled={submitting}
          onChange={(event) => {
            setMeetingTime(event.target.value);
            setNow(new Date());
            setError('');
          }}
        />
      </div>
      <label className="flex min-h-11 items-start gap-2 text-xs leading-5 text-text-secondary">
        <input
          type="checkbox"
          className="mt-0.5 size-4 accent-primary"
          checked={agreeToWaiver}
          disabled={submitting}
          onChange={(event) => setAgreeToWaiver(event.target.checked)}
        />
        <span>Tôi đồng ý cam kết trách nhiệm giao nhận.</span>
      </label>
      {error ? (
        <div role="alert">
          <Alert tone="error">{error}</Alert>
        </div>
      ) : null}
      <div className="flex flex-wrap gap-2">
        <Button
          variant="outline"
          disabled={submitting || Boolean(placeError) || Boolean(timeError)}
          onClick={() => void saveSchedule()}
        >
          Lưu lịch hẹn
        </Button>
        <Button disabled={!canConfirm} onClick={() => void confirmSchedule()}>
          {currentPartyConfirmedAt ? 'Đã xác nhận lịch' : 'Xác nhận lịch'}
        </Button>
      </div>
    </div>
  );
}
