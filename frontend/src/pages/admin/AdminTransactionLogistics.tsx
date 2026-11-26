import { useId, type ReactNode } from 'react';
import { Icon, type IconName } from '../../components/ui';
import type {
  AdminRequestDetail,
  AdminShippingLeg,
  AdminTimelineEvent,
} from '../../services/adminRequestApi';
import { formatDateTime } from '../../utils/request';
import { deliveryMethodLabels } from './adminItemDisplay';

// Trường API không có dữ liệu thì hiện đúng câu này, không tự điền giá trị.
const MISSING = 'Chưa có thông tin';

type Tone = 'success' | 'info' | 'warning';

const toneClass: Record<Tone, string> = {
  success: 'bg-success-soft text-success',
  info: 'bg-info-soft text-info',
  warning: 'bg-warning-soft text-warning',
};

function legTone(step: string): Tone {
  if (step === 'Đã nhận') return 'success';
  if (step === 'Đã gửi') return 'info';
  return 'warning';
}

function at(value: string | null | undefined) {
  return value ? formatDateTime(value) : MISSING;
}

function text(value: string | null | undefined) {
  return value?.trim() ? value : MISSING;
}

function findEvent(timeline: AdminTimelineEvent[], label: string) {
  return timeline.find((event) => event.label === label) ?? null;
}

function Badge({ tone, children }: { tone: Tone; children: ReactNode }) {
  return (
    <span
      className={`inline-flex items-center whitespace-nowrap rounded-sm px-2 py-0.5 text-2xs font-semibold ${toneClass[tone]}`}
    >
      {children}
    </span>
  );
}

function Section({
  icon,
  title,
  children,
}: {
  icon: IconName;
  title: string;
  children: ReactNode;
}) {
  const id = useId();
  return (
    <section className="admin-transaction-detail-section" aria-labelledby={id}>
      <h2 id={id} className="admin-transaction-detail-section-title">
        <span className="admin-transaction-detail-section-icon">
          <Icon name={icon} weight="bold" />
        </span>
        {title}
      </h2>
      {children}
    </section>
  );
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  const missing = children === MISSING;
  return (
    <div className="admin-transaction-detail-row">
      <dt>{label}</dt>
      <dd className={missing ? 'admin-transaction-detail-missing' : undefined}>{children}</dd>
    </div>
  );
}

/** Trạng thái xác nhận của một bên, dựa trên mốc có thật trong dòng thời gian. */
function Confirmation({
  event,
  doneLabel,
  showPending = true,
}: {
  event: AdminTimelineEvent | null;
  doneLabel: string;
  showPending?: boolean;
}) {
  if (!event && !showPending)
    return <span className="admin-transaction-detail-missing">{MISSING}</span>;
  if (!event) return <Badge tone="warning">Chưa xác nhận</Badge>;
  return (
    <span className="inline-flex flex-wrap items-center justify-end gap-2">
      <Badge tone="success">{doneLabel}</Badge>
      <span className="text-xs text-text-muted">{formatDateTime(event.at)}</span>
    </span>
  );
}

function ShippingLegCard({
  leg,
  autoReceiveAt,
}: {
  leg: AdminShippingLeg;
  autoReceiveAt?: string | null;
}) {
  return (
    <div className="admin-transaction-detail-box" data-testid={`admin-leg-${leg.leg}`}>
      <div className="mb-1 flex flex-wrap items-center justify-between gap-2">
        <p className="font-semibold text-text-primary">
          Chiều {leg.leg}: {leg.senderRole} gửi {leg.receiverRole}
        </p>
        <Badge tone={legTone(leg.step)}>{leg.step}</Badge>
      </div>
      <dl>
        <Row label="Món gửi">{text(leg.itemTitle)}</Row>
        <Row label="Địa chỉ nhận">{text(leg.address)}</Row>
        <Row label={`${leg.senderRole} xác nhận đã gửi`}>{at(leg.shippedAt)}</Row>
        <Row label="Mã vận đơn">{text(leg.trackingCode)}</Row>
        <Row label="Thời điểm ghi nhận đã nhận">{at(leg.receivedAt)}</Row>
        {autoReceiveAt ? <Row label="Tự ghi nhận đã nhận lúc">{at(autoReceiveAt)}</Row> : null}
      </dl>
    </div>
  );
}

export function AdminTransactionLogistics({ detail }: { detail: AdminRequestDetail }) {
  const { timeline, deadlines } = detail;
  const isShipping = detail.deliveryMethod === 'SHIPPING';
  const donorSchedule = findEvent(timeline, 'Chủ bài đồng ý lịch hẹn');
  const receiverSchedule = findEvent(timeline, 'Người nhận đồng ý lịch hẹn');
  const isScheduling = detail.status === 'RESERVED' || detail.status === 'AWAITING_LOGISTICS';
  return (
    <>
      <Section icon="calendarCheck" title={isShipping ? 'Lịch gửi hàng' : 'Lịch giao nhận'}>
        <dl>
          <Row label="Hình thức giao nhận">
            {detail.deliveryMethod ? deliveryMethodLabels[detail.deliveryMethod] : 'Chưa chọn'}
          </Row>
          <Row
            label={
              isShipping ? 'Ngày gửi hàng dự kiến' : isScheduling ? 'Giờ hẹn đề xuất' : 'Giờ hẹn'
            }
          >
            {at(detail.meetingTime)}
          </Row>
          {!isShipping ? <Row label="Địa điểm hẹn">{text(detail.meetingPlace)}</Row> : null}
          <Row label="Chủ bài xác nhận lịch">
            <Confirmation event={donorSchedule} doneLabel="Đã đồng ý" showPending={isScheduling} />
          </Row>
          <Row label="Người nhận xác nhận lịch">
            <Confirmation
              event={receiverSchedule}
              doneLabel="Đã đồng ý"
              showPending={isScheduling}
            />
          </Row>
          {isScheduling ? <Row label="Hạn xác nhận lịch">{at(deadlines?.autoCancelAt)}</Row> : null}
          {!isScheduling && deadlines?.autoCancelAt ? (
            <Row label="Tự huỷ nếu chưa giao nhận lúc">{at(deadlines.autoCancelAt)}</Row>
          ) : null}
          {!isShipping && deadlines?.autoConfirmAt ? (
            <Row label="Tự hoàn tất lúc">{at(deadlines.autoConfirmAt)}</Row>
          ) : null}
          {detail.deliveryMethod === 'IN_PERSON' ? (
            <>
              <Row label="Chủ bài xác nhận bàn giao">
                <Confirmation
                  event={findEvent(timeline, 'Chủ bài xác nhận đã giao nhận')}
                  doneLabel="Đã xác nhận"
                />
              </Row>
              <Row label="Người nhận xác nhận bàn giao">
                <Confirmation
                  event={findEvent(timeline, 'Người nhận xác nhận đã giao nhận')}
                  doneLabel="Đã xác nhận"
                />
              </Row>
            </>
          ) : null}
          {detail.autoConfirmed ? (
            <Row label="Cách hoàn tất">Hệ thống đã tự ghi nhận hoàn tất</Row>
          ) : null}
        </dl>
      </Section>
      {isShipping ? (
        <Section icon="topup" title="Thông tin gửi hàng">
          <dl>
            <Row label="Hạn gửi hàng">{at(deadlines?.sendDeadlineAt)}</Row>
          </dl>
          <div className="mt-3 space-y-3">
            {detail.shippingLegs?.length ? (
              detail.shippingLegs.map((leg) => (
                <ShippingLegCard
                  key={leg.leg}
                  leg={leg}
                  autoReceiveAt={
                    leg.leg === 1 ? deadlines?.leg1AutoReceiveAt : deadlines?.leg2AutoReceiveAt
                  }
                />
              ))
            ) : (
              <p className="admin-transaction-detail-missing text-sm">{MISSING}</p>
            )}
          </div>
        </Section>
      ) : null}
    </>
  );
}
