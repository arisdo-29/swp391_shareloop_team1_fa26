import { useState } from 'react';
import { Alert, Button } from '../ui';
import { DialogFrame } from '../Dialog';
import { ReportEvidenceField } from '../ReportEvidenceField';
import { mediaApi } from '../../services/mediaApi';
import { requestApi, type RequestResponse } from '../../services/requestApi';
import type { ApiError } from '../../services/http';
import { canConfirmHandover, canRefuseHandover, formatDateTime, formatDeadline } from '../../utils/request';
import { usePublicConfig } from '../../utils/usePublicConfig';
import { cancelRequestConsequence, refuseHandoverConsequence } from '../../utils/handoverOutcome';
import {
  anyLegShipped,
  shippingFailedConsequence,
  shippingLegs,
  shippingStepLabels,
  shippingStepOrder,
  viewerSendsLeg,
  type ShippingLeg,
} from '../../utils/shipping';
import { CancelRequestPanel } from './CancelRequestPanel';

interface ShipmentCardProps {
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

/**
 * Khối "Giao nhận" của Giao hàng hai chiều (GN-12): mỗi chiều có ba bước Chờ gửi, Đã gửi, Đã nhận. Bên gửi bấm Tôi đã
 * gửi hàng, bên nhận chỉ bấm Tôi đã nhận được hàng sau khi chiều đó Đã gửi. Mọi hạn do backend tính.
 */
export function ShipmentCard(props: ShipmentCardProps) {
  const { request, currentUserId, viewerIsDonor, partnerName, onUpdated, onOpenRejectCancel } = props;
  const config = usePublicConfig();
  const [shipDialogLeg, setShipDialogLeg] = useState<ShippingLeg | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  if (!canConfirmHandover(request.status) || !request.contactRevealedAt) return null;

  const legs = shippingLegs(request);
  const hasPendingCancel = Boolean(request.cancelRequestedAt);
  const noLegShipped = !anyLegShipped(request);

  const receive = async () => {
    if (submitting) return;
    setSubmitting(true);
    setError('');
    try {
      onUpdated(await requestApi.confirmHandover(request.id, currentUserId, {}));
    } catch (caught) {
      setError((caught as ApiError).message || 'Không xác nhận được việc nhận hàng.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <section className="card space-y-4 p-5 sm:p-6" aria-labelledby="shipment-title">
      <h2 id="shipment-title" className="section-title">
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
      ) : null}
      {legs.map((leg) => (
        <LegBlock
          key={leg.leg}
          leg={leg}
          request={request}
          viewerIsDonor={viewerIsDonor}
          partnerName={partnerName}
          submitting={submitting}
          hasPendingCancel={hasPendingCancel}
          responseHours={config.response_window_hours}
          cancelResponseHours={config.cancel_request_response_hours}
          onShip={() => setShipDialogLeg(leg)}
          onReceive={() => void receive()}
          onOpenFailed={props.onOpenFailed}
          onOpenRefuse={props.onOpenRefuse}
          legs={legs}
        />
      ))}
      {!hasPendingCancel && noLegShipped ? (
        <div className="space-y-1">
          <Button variant="outline" disabled={submitting} onClick={props.onOpenCancelRequest}>
            Đề nghị huỷ giao dịch
          </Button>
          <p className="text-xs leading-5 text-text-secondary">
            {cancelRequestConsequence(partnerName, config.cancel_request_response_hours)}
          </p>
        </div>
      ) : null}
      <p className="text-xs font-semibold text-warning">
        Sau khi hoàn tất, bạn chỉ có {config.complaint_window_hours} giờ để khiếu nại.
      </p>
      {error ? (
        <div role="alert">
          <Alert tone="error">{error}</Alert>
        </div>
      ) : null}
      {shipDialogLeg ? (
        <ShipDialog
          request={request}
          currentUserId={currentUserId}
          leg={shipDialogLeg}
          onClose={() => setShipDialogLeg(null)}
          onDone={(updated) => {
            setShipDialogLeg(null);
            onUpdated(updated);
          }}
        />
      ) : null}
    </section>
  );
}

function LegBlock({
  leg,
  legs,
  request,
  viewerIsDonor,
  partnerName,
  submitting,
  hasPendingCancel,
  responseHours,
  cancelResponseHours,
  onShip,
  onReceive,
  onOpenFailed,
  onOpenRefuse,
}: {
  leg: ShippingLeg;
  legs: ShippingLeg[];
  request: RequestResponse;
  viewerIsDonor: boolean;
  partnerName: string;
  submitting: boolean;
  hasPendingCancel: boolean;
  responseHours: number;
  cancelResponseHours: number;
  onShip: () => void;
  onReceive: () => void;
  onOpenFailed: () => void;
  onOpenRefuse: () => void;
}) {
  const sends = viewerSendsLeg(leg, viewerIsDonor);
  const title = sends ? 'Món bạn gửi' : 'Món bạn nhận';
  const otherLeg = legs.find((item) => item.leg !== leg.leg);
  const otherReceived = otherLeg?.step === 'RECEIVED';
  const open = canConfirmHandover(request.status);
  const receiveDeadline = leg.leg === 1 ? request.leg1AutoReceiveAt : request.leg2AutoReceiveAt;
  // Chỉ chọn nút để hiện, backend vẫn kiểm hạn gửi: chiều chưa gửi chỉ báo được khi quá hạn gửi hoặc chiều kia đã nhận.
  const pastSendDeadline = request.sendDeadlineAt ? new Date(request.sendDeadlineAt).getTime() <= Date.now() : false;
  const canReportNotShipped = otherReceived || pastSendDeadline;

  return (
    <div className="space-y-3 rounded-md p-3.5 ring-1 ring-border" data-testid={`shipment-leg-${leg.leg}`}>
      <div>
        <h3 className="text-sm font-bold">{title}</h3>
        <p className="text-sm text-text-secondary">{leg.itemTitle}</p>
      </div>
      <StepIndicator step={leg.step} />
      <dl className="grid gap-2 text-sm sm:grid-cols-2">
        {leg.address ? (
          <div>
            <dt className="label">Địa chỉ giao tới</dt>
            <dd className="break-words">{leg.address}</dd>
          </div>
        ) : null}
        {leg.shippedAt ? (
          <div>
            <dt className="label">{sends ? 'Bạn báo đã gửi lúc' : 'Người gửi báo đã gửi lúc'}</dt>
            <dd>{formatDateTime(leg.shippedAt)}</dd>
          </div>
        ) : null}
        {leg.step === 'WAITING_SHIP' && request.sendDeadlineAt ? (
          <div>
            <dt className="label">Hạn gửi</dt>
            <dd>{formatDeadline(request.sendDeadlineAt)}</dd>
          </div>
        ) : null}
        {leg.step === 'SHIPPED' && receiveDeadline ? (
          <div>
            <dt className="label">Hạn xác nhận đã nhận</dt>
            <dd>{formatDeadline(receiveDeadline)}</dd>
            {!sends && !hasPendingCancel ? (
              <dd className="text-xs leading-5 text-text-secondary">
                Nếu bạn không phản hồi trước hạn, hệ thống có thể tự ghi nhận đã nhận hàng. Nếu hàng chưa tới, hãy báo
                giao nhận không thành công.
              </dd>
            ) : null}
          </div>
        ) : null}
        {leg.receivedAt ? (
          <div>
            <dt className="label">Đã nhận lúc</dt>
            <dd>{formatDateTime(leg.receivedAt)}</dd>
          </div>
        ) : null}
      </dl>

      {sends && leg.step === 'WAITING_SHIP' && open ? (
        <div className="space-y-1">
          <Button disabled={submitting} onClick={onShip}>
            Tôi đã gửi hàng
          </Button>
          <p className="text-xs leading-5 text-text-secondary">
            Bạn có thể kèm ảnh biên nhận. {partnerName} sẽ được thông báo khi bạn xác nhận.
            {hasPendingCancel ? ' Nếu bên kia đã báo chưa nhận được hàng thì đề nghị huỷ của họ tự rút.' : ''}
          </p>
        </div>
      ) : null}
      {sends && leg.step === 'SHIPPED' ? (
        <p className="text-xs leading-5 text-text-secondary">
          Đang chờ {partnerName} xác nhận đã nhận hàng.
        </p>
      ) : null}

      {!sends && open && leg.step === 'RECEIVED' ? (
        <Alert tone="success">
          {otherLeg && otherLeg.step !== 'RECEIVED'
            ? `Bạn đã nhận, đang chờ ${partnerName} nhận hàng của họ.`
            : 'Bạn đã nhận được hàng.'}
        </Alert>
      ) : null}
      {!sends && open && leg.step === 'SHIPPED' && !hasPendingCancel ? (
        <ul className="space-y-3">
          <li className="space-y-1">
            <Button disabled={submitting} onClick={onReceive}>
              Tôi đã nhận được hàng
            </Button>
            <p className="text-xs leading-5 text-text-secondary">
              Chỉ bấm khi bạn đã cầm hàng. Giao dịch hoàn tất khi mọi chiều đều Đã nhận.
            </p>
          </li>
          <li className="space-y-1">
            <Button variant="outline" disabled={submitting} onClick={onOpenFailed}>
              Giao nhận không thành công
            </Button>
            <p className="text-xs leading-5 text-text-secondary">
              {shippingFailedConsequence(request, viewerIsDonor, partnerName, responseHours, cancelResponseHours)}
            </p>
          </li>
          {canRefuseHandover(viewerIsDonor, request.offeredItemId) ? (
            <li className="space-y-1">
              <Button variant="warning" disabled={submitting} onClick={onOpenRefuse}>
                Từ chối giao nhận
              </Button>
              <p className="text-xs leading-5 text-text-secondary">
                {refuseHandoverConsequence(request, viewerIsDonor)}
              </p>
            </li>
          ) : null}
        </ul>
      ) : null}
      {!sends && open && leg.step === 'WAITING_SHIP' && !hasPendingCancel ? (
        <div className="space-y-1">
          <p className="text-xs leading-5 text-text-secondary">
            {partnerName} chưa gửi hàng
            {request.sendDeadlineAt ? `, hạn gửi đến ${formatDeadline(request.sendDeadlineAt)}` : ''}.
          </p>
          {canReportNotShipped ? (
            <>
              <Button variant="outline" disabled={submitting} onClick={onOpenFailed}>
                Giao nhận không thành công
              </Button>
              <p className="text-xs leading-5 text-text-secondary">
                {shippingFailedConsequence(request, viewerIsDonor, partnerName, responseHours, cancelResponseHours)}
              </p>
            </>
          ) : (
            <p className="text-xs leading-5 text-text-secondary">
              Chưa quá hạn gửi nên chưa báo được. Muốn dừng giao dịch thì dùng Đề nghị huỷ giao dịch.
            </p>
          )}
        </div>
      ) : null}
    </div>
  );
}

function StepIndicator({ step }: { step: ShippingLeg['step'] }) {
  const currentIndex = shippingStepOrder.indexOf(step);
  return (
    <ol aria-label="Tiến độ chiều giao hàng" className="flex gap-2">
      {shippingStepOrder.map((item, index) => (
        <li
          key={item}
          aria-current={index === currentIndex ? 'step' : undefined}
          className={`flex-1 rounded-full px-2 py-1 text-center text-xs font-semibold ${
            index <= currentIndex ? 'bg-primary-faint text-primary' : 'bg-surface-low text-text-muted'
          }`}
        >
          {shippingStepLabels[item]}
        </li>
      ))}
    </ol>
  );
}

function ShipDialog({
  request,
  currentUserId,
  leg,
  onClose,
  onDone,
}: {
  request: RequestResponse;
  currentUserId: number;
  leg: ShippingLeg;
  onClose: () => void;
  onDone: (request: RequestResponse) => void;
}) {
  const [photos, setPhotos] = useState<File[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const submit = async () => {
    if (submitting) return;
    setSubmitting(true);
    setError('');
    try {
      const uploaded = await Promise.all(photos.map((file) => mediaApi.upload(file)));
      const mediaIds = uploaded.map((entry) => entry.mediaId);
      onDone(
        await requestApi.markShipped(request.id, currentUserId, {
          mediaIds: mediaIds.length ? mediaIds : undefined,
        }),
      );
    } catch (caught) {
      setError((caught as ApiError).message || 'Không gửi được thông tin gửi hàng. Vui lòng thử lại.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <DialogFrame labelledBy="ship-title" busy={submitting} onClose={onClose}>
      <h2 id="ship-title" className="text-xl font-bold">
        Tôi đã gửi hàng
      </h2>
      <p className="mt-1 text-sm text-text-muted">{leg.itemTitle}</p>
      <p className="mt-3 text-sm leading-6 text-text-secondary">
        Bạn có thể đính kèm ảnh biên nhận. Chỉ hai bên và quản trị viên xem được ảnh.
      </p>
      <div className="mt-4">
        <ReportEvidenceField files={photos} onChange={setPhotos} disabled={submitting} />
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
        <Button disabled={submitting} onClick={() => void submit()}>
          {submitting ? 'Đang gửi...' : 'Xác nhận đã gửi'}
        </Button>
      </div>
    </DialogFrame>
  );
}
