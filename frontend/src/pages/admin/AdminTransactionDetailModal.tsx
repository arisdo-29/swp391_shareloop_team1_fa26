import { useEffect, useId, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { DialogFrame } from '../../components/Dialog';
import { PrivateImage } from '../../components/PrivateImage';
import { Alert, Button, Icon, ItemImage, SkeletonRows, type IconName } from '../../components/ui';
import {
  adminRequestApi,
  type AdminRequestDetail,
  type AdminRequestItemInfo,
  type AdminRequestParty,
  type AdminRequestPhoto,
  type AdminShippingLeg,
  type AdminTimelineEvent,
} from '../../services/adminRequestApi';
import type { ItemRequestStatus } from '../../types/domain';
import { requestStatusLabel } from '../../utils/request';
import { adminErrorMessage } from './adminErrors';
import { deliveryMethodLabels, formatDateTime, itemStatusLabels } from './adminItemDisplay';

// Trường API không có dữ liệu thì hiện đúng câu này, không tự điền giá trị.
const MISSING = 'Chưa có thông tin';

type Tone = 'success' | 'info' | 'warning' | 'error' | 'muted';

const toneClass: Record<Tone, string> = {
  success: 'bg-success-soft text-success',
  info: 'bg-info-soft text-info',
  warning: 'bg-warning-soft text-warning',
  error: 'bg-error-soft text-error',
  muted: 'bg-surface-low text-text-muted',
};

// Cùng quy ước màu với cột Trạng thái của danh sách giao dịch.
function statusTone(status: ItemRequestStatus): Tone {
  if (status === 'COMPLETED') return 'success';
  if (status === 'DISPUTED' || status === 'CANCELLED' || status === 'REJECTED') return 'error';
  if (status === 'PENDING') return 'muted';
  return 'info';
}

// Màu mốc tiến trình theo nhãn backend trả về: huỷ đỏ, đề nghị huỷ hoặc dời lịch vàng, hoàn tất xanh lá.
function timelineTone(label: string): Tone {
  const text = label.toLowerCase();
  if (text.includes('đề nghị')) return 'warning';
  if (text.includes('huỷ') || text.includes('hủy') || text.includes('chọn người nhận khác'))
    return 'error';
  if (text.includes('hoàn tất')) return 'success';
  return 'info';
}

function legTone(step: string): Tone {
  if (step === 'Đã nhận') return 'success';
  if (step === 'Đã gửi') return 'info';
  return 'warning';
}

const photoStageLabels: Record<string, string> = {
  HANDOVER: 'Lúc giao nhận',
  DISPUTE: 'Khi có sự cố',
};

// Nhãn mốc huỷ do backend dựng (AdminRequestDetailService.cancelEvent).
const CANCEL_EVENT = /^(Do .+ huỷ|Giao dịch bị huỷ)$/;

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
    <section className="admin-tx-modal-section" aria-labelledby={id}>
      <h3 id={id} className="admin-tx-modal-section-title">
        <span className="admin-tx-modal-section-icon">
          <Icon name={icon} weight="bold" />
        </span>
        {title}
      </h3>
      {children}
    </section>
  );
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  const missing = children === MISSING;
  return (
    <div className="admin-tx-modal-row">
      <dt>{label}</dt>
      <dd className={missing ? 'admin-tx-modal-missing' : undefined}>{children}</dd>
    </div>
  );
}

/** Trạng thái xác nhận của một bên, dựa trên mốc có thật trong dòng thời gian. */
function Confirmation({ event, doneLabel }: { event: AdminTimelineEvent | null; doneLabel: string }) {
  if (!event) return <Badge tone="warning">Chưa xác nhận</Badge>;
  return (
    <span className="inline-flex flex-wrap items-center justify-end gap-2">
      <Badge tone="success">{doneLabel}</Badge>
      <span className="text-xs text-text-muted">{formatDateTime(event.at)}</span>
    </span>
  );
}

function PartyBox({ role, party }: { role: string; party: AdminRequestParty | null }) {
  return (
    <div className="admin-tx-modal-box">
      <p className="admin-tx-modal-box-label">{role}</p>
      {party ? (
        <>
          <p className="font-semibold text-text-primary">{party.name || MISSING}</p>
          <p className="text-xs text-text-muted">{party.email || MISSING}</p>
          {party.locked ? (
            <p className="mt-1 text-xs font-semibold text-error">Tài khoản đang bị khoá</p>
          ) : null}
        </>
      ) : (
        <p className="admin-tx-modal-missing">{MISSING}</p>
      )}
    </div>
  );
}

function ItemBox({ role, item }: { role: string; item: AdminRequestItemInfo | null }) {
  return (
    <div className="admin-tx-modal-box admin-tx-modal-item">
      {item?.title ? (
        <ItemImage
          src={item.imageUrl ?? undefined}
          alt={item.title}
          className="size-12 shrink-0 rounded-md object-cover"
        />
      ) : null}
      <div className="min-w-0">
        <p className="admin-tx-modal-box-label">{role}</p>
        {item?.title ? (
          <>
            <Link
              to={`/admin/items/${item.itemId}`}
              className="block truncate font-semibold text-primary hover:underline"
              title={item.title}
            >
              {item.title}
            </Link>
            {item.status ? (
              <p className="text-xs text-text-muted">
                {itemStatusLabels[item.status as keyof typeof itemStatusLabels] ?? item.status}
              </p>
            ) : null}
          </>
        ) : (
          <p className="admin-tx-modal-missing">{item ? 'Bài đã xoá' : MISSING}</p>
        )}
      </div>
    </div>
  );
}

function Photos({ photos, empty }: { photos: AdminRequestPhoto[]; empty: string }) {
  if (photos.length === 0) return <p className="admin-tx-modal-missing text-sm">{empty}</p>;
  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
      {photos.map((photo) => (
        <figure key={photo.mediaId} className="text-xs text-text-muted">
          <PrivateImage
            path={photo.url}
            alt="Ảnh minh chứng giao nhận"
            className="h-24 w-full rounded-md object-cover"
          />
          <figcaption className="mt-1">
            {photoStageLabels[photo.stage] ?? photo.stage}
            {photo.uploaderName ? `, ${photo.uploaderName}` : ''} · {formatDateTime(photo.createdAt)}
          </figcaption>
        </figure>
      ))}
    </div>
  );
}

// Mốc gửi hàng trong dòng thời gian mang mã vận đơn của bên vận chuyển ngoài, ShareLoop không quản lý nên không hiện.
const SHIPPING_SEND_EVENT = /gửi hàng \(chiều \d\)$/;

/** Một chiều gửi hàng: chỉ các trường ShareLoop lưu, không gồm thông tin của đơn vị vận chuyển. */
function ShippingLegCard({ leg }: { leg: AdminShippingLeg }) {
  return (
    <div className="admin-tx-modal-box">
      <div className="mb-1 flex flex-wrap items-center justify-between gap-2">
        <p className="font-semibold text-text-primary">
          Chiều {leg.leg}: {leg.senderRole} gửi {leg.receiverRole}
        </p>
        <Badge tone={legTone(leg.step)}>{leg.step}</Badge>
      </div>
      <dl>
        <Row label="Địa chỉ nhận">{text(leg.address)}</Row>
        <Row label={`${leg.senderRole} xác nhận đã gửi`}>{at(leg.shippedAt)}</Row>
        <Row label={`${leg.receiverRole} xác nhận đã nhận`}>{at(leg.receivedAt)}</Row>
      </dl>
    </div>
  );
}

function DetailBody({ detail }: { detail: AdminRequestDetail }) {
  const { timeline, deadlines } = detail;
  const isShipping = detail.deliveryMethod === 'SHIPPING';
  const donorSchedule = findEvent(timeline, 'Chủ bài đồng ý lịch hẹn');
  const receiverSchedule = findEvent(timeline, 'Người nhận đồng ý lịch hẹn');
  // Trước khi chốt lịch, mốc tự huỷ chính là hạn để hai bên xác nhận lịch.
  const scheduleDeadline =
    detail.status === 'RESERVED' || detail.status === 'AWAITING_LOGISTICS'
      ? deadlines?.autoCancelAt
      : null;
  // Đã chốt lịch: hạn bàn giao là mốc tự huỷ nếu không ai làm gì; đang chờ bàn giao: mốc tự hoàn tất.
  const handoverDeadline =
    detail.status === 'LOGISTICS_CONFIRMED'
      ? deadlines?.autoCancelAt
      : detail.status === 'AWAITING_HANDOVER'
        ? deadlines?.autoConfirmAt
        : null;
  const cancelEvent =
    detail.status === 'CANCELLED'
      ? ([...timeline].reverse().find((event) => CANCEL_EVENT.test(event.label)) ?? null)
      : null;
  const latest = timeline.length ? timeline[timeline.length - 1] : null;
  const legs = detail.shippingLegs ?? [];

  const scheduleConfirmations = (
    <>
      <Row label="Chủ bài xác nhận lịch">
        <Confirmation event={donorSchedule} doneLabel="Đã đồng ý" />
      </Row>
      <Row label="Người nhận xác nhận lịch">
        <Confirmation event={receiverSchedule} doneLabel="Đã đồng ý" />
      </Row>
      <Row label="Hạn xác nhận lịch">{at(scheduleDeadline)}</Row>
    </>
  );

  return (
    <div className="admin-tx-modal-body">
      <Section icon="transactions" title="Thông tin giao dịch">
        <dl>
          <Row label="Mã giao dịch">#{detail.id}</Row>
          <Row label="Loại giao dịch">
            <span
              className={`admin-transaction-type ${detail.type === 'SWAP' ? 'admin-transaction-type-swap' : 'admin-transaction-type-give'}`}
            >
              {detail.type === 'SWAP' ? 'Trao đổi' : 'Cho tặng'}
            </span>
          </Row>
          <Row label="Hình thức giao nhận">
            {detail.deliveryMethod ? deliveryMethodLabels[detail.deliveryMethod] : MISSING}
          </Row>
          <Row label="Tạo lúc">{at(detail.createdAt)}</Row>
        </dl>
        <div className="admin-tx-modal-grid mt-3">
          <ItemBox role="Món đồ chủ bài" item={detail.item} />
          {detail.type === 'SWAP' ? (
            <ItemBox role="Món đem đổi" item={detail.offeredItem} />
          ) : null}
          <PartyBox role="Chủ bài" party={detail.donor} />
          <PartyBox role="Người nhận / trao đổi" party={detail.receiver} />
        </div>
      </Section>

      {isShipping ? (
        <Section icon="topup" title="Thông tin gửi hàng">
          <dl>
            <Row label="Hạn gửi hàng">{at(deadlines?.sendDeadlineAt)}</Row>
          </dl>
          <div className="mt-3 space-y-3">
            {legs.length ? (
              legs.map((leg) => (
                <ShippingLegCard key={leg.leg} leg={leg} />
              ))
            ) : (
              <p className="admin-tx-modal-missing text-sm">{MISSING}</p>
            )}
          </div>
          {detail.photos.length ? (
            <>
              <p className="admin-tx-modal-subtitle">Minh chứng</p>
              <Photos photos={detail.photos} empty={MISSING} />
            </>
          ) : null}
        </Section>
      ) : (
        <Section icon="calendarCheck" title="Lịch giao nhận">
          {!detail.deliveryMethod ? (
            <p className="mb-2 text-sm text-text-muted">Hai bên chưa chọn hình thức giao nhận.</p>
          ) : null}
          <dl>
            <Row label="Thời gian đã chốt">{at(detail.meetingTime)}</Row>
            <Row label="Địa điểm">{text(detail.meetingPlace)}</Row>
            {scheduleConfirmations}
            <Row label="Hạn bàn giao">{at(handoverDeadline)}</Row>
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
            {detail.reschedule?.pending ? (
              <Row label="Đề nghị dời lịch đang chờ">
                {`${detail.reschedule.proposedByRole ?? MISSING}: ${at(detail.reschedule.proposedMeetingTime)}, ${text(detail.reschedule.proposedMeetingPlace)}`}
              </Row>
            ) : null}
          </dl>
          {detail.deliveryMethod === 'IN_PERSON' ? (
            <>
              <p className="admin-tx-modal-subtitle">Ảnh bàn giao</p>
              <Photos photos={detail.photos} empty={MISSING} />
            </>
          ) : null}
        </Section>
      )}

      <Section icon="report" title="Tiến trình giao dịch">
        <dl>
          <Row label="Trạng thái hiện tại">
            <Badge tone={statusTone(detail.status)}>{requestStatusLabel[detail.status]}</Badge>
          </Row>
          <Row label="Cập nhật gần nhất">{at(latest?.at)}</Row>
          {detail.status === 'CANCELLED' ? (
            <Row label="Lý do huỷ">
              {cancelEvent?.note ? `${cancelEvent.label}: ${cancelEvent.note}` : MISSING}
            </Row>
          ) : null}
          {detail.cancelRequest ? (
            <Row label="Đề nghị huỷ">
              {`${detail.cancelRequest.requestedByRole}: ${
                [detail.cancelRequest.label, detail.cancelRequest.reason].filter(Boolean).join('. ') ||
                MISSING
              }`}
            </Row>
          ) : null}
        </dl>
        {timeline.length ? (
          <ol className="admin-tx-modal-timeline" aria-label="Các mốc giao dịch">
            {timeline.map((event, index) => {
              const tone = timelineTone(event.label);
              const isLatest = index === timeline.length - 1;
              return (
                <li
                  key={`${event.label}-${event.at}`}
                  className={`admin-tx-modal-step admin-tx-modal-step--${tone}`}
                  aria-current={isLatest ? 'step' : undefined}
                >
                  <span className="admin-tx-modal-dot" aria-hidden="true" />
                  <div className="min-w-0">
                    <p className="flex flex-wrap items-center gap-2 font-semibold text-text-primary">
                      {event.label}
                      {isLatest ? <Badge tone={tone}>Mới nhất</Badge> : null}
                    </p>
                    <p className="text-xs text-text-muted">{formatDateTime(event.at)}</p>
                    {event.note && !SHIPPING_SEND_EVENT.test(event.label) ? (
                      <p className="mt-1 text-xs text-text-secondary">{event.note}</p>
                    ) : null}
                  </div>
                </li>
              );
            })}
          </ol>
        ) : (
          <p className="admin-tx-modal-missing mt-2 text-sm">{MISSING}</p>
        )}
      </Section>
    </div>
  );
}

/** Hộp thoại chi tiết một giao dịch, tải dữ liệu thật từ API chi tiết giao dịch của Admin. */
export function TransactionDetailModal({
  requestId,
  onClose,
}: {
  requestId: number;
  onClose: () => void;
}) {
  const titleId = useId();
  const [detail, setDetail] = useState<AdminRequestDetail | null>(null);
  const [error, setError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let active = true;
    setDetail(null);
    setError('');
    adminRequestApi
      .detail(requestId)
      .then((response) => {
        if (active) setDetail(response);
      })
      .catch((caught) => {
        if (active) setError(adminErrorMessage(caught, 'Không tải được chi tiết giao dịch.'));
      });
    return () => {
      active = false;
    };
  }, [requestId, reloadKey]);

  return (
    <DialogFrame labelledBy={titleId} size="xl" onClose={onClose} className="admin-tx-modal">
      <div className="admin-tx-modal-header">
        <div className="min-w-0">
          <h2 id={titleId} className="text-lg font-bold text-text-primary">
            Chi tiết giao dịch #{requestId}
          </h2>
          {detail ? (
            <p className="truncate text-sm text-text-muted">{detail.item.title || 'Bài đã xoá'}</p>
          ) : null}
        </div>
        <button type="button" aria-label="Đóng" className="admin-tx-modal-close" onClick={onClose}>
          <Icon name="close" weight="bold" />
        </button>
      </div>

      {error ? (
        <div className="space-y-3">
          <Alert tone="error">{error}</Alert>
          <Button variant="outline" onClick={() => setReloadKey((key) => key + 1)}>
            Thử lại
          </Button>
        </div>
      ) : detail ? (
        <DetailBody detail={detail} />
      ) : (
        <SkeletonRows label="Đang tải chi tiết giao dịch" />
      )}

      <div className="admin-tx-modal-footer">
        <Link
          to={`/admin/transactions/${requestId}`}
          className="text-sm font-semibold text-primary hover:underline"
        >
          Mở trang chi tiết đầy đủ
        </Link>
        <Button variant="outline" onClick={onClose}>
          Đóng
        </Button>
      </div>
    </DialogFrame>
  );
}
