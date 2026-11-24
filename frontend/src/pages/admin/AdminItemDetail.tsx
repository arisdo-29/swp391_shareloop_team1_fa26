import { useEffect, useState, type ReactNode } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { Alert, Button, PageHeader, SkeletonRows } from '../../components/ui';
import { adminItemApi, type AdminItemFullDetail } from '../../services/adminItemApi';
import { requestStatusLabel } from '../../utils/request';
import type { ItemRequestStatus } from '../../types/domain';
import { adminErrorMessage } from './adminErrors';
import {
  formatDateTime,
  hiddenSourceLabels,
  itemStatusLabels,
  ledgerTypeLabels,
  reportReasonLabels,
  reportStatusLabels,
} from './adminItemDisplay';
import { AdminItemVisibilityDialog } from './AdminItemVisibilityDialog';
import { ModerationImageGallery } from './ModerationParts';
import { conditionLabels, offerTypeLabels } from './moderationDisplay';
import type { AdminLedgerRouteState } from './adminLedgerNavigation';

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

export function AdminItemDetail() {
  const { itemId } = useParams();
  const location = useLocation();
  const ledgerReturn = (location.state as AdminLedgerRouteState | null)?.ledgerReturn;
  const id = Number(itemId);
  const [detail, setDetail] = useState<AdminItemFullDetail | null>(null);
  const [error, setError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  const [dialogMode, setDialogMode] = useState<'hide' | 'unhide' | null>(null);
  const backTarget = ledgerReturn ? '/admin/finance/ledger' : '/admin/items';
  const backLabel = ledgerReturn ? 'Về lịch sử giao dịch Credit' : 'Về danh sách bài đăng';
  const backState = ledgerReturn ? { restoreLedger: ledgerReturn } : undefined;

  useEffect(() => {
    if (!Number.isInteger(id) || id <= 0) {
      setError('Không tìm thấy bài đăng này.');
      return;
    }
    let active = true;
    setError('');
    adminItemApi
      .detail(id)
      .then((response) => {
        if (active) setDetail(response);
      })
      .catch((caught) => {
        if (active) setError(adminErrorMessage(caught, 'Không tải được chi tiết bài đăng. Vui lòng thử lại.'));
      });
    return () => {
      active = false;
    };
  }, [id, reloadKey]);

  if (error) {
    return (
      <section className="space-y-4">
        <Alert tone="error">{error}</Alert>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => setReloadKey((current) => current + 1)}>
            Thử lại
          </Button>
          <Link
            to={backTarget}
            state={backState}
            className="inline-flex min-h-11 items-center rounded-md border border-border px-4 text-sm font-semibold"
          >
            {backLabel}
          </Link>
        </div>
      </section>
    );
  }
  if (!detail) return <SkeletonRows label="Đang tải chi tiết bài đăng" />;

  const { summary } = detail;
  const item = detail.item.item;
  const hideBlocked = detail.hasRunningTransaction || summary.status === 'RESERVED' || summary.status === 'TRADED';

  return (
    <section className="space-y-5">
      <PageHeader
        title={summary.title}
        description={`${itemStatusLabels[summary.status]}. ${summary.active ? 'Đang hiện cho người dùng.' : `Đang ẩn${summary.hiddenSource ? `: ${hiddenSourceLabels[summary.hiddenSource]}` : ''}.`}`}
        action={
          <div className="flex flex-wrap gap-2">
            <Link
              to={backTarget}
              state={backState}
              className="inline-flex min-h-11 items-center rounded-md border border-border px-4 text-sm font-semibold"
            >
              {ledgerReturn ? 'Về lịch sử giao dịch Credit' : 'Về danh sách'}
            </Link>
            {summary.active ? (
              <Button variant="danger" disabled={hideBlocked} onClick={() => setDialogMode('hide')}>
                Ẩn bài
              </Button>
            ) : (
              <Button onClick={() => setDialogMode('unhide')}>Hiện lại bài</Button>
            )}
          </div>
        }
      />

      {summary.active && hideBlocked ? (
        <Alert tone="warning">
          Bài đang có giao dịch chạy nên chưa ẩn được. Hãy huỷ giao dịch ở trang Giao dịch trước khi ẩn bài.{' '}
          {detail.runningRequestId ? (
            <Link className="font-semibold underline" to={`/admin/transactions/${detail.runningRequestId}`}>
              Mở giao dịch đang chạy
            </Link>
          ) : null}
        </Alert>
      ) : null}
      {!summary.active && detail.hiddenReason ? (
        <Alert tone="info">Lý do Admin đã ẩn: {detail.hiddenReason}</Alert>
      ) : null}

      <div className="grid gap-5 lg:grid-cols-2">
        <Card title="Nội dung bài">
          <dl className="divide-y divide-border/60">
            <Row label="Chủ bài">
              {summary.ownerName || 'Người dùng đã xóa'}
              {summary.ownerEmail ? ` (${summary.ownerEmail})` : ''}
            </Row>
            <Row label="Danh mục">{summary.categoryName || '—'}</Row>
            <Row label="Hình thức">{offerTypeLabels[summary.offerType]}</Row>
            <Row label="Tình trạng">{conditionLabels[item.condition]}</Row>
            {item.brand ? <Row label="Thương hiệu">{item.brand}</Row> : null}
            {item.desiredItem ? <Row label="Muốn đổi lấy">{item.desiredItem}</Row> : null}
            {detail.item.attributes.map((attribute) => (
              <Row key={attribute.attributeId} label={attribute.name || 'Thuộc tính'}>
                {attribute.value}
              </Row>
            ))}
            <Row label="Tạo lúc">{formatDateTime(summary.createdAt)}</Row>
            <Row label="Duyệt lúc">{formatDateTime(summary.approvedAt)}</Row>
            <Row label="Hết hạn hiển thị">{formatDateTime(summary.expireAt)}</Row>
            <Row label="Đẩy bài">{summary.boosting ? `Đang đẩy tới ${formatDateTime(summary.boostedUntil)}` : 'Không đẩy'}</Row>
          </dl>
          <p className="mt-3 whitespace-pre-line text-sm text-text-secondary">{item.description}</p>
          {item.defectNote ? <p className="mt-2 text-sm text-text-secondary">Ghi chú lỗi: {item.defectNote}</p> : null}
          <ModerationImageGallery images={item.images} defectImages={item.defectImages ?? []} />
        </Card>

        <div className="space-y-5">
          <Card title="Phí và Credit">
            <p className="text-sm text-text-secondary">
              Phí đăng đã thu: <span className="font-semibold">{summary.postFeePaid} Credit</span>. Ẩn bài vi phạm không hoàn phí.
            </p>
            {detail.feeLines.length === 0 ? (
              <p className="mt-2 text-sm text-text-muted">Bài chưa có giao dịch Credit nào.</p>
            ) : (
              <ul className="mt-2 divide-y divide-border/60 text-sm">
                {detail.feeLines.map((line) => (
                  <li key={line.id} className="flex justify-between gap-3 py-2">
                    <span>
                      {ledgerTypeLabels[line.type]} lúc {formatDateTime(line.createdAt)}
                    </span>
                    <span className={`font-semibold tabular-nums ${line.amount >= 0 ? 'text-success' : 'text-error'}`}>
                      {line.amount > 0 ? `+${line.amount}` : line.amount}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </Card>

          <Card title="Yêu cầu và giao dịch">
            {detail.requests.length === 0 ? (
              <p className="text-sm text-text-muted">Chưa có ai gửi yêu cầu cho bài này.</p>
            ) : (
              <ul className="divide-y divide-border/60 text-sm">
                {detail.requests.map((request) => (
                  <li key={request.id} className="flex flex-wrap items-center justify-between gap-2 py-2">
                    <div>
                      <p className="font-semibold text-text-primary">{request.receiverName || 'Người dùng đã xóa'}</p>
                      <p className="text-xs text-text-muted">
                        {request.type === 'SWAP' ? 'Trao đổi' : 'Cho tặng'}
                        {request.offeredItemTitle ? `, đề nghị đổi «${request.offeredItemTitle}»` : ''}
                        {`, ${formatDateTime(request.createdAt)}`}
                      </p>
                    </div>
                    <div className="flex items-center gap-3">
                      <span className="text-xs font-semibold text-text-secondary">
                        {requestStatusLabel[request.status as ItemRequestStatus] ?? request.status}
                      </span>
                      {request.running ? (
                        <Link className="text-xs font-semibold text-primary hover:underline" to={`/admin/transactions/${request.id}`}>
                          Mở giao dịch
                        </Link>
                      ) : null}
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </Card>

          <Card title="Báo cáo">
            {detail.reports.length === 0 ? (
              <p className="text-sm text-text-muted">Bài chưa bị báo cáo.</p>
            ) : (
              <ul className="divide-y divide-border/60 text-sm">
                {detail.reports.map((report) => (
                  <li key={report.id} className="flex flex-wrap items-center justify-between gap-2 py-2">
                    <span>
                      {reportReasonLabels[report.reason] ?? report.reason}, {formatDateTime(report.createdAt)}
                    </span>
                    <span className="text-xs font-semibold text-text-secondary">{reportStatusLabels[report.status]}</span>
                  </li>
                ))}
              </ul>
            )}
            {detail.reports.length > 0 ? (
              <Link className="mt-2 inline-block text-sm font-semibold text-primary hover:underline" to="/admin/complaints">
                Mở trang Khiếu nại và báo cáo
              </Link>
            ) : null}
          </Card>

          <Card title="Lịch sử ẩn hiện">
            {detail.visibilityHistory.length === 0 ? (
              <p className="text-sm text-text-muted">Admin chưa ẩn hay hiện lại bài này.</p>
            ) : (
              <ul className="divide-y divide-border/60 text-sm">
                {detail.visibilityHistory.map((log) => (
                  <li key={log.id} className="py-2">
                    <p className="font-semibold text-text-primary">
                      {log.typeLabel ?? log.type} bởi {log.actorName || 'quản trị viên'}
                    </p>
                    <p className="text-xs text-text-muted">{formatDateTime(log.createdAt)}</p>
                    {log.reason ? <p className="text-text-secondary">Lý do: {log.reason}</p> : null}
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>
      </div>

      {dialogMode ? (
        <AdminItemVisibilityDialog
          itemId={summary.id}
          itemTitle={summary.title}
          mode={dialogMode}
          onClose={() => setDialogMode(null)}
          onDone={(updated) => {
            setDialogMode(null);
            setDetail(updated);
          }}
        />
      ) : null}
    </section>
  );
}
