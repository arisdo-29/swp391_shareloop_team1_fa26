import { useEffect, useState, type ReactNode } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Alert, Button, ItemImage, PageHeader, SkeletonRows } from '../../components/ui';
import { PrivateImage } from '../../components/PrivateImage';
import {
  adminRequestApi,
  type AdminChatMessage,
  type AdminRequestDetail,
  type AdminRequestItemInfo,
  type AdminRequestParty,
} from '../../services/adminRequestApi';
import { formatTrustStars, getRankLabel } from '../../utils/reputation';
import { requestStatusLabel } from '../../utils/request';
import { adminErrorMessage } from './adminErrors';
import {
  deliveryMethodLabels,
  formatDateTime,
  itemStatusLabels,
  reportReasonLabels,
  reportStatusLabels,
} from './adminItemDisplay';
import { CancelDialog } from './AdminTransactions';

const stageLabels: Record<string, string> = {
  HANDOVER: 'Lúc giao nhận',
  DISPUTE: 'Khi có sự cố',
};

function Card({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="rounded-lg bg-white p-5 ring-1 ring-border">
      <h2 className="text-base font-bold text-text-primary">{title}</h2>
      <div className="mt-3">{children}</div>
    </section>
  );
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex flex-wrap justify-between gap-2 py-2 text-sm">
      <dt className="text-text-muted">{label}</dt>
      <dd className="text-right font-medium text-text-primary">{children}</dd>
    </div>
  );
}

function Party({ title, party }: { title: string; party: AdminRequestParty | null }) {
  if (!party) {
    return (
      <div className="rounded-md bg-surface-low p-3 text-sm">
        <p className="text-xs text-text-muted">{title}</p>
        <p className="font-semibold text-text-primary">Không còn thông tin</p>
      </div>
    );
  }
  return (
    <div className="rounded-md bg-surface-low p-3 text-sm">
      <p className="text-xs text-text-muted">{title}</p>
      <p className="font-semibold text-text-primary">{party.name || 'Người dùng đã xóa'}</p>
      {party.email ? <p className="text-xs text-text-muted">{party.email}</p> : null}
      <p className="mt-1 text-xs text-text-secondary">
        {formatTrustStars(party.trustStars)} sao{party.rank ? `, hạng ${getRankLabel(party.rank)}` : ''}
      </p>
      {party.locked ? <p className="mt-1 text-xs font-semibold text-error">Tài khoản đang bị khoá</p> : null}
    </div>
  );
}

function ItemBox({ title, info }: { title: string; info: AdminRequestItemInfo }) {
  return (
    <div className="flex items-center gap-3 rounded-md bg-surface-low p-3 text-sm">
      <ItemImage
        src={info.imageUrl ?? undefined}
        alt={info.title ?? 'Món đồ'}
        className="h-14 w-16 shrink-0 rounded-md object-cover"
      />
      <div className="min-w-0">
        <p className="text-xs text-text-muted">{title}</p>
        <p className="truncate font-semibold text-text-primary">{info.title || 'Bài đã xoá'}</p>
        {info.status ? (
          <p className="text-xs text-text-secondary">
            {itemStatusLabels[info.status as keyof typeof itemStatusLabels] ?? info.status}
          </p>
        ) : null}
        {info.title ? (
          <Link className="text-xs font-semibold text-primary hover:underline" to={`/admin/items/${info.itemId}`}>
            Xem bài đăng
          </Link>
        ) : null}
      </div>
    </div>
  );
}

export function AdminTransactionDetail() {
  const { transactionId } = useParams();
  const id = Number(transactionId);
  const [detail, setDetail] = useState<AdminRequestDetail | null>(null);
  const [error, setError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  const [cancelling, setCancelling] = useState(false);
  const [messages, setMessages] = useState<AdminChatMessage[] | null>(null);
  const [messagesLoading, setMessagesLoading] = useState(false);
  const [messagesError, setMessagesError] = useState('');

  useEffect(() => {
    if (!Number.isInteger(id) || id <= 0) {
      setError('Không tìm thấy giao dịch này.');
      return;
    }
    let active = true;
    setError('');
    adminRequestApi
      .detail(id)
      .then((response) => {
        if (active) setDetail(response);
      })
      .catch((caught) => {
        if (active) setError(adminErrorMessage(caught, 'Không tải được chi tiết giao dịch. Vui lòng thử lại.'));
      });
    return () => {
      active = false;
    };
  }, [id, reloadKey]);

  const loadMessages = async () => {
    setMessagesLoading(true);
    setMessagesError('');
    try {
      setMessages(await adminRequestApi.messages(id));
    } catch (caught) {
      setMessagesError(adminErrorMessage(caught, 'Không tải được tin nhắn. Vui lòng thử lại.'));
    } finally {
      setMessagesLoading(false);
    }
  };

  if (error) {
    return (
      <section className="space-y-4">
        <Alert tone="error">{error}</Alert>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => setReloadKey((current) => current + 1)}>
            Thử lại
          </Button>
          <Link
            to="/admin/transactions"
            className="inline-flex min-h-11 items-center rounded-md border border-border px-4 text-sm font-semibold"
          >
            Về danh sách giao dịch
          </Link>
        </div>
      </section>
    );
  }
  if (!detail) return <SkeletonRows label="Đang tải chi tiết giao dịch" />;

  return (
    <section className="space-y-5">
      <PageHeader
        title={detail.item.title || 'Bài đã xoá'}
        description={`${detail.type === 'SWAP' ? 'Trao đổi' : 'Cho tặng'}. ${requestStatusLabel[detail.status]}.`}
        action={
          <div className="flex flex-wrap gap-2">
            <Link
              to="/admin/transactions"
              className="inline-flex min-h-11 items-center rounded-md border border-border px-4 text-sm font-semibold"
            >
              Về danh sách
            </Link>
            {detail.cancellable ? (
              <Button variant="danger" onClick={() => setCancelling(true)}>
                Huỷ giao dịch
              </Button>
            ) : null}
          </div>
        }
      />

      <div className="grid gap-5 lg:grid-cols-2">
        <div className="space-y-5">
          <Card title="Hai bên">
            <div className="grid gap-3 sm:grid-cols-2">
              <Party title="Chủ bài" party={detail.donor} />
              <Party title="Người nhận" party={detail.receiver} />
            </div>
          </Card>

          <Card title="Món đồ">
            <div className="space-y-3">
              <ItemBox title="Bài đăng" info={detail.item} />
              {detail.offeredItem ? <ItemBox title="Món đem đổi" info={detail.offeredItem} /> : null}
            </div>
          </Card>

          <Card title="Giao nhận">
            <dl className="divide-y divide-border/60">
              <Row label="Hình thức">
                {detail.deliveryMethod ? deliveryMethodLabels[detail.deliveryMethod] : 'Chưa chọn'}
              </Row>
              <Row label="Địa điểm hoặc địa chỉ">{detail.meetingPlace || '—'}</Row>
              <Row label="Giờ hẹn">{formatDateTime(detail.meetingTime)}</Row>
            </dl>
            {detail.photos.length > 0 ? (
              <div className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-3">
                {detail.photos.map((photo) => (
                  <figure key={photo.mediaId} className="text-xs text-text-muted">
                    <PrivateImage
                      path={photo.url}
                      alt="Ảnh giao nhận"
                      className="h-28 w-full rounded-md object-cover"
                    />
                    <figcaption className="mt-1">
                      {stageLabels[photo.stage] ?? photo.stage}
                      {photo.uploaderName ? `, ${photo.uploaderName}` : ''}
                    </figcaption>
                  </figure>
                ))}
              </div>
            ) : (
              <p className="mt-3 text-sm text-text-muted">Chưa có ảnh giao nhận.</p>
            )}
          </Card>
        </div>

        <div className="space-y-5">
          <Card title="Dòng thời gian">
            <ol className="space-y-3 border-l border-border pl-4 text-sm">
              {detail.timeline.map((event, index) => (
                <li key={`${event.label}-${index}`}>
                  <p className="font-semibold text-text-primary">{event.label}</p>
                  <p className="text-xs text-text-muted">{formatDateTime(event.at)}</p>
                  {event.note ? <p className="text-text-secondary">Lý do: {event.note}</p> : null}
                </li>
              ))}
            </ol>
          </Card>

          <Card title="Báo cáo và khiếu nại">
            {detail.reports.length === 0 ? (
              <p className="text-sm text-text-muted">Giao dịch chưa có báo cáo hay khiếu nại.</p>
            ) : (
              <>
                <ul className="divide-y divide-border/60 text-sm">
                  {detail.reports.map((report) => (
                    <li key={report.id} className="flex flex-wrap items-center justify-between gap-2 py-2">
                      <span>
                        {reportReasonLabels[report.reason] ?? report.reason}
                        {report.reporterName ? `, do ${report.reporterName} gửi` : ''}
                        {`, ${formatDateTime(report.createdAt)}`}
                      </span>
                      <span className="text-xs font-semibold text-text-secondary">
                        {reportStatusLabels[report.status]}
                      </span>
                    </li>
                  ))}
                </ul>
                <Link className="mt-2 inline-block text-sm font-semibold text-primary hover:underline" to="/admin/complaints">
                  Mở trang Khiếu nại và báo cáo
                </Link>
              </>
            )}
          </Card>

          <Card title="Điểm tích luỹ">
            {detail.points.length === 0 ? (
              <p className="text-sm text-text-muted">Chưa cộng điểm cho giao dịch này.</p>
            ) : (
              <ul className="divide-y divide-border/60 text-sm">
                {detail.points.map((award, index) => (
                  <li key={`${award.userName}-${index}`} className="flex justify-between gap-3 py-2">
                    <span>{award.userName || 'Người dùng đã xóa'}</span>
                    <span className="font-semibold tabular-nums text-success">+{award.points}</span>
                  </li>
                ))}
              </ul>
            )}
          </Card>

          <Card title="Tin nhắn">
            {!detail.messagesViewable ? (
              <p className="text-sm text-text-muted">
                Chỉ xem được tin nhắn khi giao dịch có báo cáo hoặc khiếu nại.
              </p>
            ) : messages === null ? (
              <div className="space-y-2">
                <p className="text-sm text-text-secondary">
                  Tin nhắn là dữ liệu riêng tư của hai bên. Mỗi lần mở phần này đều được ghi nhật ký.
                </p>
                {messagesError ? <Alert tone="error">{messagesError}</Alert> : null}
                <Button variant="outline" disabled={messagesLoading} onClick={() => void loadMessages()}>
                  {messagesLoading ? 'Đang tải...' : 'Xem tin nhắn'}
                </Button>
              </div>
            ) : messages.length === 0 ? (
              <p className="text-sm text-text-muted">Cuộc trò chuyện chưa có tin nhắn nào.</p>
            ) : (
              <ul className="space-y-2 text-sm">
                {messages.map((message) => (
                  <li key={message.id} className="rounded-md bg-surface-low p-3">
                    <p className="text-xs text-text-muted">
                      {message.senderRole}
                      {message.senderName ? ` (${message.senderName})` : ''}, {formatDateTime(message.createdAt)}
                    </p>
                    {message.mediaUrl ? (
                      <PrivateImage path={message.mediaUrl} alt="Ảnh trong tin nhắn" className="mt-1 h-32 rounded-md object-cover" />
                    ) : null}
                    {message.itemTitle ? <p className="text-text-secondary">Món được nhắc: {message.itemTitle}</p> : null}
                    {message.content ? <p className="whitespace-pre-line text-text-primary">{message.content}</p> : null}
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>
      </div>

      {cancelling ? (
        <CancelDialog
          row={{
            id: detail.id,
            itemTitle: detail.item.title,
            donorName: detail.donor?.name ?? null,
            receiverName: detail.receiver.name,
          }}
          onClose={() => setCancelling(false)}
          onDone={() => {
            setCancelling(false);
            setReloadKey((current) => current + 1);
          }}
        />
      ) : null}
    </section>
  );
}
