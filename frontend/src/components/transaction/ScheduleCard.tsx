import { useEffect, useState } from 'react';
import { Alert, Button, Field } from '../ui';
import {
  requestApi,
  type RequestContactResponse,
  type RequestResponse,
} from '../../services/requestApi';
import type { ApiError } from '../../services/http';
import { canSchedule, formatDateTime } from '../../utils/request';
import { usePublicConfig } from '../../utils/usePublicConfig';
import { SCHEDULE_SECTION_ID } from '../../utils/chatMessages';
import { ReschedulePanel } from './ReschedulePanel';
import { ownShippingAddress, partnerAddressProvided, partnerNeedsAddress, viewerNeedsOwnAddress } from '../../utils/shipping';
import {
  canConfirmMeetingSchedule,
  hasConfirmedMeetingSchedule,
  maximumMeetingTimeValue,
  minimumMeetingTimeValue,
  parseLocalMeetingTime,
  scheduleApiErrorMessage,
  toLocalMeetingTimeValue,
  validateLocalMeetingTime,
  validateMeetingPlace,
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
  const { request, currentUserId, viewerIsDonor } = props;
  const editable = canSchedule(request.status) && !request.contactRevealedAt;
  if (!editable && !request.meetingTime && !request.meetingPlace) return null;

  const currentPartyWaiverAt = viewerIsDonor ? request.donorWaiverAt : request.receiverWaiverAt;
  const scheduleKey = JSON.stringify([
    request.id, currentUserId, request.meetingTime, request.meetingPlace,
    request.deliveryMethod, currentPartyWaiverAt,
  ]);

  return (
    <section className="card space-y-4 p-5 sm:p-6" aria-labelledby={SCHEDULE_SECTION_ID}>
      <h2 id={SCHEDULE_SECTION_ID} tabIndex={-1} className="section-title scroll-mt-24">
        Lịch giao nhận
      </h2>
      {editable ? (
        <ScheduleForm key={scheduleKey} {...props} currentPartyWaiverAt={currentPartyWaiverAt} />
      ) : <ScheduleSummary {...props} />}
    </section>
  );
}

function ScheduleSummary(props: ScheduleCardProps) {
  const { request, currentUserId } = props;
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
          <dt className="label">{request.deliveryMethod === 'SHIPPING' ? 'Ngày gửi hàng dự kiến' : 'Thời gian'}</dt>
          <dd>{request.meetingTime ? formatDateTime(request.meetingTime) : 'Chưa có'}</dd>
        </div>
        {request.deliveryMethod === 'SHIPPING' ? null : (
          <div>
            <dt className="label">Địa điểm</dt>
            <dd className="break-words">{request.meetingPlace || 'Chưa có'}</dd>
          </div>
        )}
        {request.deliveryMethod === 'SHIPPING' ? (
          <>
            {request.receiverAddress ? (
              <div>
                <dt className="label">Địa chỉ nhận hàng của người nhận</dt>
                <dd className="break-words">{request.receiverAddress}</dd>
              </div>
            ) : null}
            {request.donorAddress ? (
              <div>
                <dt className="label">Địa chỉ nhận hàng của chủ bài</dt>
                <dd className="break-words">{request.donorAddress}</dd>
              </div>
            ) : null}
          </>
        ) : null}
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
      <ReschedulePanel {...props} />
    </>
  );
}

function ScheduleForm({
  request,
  currentUserId,
  viewerIsDonor,
  partnerName,
  onUpdated,
  currentPartyWaiverAt,
}: ScheduleCardProps & { currentPartyWaiverAt?: string }) {
  const [deliveryMethod, setDeliveryMethod] = useState<'IN_PERSON' | 'SHIPPING'>(
    request.deliveryMethod === 'SHIPPING' ? 'SHIPPING' : 'IN_PERSON',
  );
  const [meetingPlace, setMeetingPlace] = useState(request.meetingPlace ?? '');
  const [shippingAddress, setShippingAddress] = useState(ownShippingAddress(request, viewerIsDonor));
  const [meetingTime, setMeetingTime] = useState(
    () => request.meetingTime ? toLocalMeetingTimeValue(request.meetingTime) : '',
  );
  const [agreeToWaiver, setAgreeToWaiver] = useState(Boolean(currentPartyWaiverAt));
  const [now, setNow] = useState(() => new Date());
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const maxDays = usePublicConfig().meeting_max_days_ahead;

  // Cập nhật mốc "bây giờ" mỗi 30 giây để kiểm giờ hẹn không lùi về quá khứ.
  useEffect(() => {
    const intervalId = window.setInterval(() => setNow(new Date()), 30_000);
    return () => window.clearInterval(intervalId);
  }, []);

  const currentPartyConfirmed = hasConfirmedMeetingSchedule(
    viewerIsDonor ? request.donorScheduleOkAt : request.receiverScheduleOkAt,
    currentPartyWaiverAt,
  );
  const otherPartyConfirmed = hasConfirmedMeetingSchedule(
    viewerIsDonor ? request.receiverScheduleOkAt : request.donorScheduleOkAt,
    viewerIsDonor ? request.receiverWaiverAt : request.donorWaiverAt,
  );
  const isShipping = deliveryMethod === 'SHIPPING';
  const needsOwnAddress = isShipping && viewerNeedsOwnAddress(request, viewerIsDonor);
  // Giao hàng không dùng địa điểm: thay bằng địa chỉ nhận hàng của chính mình khi có chiều nhận (GN-12)
  const placeError = isShipping ? null : validateMeetingPlace(meetingPlace);
  const addressError = needsOwnAddress
    ? shippingAddress.trim().length === 0
      ? 'Vui lòng nhập địa chỉ nhận hàng của bạn.'
      : shippingAddress.length > 255
        ? 'Địa chỉ tối đa 255 ký tự.'
        : null
    : null;
  const timeError = validateLocalMeetingTime(meetingTime, now, maxDays);
  const hasUnsavedSchedule =
    (isShipping
      ? shippingAddress.trim() !== ownShippingAddress(request, viewerIsDonor).trim()
      : meetingPlace.trim() !== (request.meetingPlace ?? '').trim()) ||
    meetingTime !== (request.meetingTime ? toLocalMeetingTimeValue(request.meetingTime) : '') ||
    deliveryMethod !== request.deliveryMethod;
  const canConfirm =
    request.status === 'AWAITING_LOGISTICS' &&
    canConfirmMeetingSchedule({
      meetingPlace: isShipping ? (needsOwnAddress ? shippingAddress : 'Giao hàng') : meetingPlace,
      localMeetingTime: meetingTime,
      storedMeetingTime: request.meetingTime,
      agreed: agreeToWaiver,
      hasUnsavedChanges: hasUnsavedSchedule,
      now,
    }) &&
    !currentPartyConfirmed &&
    !submitting;

  const saveSchedule = async () => {
    if (!agreeToWaiver || submitting) return;

    const checkNow = new Date();
    const parsedMeetingTime = parseLocalMeetingTime(meetingTime);
    const validationError =
      placeError || addressError || validateLocalMeetingTime(meetingTime, checkNow, maxDays);
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
          meetingPlace: isShipping ? undefined : meetingPlace.trim(),
          meetingTime: parsedMeetingTime.toISOString(),
          agreeToWaiver: true,
          shippingAddress: needsOwnAddress ? shippingAddress.trim() : undefined,
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
      onUpdated(
        await requestApi.confirmLogistics(request.id, currentUserId, {
          agreeToWaiver,
          shippingAddress: needsOwnAddress ? shippingAddress.trim() : undefined,
        }),
      );
    } catch (caught) {
      setError(scheduleApiErrorMessage(caught, 'Không xác nhận được lịch giao nhận.'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-3">
      <p className="text-xs leading-5 text-text-secondary">
        Cả hai bên phải đồng ý lịch và cam kết trách nhiệm giao nhận để xem số điện thoại của nhau.
      </p>
      {otherPartyConfirmed ? (
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
            onClick={() => {
              if (value === deliveryMethod) return;
              setDeliveryMethod(value);
              setAgreeToWaiver(false);
            }}
            className={`flex min-h-11 flex-col gap-0.5 rounded-md border p-2.5 text-left transition disabled:opacity-40 ${deliveryMethod === value ? 'border-primary bg-primary-faint' : 'border-border hover:bg-primary-faint'}`}
          >
            <strong className="text-sm">{label}</strong>
            <span className="text-2xs text-text-secondary">{hint}</span>
            {deliveryMethod === value ? (
              <span className="mt-1 text-xs leading-5 text-text-secondary">
                {value === 'IN_PERSON'
                  ? 'Nên quay video kiểm tra tình trạng món đồ ngay tại điểm hẹn, trước khi xác nhận đã nhận. Giữ lại video để đối chiếu nếu cần báo cáo sự cố.'
                  : 'Nên quay video lúc mở kiện và kiểm tra món đồ trước khi xác nhận đã nhận. Giữ lại video, mã vận đơn và trao đổi giao nhận để hỗ trợ khiếu nại hoặc giải trình.'}
              </span>
            ) : null}
          </button>
        ))}
      </fieldset>
      <div className="grid gap-3 sm:grid-cols-2">
        {isShipping ? (
          needsOwnAddress ? (
            <div>
              <Field
                label="Địa chỉ nhận hàng của bạn"
                value={shippingAddress}
                maxLength={255}
                error={shippingAddress ? (addressError ?? undefined) : undefined}
                disabled={submitting}
                onChange={(event) => {
                  setShippingAddress(event.target.value);
                  setAgreeToWaiver(false);
                  setError('');
                }}
              />
              <p className="mt-1 text-xs text-text-muted">{shippingAddress.length}/255</p>
            </div>
          ) : null
        ) : (
          <div>
            <Field
              label="Địa điểm gặp"
              value={meetingPlace}
              maxLength={255}
              error={meetingPlace.length >= 255 ? 'Địa điểm tối đa 255 ký tự.' : meetingPlace ? (placeError ?? undefined) : undefined}
              disabled={submitting}
              onChange={(event) => {
                setMeetingPlace(event.target.value);
                setAgreeToWaiver(false);
                setError('');
              }}
            />
            <p className="mt-1 text-xs text-text-muted">{meetingPlace.length}/255</p>
          </div>
        )}
        <Field
          label={isShipping ? 'Ngày gửi hàng dự kiến' : 'Thời gian'}
          type="datetime-local"
          value={meetingTime}
          min={minimumMeetingTimeValue(now)}
          max={maximumMeetingTimeValue(now, maxDays)}
          hint={`Chỉ hẹn trong vòng ${maxDays} ngày tới.`}
          error={meetingTime ? (timeError ?? undefined) : undefined}
          disabled={submitting}
          onChange={(event) => {
            setMeetingTime(event.target.value);
            setAgreeToWaiver(false);
            setNow(new Date());
            setError('');
          }}
        />
      </div>
      {isShipping && partnerNeedsAddress(request, viewerIsDonor) ? (
        <p className="text-xs leading-5 text-text-secondary">
          Địa chỉ của {partnerName}: {partnerAddressProvided(request, viewerIsDonor) ? 'Đã nhập' : 'Chưa nhập'}. Địa chỉ
          chỉ hiện cho bên kia sau khi hai bên chốt lịch.
        </p>
      ) : null}
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
          disabled={submitting || !agreeToWaiver || Boolean(placeError) || Boolean(addressError) || Boolean(timeError)}
          onClick={() => void saveSchedule()}
        >
          Lưu lịch hẹn
        </Button>
        <Button disabled={!canConfirm} onClick={() => void confirmSchedule()}>
          {currentPartyConfirmed && !hasUnsavedSchedule ? 'Đã xác nhận lịch' : 'Xác nhận lịch'}
        </Button>
      </div>
    </div>
  );
}
