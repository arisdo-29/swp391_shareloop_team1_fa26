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
  hasConfirmedMeetingSchedule,
  maximumMeetingTimeValue,
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
  const { request, currentUserId, viewerIsDonor } = props;
  const [reloadedSchedule, setReloadedSchedule] = useState(false);
  const editable = canSchedule(request.status) && !request.contactRevealedAt;
  if (!editable && !request.meetingTime && !request.meetingPlace) return null;

  const currentPartyWaiverAt = viewerIsDonor ? request.donorWaiverAt : request.receiverWaiverAt;
  const scheduleKey = JSON.stringify([
    request.id, currentUserId, request.scheduleRevision, request.meetingTime, request.meetingPlace,
    request.deliveryMethod, ownShippingAddress(request, viewerIsDonor), currentPartyWaiverAt,
  ]);

  return (
    <section className="card space-y-4 p-5 sm:p-6" aria-labelledby={SCHEDULE_SECTION_ID}>
      <h2 id={SCHEDULE_SECTION_ID} tabIndex={-1} className="section-title scroll-mt-24">
        Lịch giao nhận
      </h2>
      {reloadedSchedule ? (
        <Alert tone="warning">Lịch giao nhận đã thay đổi. Hãy xem lại thông tin mới trước khi xác nhận.</Alert>
      ) : null}
      {editable ? (
        <ScheduleForm
          key={scheduleKey}
          {...props}
          currentPartyWaiverAt={currentPartyWaiverAt}
          onUpdated={(updated) => {
            setReloadedSchedule(false);
            props.onUpdated(updated);
          }}
          onScheduleChanged={(updated) => {
            setReloadedSchedule(true);
            props.onUpdated(updated);
          }}
        />
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
  onScheduleChanged,
  currentPartyWaiverAt,
}: ScheduleCardProps & {
  currentPartyWaiverAt?: string;
  onScheduleChanged: (request: RequestResponse) => void;
}) {
  const savedMethod = request.deliveryMethod === 'SHIPPING' ? 'SHIPPING' : 'IN_PERSON';
  const savedPlace = request.meetingPlace ?? '';
  const savedAddress = ownShippingAddress(request, viewerIsDonor);
  const savedTime = request.meetingTime ? toLocalMeetingTimeValue(request.meetingTime) : '';
  const [deliveryMethod, setDeliveryMethod] = useState<'IN_PERSON' | 'SHIPPING'>(savedMethod);
  const [meetingPlace, setMeetingPlace] = useState(savedPlace);
  const [shippingAddress, setShippingAddress] = useState(savedAddress);
  const [meetingTime, setMeetingTime] = useState(savedTime);
  const [agreeToWaiver, setAgreeToWaiver] = useState(Boolean(currentPartyWaiverAt));
  const [now, setNow] = useState(() => new Date());
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const {
    meeting_max_days_ahead: maxDays,
    shipping_send_deadline_days: sendDays,
    auto_confirm_days_shipping: receiveDays,
  } = usePublicConfig();

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
  const hasSavedSchedule = request.status === 'AWAITING_LOGISTICS' && Boolean(savedTime && request.deliveryMethod);
  const sharedChanges = deliveryMethod !== savedMethod || meetingTime !== savedTime ||
    (!isShipping && meetingPlace.trim() !== savedPlace.trim());
  const addressChanged = needsOwnAddress && shippingAddress.trim() !== savedAddress.trim();
  const proposes = !hasSavedSchedule || sharedChanges ||
    (addressChanged && (Boolean(savedAddress.trim()) || currentPartyConfirmed));
  const waiting = !proposes && currentPartyConfirmed;
  // Giao hàng không dùng địa điểm: thay bằng địa chỉ nhận hàng của chính mình khi có chiều nhận (GN-12)
  const placeError = isShipping ? null : validateMeetingPlace(meetingPlace);
  const addressError = needsOwnAddress
    ? shippingAddress.trim().length === 0
      ? 'Vui lòng nhập địa chỉ nhận hàng của bạn.'
      : shippingAddress.length > 255
        ? 'Địa chỉ tối đa 255 ký tự.'
        : null
    : null;
  const timeError = proposes
    ? validateLocalMeetingTime(meetingTime, now, maxDays)
    : validateStoredMeetingTime(request.meetingTime, now);
  const requirements = [
    placeError ? 'Nhập địa điểm gặp hợp lệ.' : null,
    addressError ? 'Nhập địa chỉ nhận hàng hợp lệ.' : null,
    timeError ? (meetingTime ? timeError : `Chọn ${isShipping ? 'ngày gửi hàng dự kiến' : 'thời gian gặp'}.`) : null,
    agreeToWaiver ? null : 'Đánh dấu cam kết trách nhiệm giao nhận.',
  ].filter((reason) => reason !== null);
  const subject = isShipping ? 'thông tin giao hàng' : 'lịch hẹn';
  const primaryLabel = proposes
    ? `${hasSavedSchedule ? 'Gửi lại' : 'Gửi'} ${subject}`
    : waiting ? `Đã gửi, chờ ${partnerName} đồng ý` : `Đồng ý ${subject}${isShipping ? '' : ' này'}`;

  const discardChanges = () => {
    setDeliveryMethod(savedMethod);
    setMeetingPlace(savedPlace);
    setMeetingTime(savedTime);
    setShippingAddress(savedAddress);
    setAgreeToWaiver(Boolean(currentPartyWaiverAt));
    setError('');
  };

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
    if (waiting || submitting || requirements.length) return;
    const { scheduleRevision } = request;
    const checkNow = new Date();
    const validationError = validateStoredMeetingTime(request.meetingTime, checkNow);
    if (validationError) {
      setNow(checkNow);
      setError(validationError);
      return;
    }
    setSubmitting(true);
    setError('');
    try {
      if (scheduleRevision == null) {
        onScheduleChanged(await requestApi.get(request.id, currentUserId));
        return;
      }
      onUpdated(
        await requestApi.confirmLogistics(request.id, currentUserId, {
          scheduleRevision,
          agreeToWaiver,
          shippingAddress: needsOwnAddress ? shippingAddress.trim() : undefined,
        }),
      );
    } catch (caught) {
      if ((caught as ApiError).code === 'REQUEST_SCHEDULE_CHANGED') {
        try {
          onScheduleChanged(await requestApi.get(request.id, currentUserId));
        } catch {
          setError('Lịch giao nhận đã thay đổi. Hãy tải lại trang để xem lịch mới.');
        }
        return;
      }
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
      {hasSavedSchedule ? (
        <ul aria-label="Trạng thái đồng ý lịch đang lưu" className="flex flex-wrap gap-2">
          <li className={`rounded-full px-2.5 py-1 text-xs font-semibold ${currentPartyConfirmed ? 'bg-success-soft text-success' : 'bg-surface-low text-text-secondary'}`}>
            Bạn {currentPartyConfirmed ? 'đã đồng ý' : 'chưa đồng ý'}
          </li>
          <li className={`rounded-full px-2.5 py-1 text-xs font-semibold ${otherPartyConfirmed ? 'bg-success-soft text-success' : 'bg-surface-low text-text-secondary'}`}>
            {partnerName} {otherPartyConfirmed ? 'đã đồng ý' : 'chưa đồng ý'}
          </li>
        </ul>
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
          </button>
        ))}
      </fieldset>
      <p className="text-xs leading-5 text-text-secondary">
        {isShipping
          ? 'Nên quay video khi mở kiện. Giữ lại video và mã vận đơn để đối chiếu nếu có sự cố.'
          : 'Nên quay video kiểm tra món đồ tại điểm hẹn. Giữ lại video để đối chiếu nếu có sự cố.'}
      </p>
      <div className="space-y-3 rounded-lg border border-border bg-surface-low p-3 sm:p-4">
        <h3 className="text-sm font-semibold text-text-primary">
          {isShipping ? 'Thông tin giao hàng' : 'Thông tin gặp mặt'}
        </h3>
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
                    if (savedAddress.trim() || currentPartyConfirmed) setAgreeToWaiver(false);
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
                error={meetingPlace ? (placeError ?? undefined) : undefined}
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
            label={isShipping ? 'Ngày gửi hàng dự kiến' : 'Thời gian gặp'}
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
        {isShipping && !needsOwnAddress ? (
          <p className="text-xs leading-5 text-text-secondary">Bạn chỉ gửi hàng. {partnerName} sẽ nhập địa chỉ nhận.</p>
        ) : null}
        {isShipping && partnerNeedsAddress(request, viewerIsDonor) ? (
          <p className="text-xs leading-5 text-text-secondary">
            {partnerName} {partnerAddressProvided(request, viewerIsDonor) ? 'đã nhập' : 'chưa nhập'} địa chỉ nhận hàng.
            Hai bên xem được địa chỉ của nhau sau khi chốt lịch.
          </p>
        ) : null}
        {isShipping ? (
          <div className="space-y-1 border-t border-border pt-3 text-xs leading-5 text-text-secondary">
            <p><strong>Hạn gửi</strong> đến {sendDays} ngày sau ngày gửi dự kiến.</p>
            <p>
              <strong>Hạn xác nhận đã nhận</strong> là {receiveDays} ngày kể từ lúc bên gửi đánh dấu đã gửi.
              Nếu bên nhận không phản hồi thì hệ thống tự ghi nhận đã nhận cho chiều đó.
            </p>
          </div>
        ) : null}
      </div>
      <label className="flex items-start gap-2 py-1 text-sm leading-5 text-text-secondary">
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
          disabled={submitting || waiting || requirements.length > 0}
          onClick={() => void (proposes ? saveSchedule() : confirmSchedule())}
        >
          {submitting ? 'Đang xử lý...' : primaryLabel}
        </Button>
        {hasSavedSchedule && (sharedChanges || addressChanged) ? (
          <Button variant="outline" disabled={submitting} onClick={discardChanges}>Bỏ thay đổi</Button>
        ) : null}
      </div>
      {waiting ? (
        <p className="text-xs leading-5 text-text-secondary">
          {timeError || 'Bạn vẫn có thể sửa thông tin và gửi lại trước khi hai bên chốt lịch.'}
        </p>
      ) : requirements.length ? (
        <div className="space-y-1 text-xs leading-5 text-text-secondary">
          <p className="font-semibold">Cần hoàn tất</p>
          <ul className="list-disc pl-4">{requirements.map((reason) => <li key={reason}>{reason}</li>)}</ul>
        </div>
      ) : (
        <p className="text-xs leading-5 text-text-secondary">
          {proposes
            ? `${partnerName} sẽ nhận được đề xuất${hasSavedSchedule ? ' mới và cần đồng ý lại' : ''}.`
            : otherPartyConfirmed ? 'Khi bạn đồng ý, lịch sẽ được chốt.' : `Sau khi bạn đồng ý, lịch vẫn chờ ${partnerName} đồng ý.`}
        </p>
      )}
    </div>
  );
}
