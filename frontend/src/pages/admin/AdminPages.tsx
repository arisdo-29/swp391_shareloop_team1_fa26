import { AdminComplaints } from './AdminComplaints';
import { AdminConfigs } from './AdminConfigs';
import { AdminUserPicker } from './AdminUserPicker';
import { AdminAnomalyContent } from '../../components/admin/AdminAnomalyContent';
import type { AdminUserSummary } from '../../services/adminUserApi';
import { useEffect, useId, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '../../app/hooks';
import { actions, selectCurrentUser, selectData } from '../../app/store';
import {
  Alert,
  Avatar,
  Button,
  EmptyState,
  Field,
  Icon,
  ItemImage,
  PageHeader,
  SearchField,
  Select,
  Stat,
  StatusBadge,
  TextArea,
  TypeBadge,
} from '../../components/ui';
import { CATEGORIES } from '../../constants/domain';
import { formatVnd } from '../../utils/formatting';
import { adminLexiconApi, type AdminLexiconEntry, type AdminLexiconRequest, type LexiconMatch } from '../../services/adminLexiconApi';
import {
  adminCategoryApi,
  type AdminCategoryAttributeRequest,
  type AdminCategoryAttributeResponse,
  type AdminCategoryRequest,
  type AdminCategoryResponse,
} from '../../services/adminCategoryApi';
import { adminAreaApi, type AdminAreaRequest, type AdminAreaResponse } from '../../services/adminAreaApi';
import type { AttributeDataType } from '../../services/catalogApi';
import {
  ADMIN_CHECKLIST_QUESTION_MAX_LENGTH,
  adminChecklistApi,
  type AdminChecklistEntry,
} from '../../services/adminChecklistApi';
import {
  adminAuditApi,
  type ActivityLogParams,
  type ActivityLogResponse,
} from '../../services/adminAuditApi';
import type { ApiError } from '../../services/http';
import { toBackendUserId } from '../../services/requestApi';
import type { AppStateData, ForbiddenKeyword, Item, KeywordAction, RankRule, ReputationPointRule, SupportedDistrict, User } from '../../types/domain';

function Panel({
  title,
  description,
  action,
  children,
}: {
  title: string;
  description?: string;
  action?: ReactNode;
  children: ReactNode;
}) {
  return (
    <section>
      <PageHeader title={title} description={description} action={action} />
      {children}
    </section>
  );
}
function FilterBar({ children }: { children: ReactNode }) {
  return (
    <div className="mb-5 flex flex-col gap-3 rounded-lg bg-white p-3 ring-1 ring-border/80 sm:flex-row sm:items-center">
      {children}
    </div>
  );
}

export function AdminDashboard() {
  const data = useAppSelector(selectData);
  const navigate = useNavigate();
  const pending = data.items.filter((i) => i.status === 'pending' || i.status === 'PENDING_REVIEW' || i.status === 'VIOLATION');
  const activeTx = data.transactions.filter((t) => !['COMPLETED', 'CANCELLED'].includes(t.status));
  return (
    <Panel
      title="Tổng quan vận hành"
      description="Tình hình cộng đồng và các công việc cần xử lý hôm nay."
    >
      <div className="grid gap-5 sm:grid-cols-2 xl:grid-cols-4">
        <Stat label="Thành viên" value={data.users.length} detail="+2 trong 7 ngày" />
        <Stat label="Bài chờ duyệt" value={pending.length} detail="Cần kiểm tra nội dung" />
        <Stat label="Giao dịch đang mở" value={activeTx.length} />
        <Stat
          label="Credit tạm giữ"
          value={data.users.reduce((s, u) => s + u.holdCredit, 0)}
          detail="Không dùng cho phí giao dịch"
        />
      </div>
      <div className="mt-8 grid gap-6 xl:grid-cols-[1.2fr_.8fr]">
        <section>
          <div className="mb-3 flex items-center justify-between">
            <h2 className="section-title">Việc cần xử lý</h2>
            <Link to="/admin/moderation" className="text-xs font-semibold text-primary">
              Xem hàng chờ
            </Link>
          </div>
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Công việc</th>
                  <th>Đối tượng</th>
                  <th>Mức độ</th>
                  <th>Cập nhật</th>
                </tr>
              </thead>
              <tbody>
                <tr className="cursor-pointer hover:bg-surface-low" onClick={() => navigate('/admin/moderation')}>
                  <td className="font-semibold">Duyệt bài đăng mới</td>
                  <td>{pending.length} bài</td>
                  <td>
                    <span className="text-warning">Cần xử lý</span>
                  </td>
                  <td>Hôm nay</td>
                </tr>
                <tr className="cursor-pointer hover:bg-surface-low" onClick={() => navigate('/admin/finance')}>
                  <td className="font-semibold">Top-up chờ xác nhận</td>
                  <td>{data.topups.filter((t) => t.status === 'pending').length} yêu cầu</td>
                  <td>Thông thường</td>
                  <td>15 phút trước</td>
                </tr>
                <tr className="cursor-pointer hover:bg-surface-low" onClick={() => navigate('/admin/complaints')}>
                  <td className="font-semibold">Giao dịch cần theo dõi</td>
                  <td>
                    {data.complaints.filter((item) => item.status !== 'resolved').length} khiếu nại
                  </td>
                  <td>
                    <span className="text-error">Ưu tiên</span>
                  </td>
                  <td>1 giờ trước</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
        <section>
          <h2 className="mb-3 section-title">Hoạt động gần đây</h2>
          <div className="rounded-lg bg-white px-4 ring-1 ring-border/80">
            {data.auditLogs.slice(0, 5).map((log) => (
              <div key={log.id} className="border-b border-border/70 py-3 last:border-0">
                <p className="text-sm font-semibold">{log.detail}</p>
                <p className="mt-1 text-xs text-text-muted">
                  {new Date(log.createdAt).toLocaleString('vi-VN')}
                </p>
              </div>
            ))}
          </div>
        </section>
      </div>
    </Panel>
  );
}

export function AdminUsers() {
  const data = useAppSelector(selectData);
  const admin = useAppSelector(selectCurrentUser)!;
  const dispatch = useAppDispatch();
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('');
  const [accountAction, setAccountAction] = useState<{ user: User; locked: boolean } | null>(null);
  const rows = data.users
    .filter((u) => `${u.name} ${u.email} ${u.phone}`.toLowerCase().includes(query.toLowerCase()))
    .filter((u) => !status || u.status === status);
  return (
    <Panel
      title="Danh sách người dùng"
      description="Tra cứu hồ sơ, trạng thái và hoạt động tài khoản."
    >
      <FilterBar>
        <div className="flex-1">
          <SearchField
            placeholder="Tên, email hoặc số điện thoại..."
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>
        <select
          className="input sm:w-44"
          value={status}
          onChange={(event) => setStatus(event.target.value)}
        >
          <option value="">Tất cả trạng thái</option>
          <option value="active">Hoạt động</option>
          <option value="locked">Đã khóa</option>
          <option value="suspended">Tạm khóa</option>
        </select>
      </FilterBar>
      <div className="table-wrap">
        <table className="data-table">
          <thead>
            <tr>
              <th>Người dùng</th>
              <th>Khu vực</th>
              <th>Credit</th>
              <th>Uy tín</th>
              <th>Trạng thái</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {rows.map((u) => (
              <tr key={u.id}>
                <td>
                  <Link
                    to={`/admin/users/${u.id}`}
                    className="font-semibold text-primary hover:underline"
                  >
                    {u.name}
                  </Link>
                  <p className="mt-1 text-xs text-text-muted">{u.email}</p>
                </td>
                <td>{u.district}</td>
                <td className="tabular-nums">
                  {u.availableCredit} / {u.holdCredit} tạm giữ
                </td>
                <td>
                  {u.reputationStars.toFixed(1)} · {u.rank}
                </td>
                <td>
                  <StatusBadge status={u.status} />
                </td>
                <td>
                  {u.role !== 'admin' ? (
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => setAccountAction({ user: u, locked: u.status !== 'locked' })}
                    >
                      {u.status === 'locked' ? 'Mở khóa' : 'Khóa'}
                    </Button>
                  ) : null}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {accountAction ? (
        <AccountStatusModal
          user={accountAction.user}
          locked={accountAction.locked}
          history={data.auditLogs.filter((log) => log.targetType === 'user' && log.targetId === accountAction.user.id && ['ACCOUNT_LOCKED', 'ACCOUNT_UNLOCKED'].includes(log.action))}
          onClose={() => setAccountAction(null)}
          onConfirm={(reason) => {
            dispatch(
              actions.lockUser({
                adminId: admin.id,
                userId: accountAction.user.id,
                locked: accountAction.locked,
                reason,
              }),
            );
            setAccountAction(null);
          }}
        />
      ) : null}
    </Panel>
  );
}

function AccountStatusModal({
  user,
  locked,
  history,
  onClose,
  onConfirm,
}: {
  user: User;
  locked: boolean;
  history: AppStateData['auditLogs'];
  onClose: () => void;
  onConfirm: (reason: string) => void;
}) {
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const actionLabel = locked ? 'Khóa tài khoản' : 'Mở khóa tài khoản';
  return (
    <Modal title={actionLabel} onClose={onClose}>
      <div className="grid gap-3 text-sm sm:grid-cols-2">
        <Info label="Người dùng" value={user.name} />
        <Info label="Trạng thái hiện tại" value={user.status === 'locked' ? 'Đã khóa' : 'Đang hoạt động'} />
      </div>
      <TextArea
        className="mt-4"
        label="Lý do"
        value={reason}
        error={error}
        placeholder="Nhập lý do để lưu vào Audit Log..."
        onChange={(event) => {
          setReason(event.target.value);
          setError('');
        }}
      />
      <div className="mt-5">
        <h3 className="text-sm font-bold">Lịch sử khóa / mở khóa</h3>
        <div className="mt-2 max-h-48 space-y-2 overflow-y-auto">
          {history.length ? history.map((log) => (
            <div key={log.id} className="rounded-md border border-border px-3 py-2 text-xs">
              <p className="font-semibold">{log.detail}</p>
              <p className="mt-1 text-text-muted">{new Date(log.createdAt).toLocaleString('vi-VN')}</p>
            </div>
          )) : <p className="text-sm text-text-muted">Chưa có lịch sử khóa / mở khóa.</p>}
        </div>
      </div>
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="ghost" onClick={onClose}>Hủy</Button>
        <Button
          variant={locked ? 'danger' : 'primary'}
          onClick={() => {
            if (!reason.trim()) {
              setError('Vui lòng nhập lý do.');
              return;
            }
            onConfirm(reason.trim());
          }}
        >
          Xác nhận
        </Button>
      </div>
    </Modal>
  );
}

export function AdminUserDetail() {
  const { userId } = useParams();
  const data = useAppSelector(selectData);
  const u = data.users.find((x) => x.id === userId);
  if (!u)
    return (
      <Panel title="Không tìm thấy người dùng">
        <p>Hồ sơ không tồn tại.</p>
      </Panel>
    );
  const txs = data.transactions.filter((t) => t.ownerId === u.id || t.requesterId === u.id);
  const creditHistory = data.creditHistory.filter((entry) => entry.userId === u.id);
  const pointHistory = data.pointHistory.filter((entry) => entry.userId === u.id);
  return (
    <Panel
      title={u.name}
      description={`${u.email} · ${u.phone} · ${u.district}`}
      action={<StatusBadge status={u.status} />}
    >
      <div className="space-y-6">
        <section className="grid gap-5 rounded-lg bg-white p-5 ring-1 ring-border/80 sm:grid-cols-3">
          <Stat label="Credit khả dụng" value={u.availableCredit} />
          <Stat label="Credit tạm giữ" value={u.holdCredit} />
          <Stat label="Tổng giao dịch" value={txs.length} />
        </section>
        <section>
          <h2 className="mb-3 section-title">Lịch sử Credit</h2>
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Thời gian</th>
                  <th>Nội dung</th>
                  <th>Thay đổi</th>
                  <th>Số dư</th>
                </tr>
              </thead>
              <tbody>
                {creditHistory.map((entry) => (
                  <tr key={entry.id}>
                    <td>{new Date(entry.createdAt).toLocaleString('vi-VN')}</td>
                    <td>{entry.note}</td>
                    <td className={entry.amount < 0 ? 'text-error' : 'text-success'}>
                      {entry.amount > 0 ? '+' : ''}
                      {entry.amount}
                    </td>
                    <td>{entry.balance}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
        <section>
          <h2 className="mb-3 section-title">Lịch sử điểm</h2>
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Thời gian</th>
                  <th>Sự kiện</th>
                  <th>Thay đổi</th>
                  <th>Điểm sau thay đổi</th>
                </tr>
              </thead>
              <tbody>
                {pointHistory.map((entry) => (
                  <tr key={entry.id}>
                    <td>{new Date(entry.createdAt).toLocaleString('vi-VN')}</td>
                    <td>{entry.event}</td>
                    <td className={entry.change < 0 ? 'text-error' : 'text-success'}>
                      {entry.change > 0 ? '+' : ''}{entry.change}
                    </td>
                    <td>{entry.pointsAfter}</td>
                  </tr>
                ))}
                {!pointHistory.length ? (
                  <tr>
                    <td colSpan={4} className="text-text-muted">Chưa có lịch sử điểm.</td>
                  </tr>
                ) : null}
              </tbody>
            </table>
          </div>
        </section>
        <section>
          <h2 className="mb-3 section-title">Giao dịch gần đây</h2>
          <div className="table-wrap">
            <table className="data-table">
              <tbody>
                {txs.map((tx) => (
                  <tr key={tx.id}>
                    <td>
                      <Link
                        className="font-semibold text-primary"
                        to={`/admin/transactions/${tx.id}`}
                      >
                        {tx.id}
                      </Link>
                    </td>
                    <td>{data.items.find((i) => i.id === tx.itemId)?.title}</td>
                    <td>
                      <StatusBadge status={tx.status} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </Panel>
  );
}

export function AdminTransactions() {
  const data = useAppSelector(selectData);
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('');
  const rows = data.transactions
    .filter((tx) =>
      `${tx.id} ${data.items.find((i) => i.id === tx.itemId)?.title}`
        .toLowerCase()
        .includes(query.toLowerCase()),
    )
    .filter((tx) => !status || tx.status === status);
  return (
    <Panel title="Giao dịch" description="Theo dõi tiến trình, lịch giao nhận và xác nhận bàn giao.">
      <FilterBar>
        <div className="flex-1">
          <SearchField
            placeholder="Mã giao dịch hoặc tên món..."
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>
        <select
          className="input sm:w-52"
          value={status}
          onChange={(event) => setStatus(event.target.value)}
        >
          <option value="">Tất cả trạng thái</option>
          <option value="NEGOTIATING">Đang thương lượng</option>
          <option value="SCHEDULE_CONFIRMED">Đã chốt lịch</option>
          <option value="WAITING_HANDOVER">Chờ bàn giao</option>
          <option value="COMPLETED">Hoàn tất</option>
          <option value="DISPUTED">Khiếu nại</option>
        </select>
      </FilterBar>
      <div className="table-wrap">
        <table className="data-table">
          <thead>
            <tr>
              <th>Mã giao dịch</th>
              <th>Món đồ</th>
              <th>Loại</th>
              <th>Trạng thái</th>
              <th>Lịch giao nhận</th>
              <th>Ngày tạo</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((tx) => (
              <tr key={tx.id}>
                <td>
                  <Link to={`/admin/transactions/${tx.id}`} className="font-semibold text-primary">
                    {tx.id}
                  </Link>
                </td>
                <td className="max-w-[220px] truncate">
                  {data.items.find((i) => i.id === tx.itemId)?.title}
                </td>
                <td>{tx.type === 'gift' ? 'Cho tặng' : 'Trao đổi'}</td>
                <td>
                  <StatusBadge status={tx.status} />
                </td>
                <td>{tx.status === 'WAITING_HANDOVER' || tx.status === 'COMPLETED' ? 'Đã chốt' : 'Đang xử lý'}</td>
                <td>{new Date(tx.createdAt).toLocaleDateString('vi-VN')}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Panel>
  );
}

export function AdminTransactionDetail() {
  const { transactionId } = useParams();
  const data = useAppSelector(selectData);
  const tx = data.transactions.find((t) => t.id === transactionId);
  if (!tx)
    return (
      <Panel title="Không tìm thấy giao dịch">
        <p>Giao dịch không tồn tại.</p>
      </Panel>
    );
  const item = data.items.find((i) => i.id === tx.itemId)!;
  const owner = data.users.find((u) => u.id === tx.ownerId)!;
  const requester = data.users.find((u) => u.id === tx.requesterId)!;
  const ho = data.handovers.find((h) => h.id === tx.handoverId);
  return (
    <Panel
      title={tx.id}
      description="Chi tiết đầy đủ của giao dịch và dấu vết xác nhận."
      action={<StatusBadge status={tx.status} />}
    >
      <div className="grid gap-6 xl:grid-cols-[1fr_360px]">
        <div className="space-y-6">
          <section className="rounded-lg bg-white p-5 ring-1 ring-border/80">
            <div className="flex gap-4">
              <ItemImage
                src={item.images[0]}
                alt={item.title}
                className="size-24 rounded-md object-cover"
              />
              <div>
                <TypeBadge type={item.type} />
                <h2 className="mt-2 font-bold">{item.title}</h2>
                <p className="mt-1 text-sm text-text-muted">
                  {owner.name} ↔ {requester.name}
                </p>
              </div>
            </div>
          </section>
          <section className="rounded-lg bg-white p-5 ring-1 ring-border/80">
            <h2 className="section-title">Bàn giao</h2>
            {ho ? (
              <dl className="mt-4 grid gap-4 text-sm sm:grid-cols-2">
                <Info label="Thời gian" value={`${ho.date}, ${ho.time}`} />
                <Info label="Phương thức" value={ho.method} />
                <Info label="Địa điểm" value={`${ho.address}, ${ho.district}`} />
                <Info
                  label="Trạng thái"
                  value={ho.status === 'confirmed' ? 'Đã thống nhất' : 'Chờ phản hồi'}
                />
              </dl>
            ) : (
              <p className="mt-3 text-sm text-text-muted">Hai bên chưa đề xuất lịch giao nhận.</p>
            )}
          </section>
        </div>
        <aside className="h-fit rounded-lg bg-white p-5 ring-1 ring-border/80">
          <h2 className="section-title">Kiểm soát giao dịch</h2>
          <div className="mt-4 space-y-4">
            <Info
              label="Trạng thái liên hệ"
              value={['WAITING_HANDOVER', 'SENDER_CONFIRMED', 'RECEIVER_CONFIRMED', 'COMPLETED'].includes(tx.status) ? 'Đã mở khóa' : 'Chưa mở khóa'}
            />
            <Info
              label="Người gửi xác nhận"
              value={tx.senderConfirmed ? 'Đã xác nhận' : 'Chưa xác nhận'}
            />
            <Info
              label="Người nhận xác nhận"
              value={tx.receiverConfirmed ? 'Đã xác nhận' : 'Chưa xác nhận'}
            />
            <Info
              label="Bằng chứng"
              value={`${(tx.senderEvidence?.length ?? 0) + (tx.receiverEvidence?.length ?? 0)} tệp`}
            />
          </div>
        </aside>
      </div>
    </Panel>
  );
}

export function AdminFinance() {
  const data = useAppSelector(selectData);
  const admin = useAppSelector(selectCurrentUser)!;
  const dispatch = useAppDispatch();
  const pending = data.topups.filter((t) => t.status === 'pending');
  return (
    <Panel
      title="Báo cáo tài chính"
      description="Credit phát hành, đang giữ và các yêu cầu nạp tiền."
    >
      <div className="grid gap-5 sm:grid-cols-4">
        <Stat
          label="Credit toàn hệ thống"
          value={data.users.reduce((s, u) => s + u.totalCredit, 0)}
        />
        <Stat label="Credit tạm giữ" value={data.users.reduce((s, u) => s + u.holdCredit, 0)} />
        <Stat label="Doanh thu hệ thống" value={data.systemRevenue} />
        <Stat label="Top-up chờ duyệt" value={pending.length} />
      </div>
      <h2 className="mb-3 mt-8 section-title">Yêu cầu top-up</h2>
      <div className="table-wrap">
        <table className="data-table">
          <thead>
            <tr>
              <th>Người dùng</th>
              <th>Số tiền</th>
              <th>Credit</th>
              <th>Thời gian</th>
              <th>Trạng thái</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {data.topups.map((t) => {
              const u = data.users.find((x) => x.id === t.userId);
              return (
                <tr key={t.id}>
                  <td>{u?.name}</td>
                  <td>{formatVnd(t.vnd)}</td>
                  <td>{t.amount}</td>
                  <td>{new Date(t.createdAt).toLocaleString('vi-VN')}</td>
                  <td>
                    <span className={t.status === 'completed' ? 'text-success' : 'text-warning'}>
                      {t.status === 'completed' ? 'Đã xác nhận' : 'Chờ xác nhận'}
                    </span>
                  </td>
                  <td>
                    {t.status !== 'completed' ? (
                      <Button
                        size="sm"
                        onClick={() =>
                          dispatch(actions.confirmTopup({ topupId: t.id, adminId: admin.id }))
                        }
                      >
                        Xác nhận
                      </Button>
                    ) : null}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </Panel>
  );
}

export function AdminFinanceUserLookup() {
  const data = useAppSelector(selectData);
  const [query, setQuery] = useState('');
  const normalized = query.trim().toLowerCase();
  const rows = normalized
    ? data.users.filter((user) =>
        `${user.name} ${user.username} ${user.email} ${user.phone}`
          .toLowerCase()
          .includes(normalized),
      )
    : [];
  return (
    <Panel
      title="Tra cứu người dùng"
      description="Tìm người dùng trước khi xem chi tiết tài chính và lịch sử giao dịch."
    >
      <FilterBar>
        <div className="flex-1">
          <SearchField
            placeholder="Tìm theo tên, email, số điện thoại hoặc tên tài khoản..."
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
        </div>
      </FilterBar>
      {rows.length ? (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Người dùng</th>
                <th>Username</th>
                <th>Email</th>
                <th>SĐT</th>
                <th>Credit khả dụng</th>
                <th>Credit tạm giữ</th>
                <th>Trạng thái</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {rows.map((user) => (
                <tr key={user.id}>
                  <td>
                    <div className="flex items-center gap-2">
                      <Avatar user={user} />
                      <span className="font-semibold">{user.name}</span>
                    </div>
                  </td>
                  <td>{user.username}</td>
                  <td>{user.email}</td>
                  <td>{user.phone}</td>
                  <td>{user.availableCredit}</td>
                  <td>{user.holdCredit}</td>
                  <td><StatusBadge status={user.status} /></td>
                  <td>
                    <Link to={`/admin/finance/users/${user.id}`}>
                      <Button size="sm" variant="outline">Xem chi tiết</Button>
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <EmptyState
          icon="search"
          title={query ? 'Không tìm thấy người dùng' : 'Nhập từ khóa để tra cứu'}
          text="Kết quả sẽ hiển thị sau khi bạn nhập tên, email, số điện thoại hoặc username."
        />
      )}
    </Panel>
  );
}

export function AdminSettings() {
  const dispatch = useAppDispatch();
  const admin = useAppSelector(selectCurrentUser)!;
  const backendAdminId = toBackendUserId(admin.id);
  const isApiAdmin = backendAdminId !== null && admin.role === 'admin';

  if (isApiAdmin) return <AdminConfigs />;

  return (
    <Panel title="Cấu hình Credit" description="Giao dịch ShareLoop hiện miễn phí cho cả Swap và Give.">
      <div className="max-w-2xl rounded-lg bg-white p-6 ring-1 ring-border/80">
        <div className="rounded-md bg-primary-faint p-4 text-sm text-text-secondary">Phí chỉ phát sinh khi đăng bài: 5 Credit mỗi bài. Gửi yêu cầu, chọn người, chat, chốt lịch, giao nhận và hoàn tất đều không trừ Credit.</div>
        <div className="mt-8 border-t border-border pt-5">
          <h3 className="text-sm font-bold">Dữ liệu demo</h3>
          <p className="mt-1 text-xs leading-5 text-text-muted">Khôi phục toàn bộ người dùng, món đồ, giao dịch và cấu hình về trạng thái ban đầu.</p>
          <Button className="mt-3" variant="danger" onClick={() => dispatch(actions.resetDemoData())}>
            Reset Demo Data
          </Button>
        </div>
      </div>
    </Panel>
  );
}

export function AdminAuditLogs() {
  const [actionTypes, setActionTypes] = useState<{ type: string; label: string }[]>([]);
  const [logs, setLogs] = useState<ActivityLogResponse[]>([]);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  const [actorUser, setActorUser] = useState<AdminUserSummary | null>(null);
  const [actionType, setActionType] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [filters, setFilters] = useState<Omit<ActivityLogParams, 'page' | 'size'>>({});

  useEffect(() => {
    let active = true;
    adminAuditApi.types().then((types) => { if (active) setActionTypes(types); }).catch(() => { /* Backend cũ vẫn dùng danh sách nhãn sẵn có. */ });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');

    adminAuditApi
      .list({ ...filters, page, size: pageSize })
      .then((response) => {
        if (!active) return;
        setLogs(response.content);
        setTotalElements(response.totalElements);
        setTotalPages(response.totalPages);
      })
      .catch((apiError: ApiError) => {
        if (!active) return;
        setLogs([]);
        setTotalElements(0);
        setTotalPages(0);
        setError(activityLogErrorMessage(apiError));
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [filters, page, pageSize, reloadKey]);

  const applyFilters = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (from && to && from > to) {
      setError('Khoảng ngày không hợp lệ');
      return;
    }
    setError('');
    setPage(0);
    setFilters({
      actorId: actorUser?.id,
      type: actionType || undefined,
      from: from || undefined,
      to: to || undefined,
    });
  };

  return (
    <Panel title="Nhật ký hành động">
      <form onSubmit={applyFilters} className="mb-5 rounded-lg bg-white p-4 ring-1 ring-border/80">
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
          <div>
            <span className="label">Người thực hiện</span>
            <AdminUserPicker searchLabel="Tìm người thực hiện" selected={actorUser} disabled={loading} onSelect={setActorUser} />
          </div>
          <Select label="Hành động" value={actionType} onChange={(event) => setActionType(event.target.value)}>
            <option value="">Tất cả hành động</option>
            {(actionTypes.length ? actionTypes.map((entry) => [entry.type, entry.label]) : Object.entries(activityLogTypeLabels)).map(([value, label]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </Select>
          <Field label="Từ ngày" type="date" value={from} onChange={(event) => setFrom(event.target.value)} />
          <Field label="Đến ngày" type="date" value={to} onChange={(event) => setTo(event.target.value)} />
          <div className="flex items-end">
            <Button type="submit" className="w-full" disabled={loading}>Lọc nhật ký</Button>
          </div>
        </div>
      </form>

      {loading ? (
        <EmptyState title="Đang tải nhật ký" text="Vui lòng chờ trong giây lát." />
      ) : error ? (
        <div className="rounded-md bg-red-50 p-4 text-sm leading-6 text-error">
          <p>{error}</p>
          <Button className="mt-3" size="sm" variant="outline" onClick={() => setReloadKey((value) => value + 1)}>
            Thử lại
          </Button>
        </div>
      ) : logs.length === 0 ? (
        <EmptyState icon="history" title="Chưa có nhật ký" text="Không có hành động nào phù hợp với bộ lọc hiện tại." />
      ) : (
        <>
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Thời gian</th>
                  <th>Người thực hiện</th>
                  <th>Hành động</th>
                  <th>Đối tượng</th>
                  <th>Chi tiết</th>
                </tr>
              </thead>
              <tbody>
                {logs.map((log) => (
                  <tr key={log.id}>
                    <td className="whitespace-nowrap">{formatActivityLogTime(log.createdAt)}</td>
                    <td>{log.actorName ?? (log.actorId ? 'Quản trị viên' : 'Hệ thống')}</td>
                    <td>{log.typeLabel ?? activityLogTypeLabel(log.type)}</td>
                    <td>{activityLogTarget(log)}</td>
                    <td><ActivityLogDetail log={log} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="mt-4 flex flex-col gap-3 rounded-lg bg-white p-3 text-sm text-text-secondary ring-1 ring-border/80 sm:flex-row sm:items-center sm:justify-between">
            <p>
              {totalElements.toLocaleString('vi-VN')} bản ghi · Trang {page + 1}/{Math.max(totalPages, 1)}
            </p>
            <div className="flex items-center gap-2">
              <Select
                aria-label="Số bản ghi mỗi trang"
                value={pageSize}
                onChange={(event) => {
                  setPageSize(Number(event.target.value));
                  setPage(0);
                }}
              >
                <option value={10}>10 / trang</option>
                <option value={20}>20 / trang</option>
                <option value={50}>50 / trang</option>
              </Select>
              <Button size="sm" variant="outline" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>
                Trang trước
              </Button>
              <Button size="sm" variant="outline" disabled={page + 1 >= totalPages} onClick={() => setPage((value) => value + 1)}>
                Trang sau
              </Button>
            </div>
          </div>
        </>
      )}
    </Panel>
  );
}

const activityLogTypeLabels: Record<string, string> = {
  APPROVE_ITEM: 'Duyệt bài',
  REJECT_ITEM: 'Từ chối bài',
  REVIEW_APPROVE: 'Duyệt bài',
  REVIEW_REJECT: 'Từ chối bài',
  CONFIG_UPDATE: 'Cập nhật cấu hình',
  ADMIN_ACTION: 'Thao tác quản trị',
  REVIEW: 'Kiểm duyệt bài',
  AI_CALL: 'Gọi trợ lý AI',
  POINTS: 'Thay đổi điểm uy tín',
  STARS: 'Thay đổi sao uy tín',
  SECURITY: 'Sự kiện bảo mật',
};

function activityLogTypeLabel(type: string) {
  return activityLogTypeLabels[type.trim().toUpperCase()] ?? 'Khác';
}

interface ActivityLogCheck {
  passed: boolean;
  question: string;
}

function ActivityLogDetail({ log }: { log: ActivityLogResponse }) {
  const [expanded, setExpanded] = useState(false);
  const content = log.detail?.trim() || log.reason?.trim() || 'Không có thông tin chi tiết';
  const checks = activityLogChecks(log, content);
  const isStructured = checks.length > 0 || /^[{[]/.test(content);

  if (!isStructured) {
    return <p className="max-w-xl line-clamp-2 leading-5">{content}</p>;
  }

  const passedCount = checks.filter((check) => check.passed).length;
  const summary = checks.length
    ? `Đạt đủ ${passedCount}/${checks.length} mục kiểm duyệt`
    : 'Có dữ liệu chi tiết';

  return (
    <div className="max-w-xl">
      <p className={expanded ? 'leading-5' : 'line-clamp-2 leading-5'}>{summary}</p>
      <button
        type="button"
        className="mt-1 text-xs font-semibold text-primary hover:text-primary-hover"
        aria-expanded={expanded}
        onClick={() => setExpanded((value) => !value)}
      >
        {expanded ? 'Thu gọn' : 'Xem chi tiết'}
      </button>
      {expanded ? (
        checks.length ? (
          <ul className="mt-2 max-h-48 space-y-1.5 overflow-y-auto rounded-md bg-surface-low p-2 text-xs leading-5">
            {checks.map((check, index) => (
              <li key={`${check.question}-${index}`} className="flex items-start gap-2">
                <span className={check.passed ? 'font-bold text-success' : 'font-bold text-error'}>
                  {check.passed ? '✓' : '✗'}
                </span>
                <span>{check.question}</span>
              </li>
            ))}
          </ul>
        ) : (
          <p className="mt-2 rounded-md bg-surface-low p-2 text-xs leading-5 text-text-muted">
            Dữ liệu chi tiết đã được ghi nhận.
          </p>
        )
      ) : null}
    </div>
  );
}

function activityLogChecks(log: ActivityLogResponse, content: string): ActivityLogCheck[] {
  const dataChecks = activityLogChecksFromValue(log.data);
  if (dataChecks.length) return dataChecks;

  if (/^[{[]/.test(content)) {
    try {
      const parsedChecks = activityLogChecksFromValue(JSON.parse(content) as unknown);
      if (parsedChecks.length) return parsedChecks;
    } catch {
      // Backend cũ có thể trả Map.toString(), được xử lý bằng biểu thức bên dưới.
    }
  }

  if (!/checks|passed|question/i.test(content)) return [];
  return [...content.matchAll(/\{passed=(true|false),\s*question=([^}]*)}/gi)].flatMap((match) => {
    const question = match[2]?.trim();
    return question ? [{ passed: match[1].toLowerCase() === 'true', question }] : [];
  });
}

function activityLogChecksFromValue(value: unknown): ActivityLogCheck[] {
  const entries = Array.isArray(value)
    ? value
    : value && typeof value === 'object' && Array.isArray((value as Record<string, unknown>).checks)
      ? (value as Record<string, unknown>).checks as unknown[]
      : [];

  return entries.flatMap((entry) => {
    if (!entry || typeof entry !== 'object') return [];
    const record = entry as Record<string, unknown>;
    const question = typeof record.statement === 'string' ? record.statement.trim() : typeof record.question === 'string' ? record.question.trim() : '';
    const passed = record.passed === true || record.passed === 'true';
    return question ? [{ passed, question }] : [];
  });
}

const activityLogTargetLabels: Record<string, string> = {
  ITEM: 'Bài đăng',
  USER: 'Người dùng',
  REQUEST: 'Yêu cầu',
  REPORT: 'Báo cáo',
  CONFIG: 'Cấu hình',
};

function activityLogTarget(log: ActivityLogResponse) {
  if (log.targetTitle) return log.targetTitle;
  // Không có tên hiển thị thì chỉ nêu loại đối tượng, không in id kỹ thuật ra màn hình.
  return activityLogTargetLabels[log.targetType?.trim().toUpperCase() ?? ''] ?? 'Đối tượng';
}

function formatActivityLogTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return 'Không xác định';
  return new Intl.DateTimeFormat('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh',
    dateStyle: 'short',
    timeStyle: 'medium',
  }).format(date);
}

function activityLogErrorMessage(error: ApiError) {
  if (error.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
  if (error.status === 403) return 'Tài khoản hiện tại không có quyền xem nhật ký hành động.';
  if (error.status === 400) return 'Khoảng ngày không hợp lệ';
  if (error.status === 0) return 'Không thể kết nối máy chủ. Vui lòng kiểm tra kết nối và thử lại.';
  return error.message || 'Không tải được nhật ký hành động. Vui lòng thử lại.';
}

export function AdminContentPage({
  kind,
}: {
  kind:
    | 'expired'
    | 'categories'
    | 'checklist'
    | 'keywords'
    | 'districts'
    | 'reputation'
    | 'locked'
    | 'disputes'
    | 'alerts'
    | 'ranks';
}) {
  if (kind === 'keywords') return <AdminKeywords />;
  if (kind === 'categories') return <AdminCategories />;
  if (kind === 'checklist') return <AdminChecklist />;
  if (kind === 'districts') return <AdminDistricts />;
  if (kind === 'disputes') return <AdminComplaints />;
  if (kind === 'ranks') return <AdminRanks />;
  if (kind === 'reputation') return <AdminReputation />;
  if (kind === 'alerts') return <AdminAnomalyContent />;
  return <AdminStaticContent kind={kind} />;
}

function AdminKeywords() {
  const admin = useAppSelector(selectCurrentUser)!;
  return toBackendUserId(admin.id) !== null && admin.role === 'admin'
    ? <ApiAdminKeywords />
    : <MockAdminKeywords />;
}

const lexiconGroupLabels: Record<string, string> = {
  WEAPONS: 'Vũ khí',
  DRUGS: 'Ma túy',
  CONTACT: 'Thông tin liên hệ',
};

const lexiconLevelLabels: Record<string, string> = {
  BLOCK: 'Chặn đăng',
  REVIEW: 'Cần duyệt thủ công',
};

const lexiconScopeLabels: Record<string, string> = {
  POST: 'Bài đăng',
  CHAT: 'Tin nhắn',
  MESSAGE: 'Tin nhắn',
  BOTH: 'Bài đăng và tin nhắn',
};

function getLexiconLabel(labels: Record<string, string>, value: string) {
  return labels[value.trim().toUpperCase()] ?? 'Khác';
}

function ApiAdminKeywords() {
  const [entries, setEntries] = useState<AdminLexiconEntry[]>([]);
  const [editing, setEditing] = useState<AdminLexiconEntry | null | 'new'>(null);
  const [loading, setLoading] = useState(true);
  const [savingId, setSavingId] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  const [testText, setTestText] = useState('');
  const [testMatches, setTestMatches] = useState<LexiconMatch[]>([]);
  const [testing, setTesting] = useState(false);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    adminLexiconApi.list()
      .then((response) => {
        if (active) setEntries(response);
      })
      .catch((apiError: ApiError) => {
        if (active) setError(apiError.message || 'Không tải được từ điển kiểm duyệt.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => { active = false; };
  }, [reloadKey]);

  const save = async (input: AdminLexiconRequest) => {
    setSavingId(editing === 'new' ? 0 : editing?.id ?? null);
    setError('');
    try {
      const saved = editing === 'new'
        ? await adminLexiconApi.create(input)
        : await adminLexiconApi.update(editing!.id, input);
      setEntries((current) => editing === 'new'
        ? [...current, saved]
        : current.map((entry) => entry.id === saved.id ? saved : entry));
      setEditing(null);
      setNotice(editing === 'new' ? 'Đã thêm từ khóa.' : 'Đã cập nhật từ khóa.');
    } catch (apiError) {
      const response = apiError as ApiError;
      setError(response.message || 'Không lưu được từ khóa.');
    } finally {
      setSavingId(null);
    }
  };

  const toggleActive = async (entry: AdminLexiconEntry) => {
    setSavingId(entry.id);
    setError('');
    try {
      const updated = await adminLexiconApi.setActive(entry.id, !entry.active);
      setEntries((current) => current.map((item) => item.id === updated.id ? updated : item));
    } catch (apiError) {
      const response = apiError as ApiError;
      setError(response.message || 'Không cập nhật được trạng thái từ khóa.');
    } finally {
      setSavingId(null);
    }
  };

  const testLexicon = async () => {
    if (!testText.trim()) return;
    setTesting(true);
    setError('');
    try {
      setTestMatches(await adminLexiconApi.test(testText.trim()));
    } catch (apiError) {
      const response = apiError as ApiError;
      setError(response.message || 'Không kiểm tra được nội dung.');
    } finally {
      setTesting(false);
    }
  };

  return (
    <Panel
      title="Từ khóa kiểm duyệt"
      action={<Button icon="add" onClick={() => setEditing('new')}>Thêm từ khóa</Button>}
    >
      {notice ? <div className="mb-4"><Alert tone="success">{notice}</Alert></div> : null}
      {error ? <div className="mb-4 space-y-3"><Alert tone="error">{error}</Alert><Button size="sm" variant="outline" onClick={() => setReloadKey((key) => key + 1)}>Tải lại</Button></div> : null}
      <section className="mb-5 rounded-lg bg-white p-4 ring-1 ring-border/80">
        <TextArea label="Kiểm tra nội dung" value={testText} onChange={(event) => setTestText(event.target.value)} placeholder="Nhập nội dung cần đối chiếu với từ điển..." />
        <Button className="mt-3" variant="outline" disabled={testing || !testText.trim()} onClick={() => void testLexicon()}>{testing ? 'Đang kiểm tra...' : 'Kiểm tra'}</Button>
        {testMatches.length ? <div className="mt-3 space-y-1 text-xs text-text-secondary">{testMatches.map((match, index) => <p key={`${match.keyword}-${index}`}><strong>{match.keyword}</strong> · {getLexiconLabel(lexiconLevelLabels, match.level)} · {getLexiconLabel(lexiconGroupLabels, match.group)} · {match.matchedAs}</p>)}</div> : null}
      </section>
      {loading ? <EmptyState title="Đang tải từ điển" text="Vui lòng chờ trong giây lát." /> : entries.length ? (
        <div className="table-wrap"><table className="data-table"><thead><tr><th>Từ khóa</th><th>Nhóm</th><th>Mức độ</th><th>Phạm vi</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>
          {entries.map((entry) => <tr key={entry.id}><td className="font-semibold">{entry.keyword}</td><td>{getLexiconLabel(lexiconGroupLabels, entry.group)}</td><td>{getLexiconLabel(lexiconLevelLabels, entry.level)}</td><td>{getLexiconLabel(lexiconScopeLabels, entry.scope)}</td><td>{entry.active ? 'Đang hiển thị' : 'Đang ẩn'}</td><td className="space-x-2"><Button size="sm" variant="ghost" disabled={savingId === entry.id} onClick={() => setEditing(entry)}>Sửa</Button><Button size="sm" variant="outline" disabled={savingId === entry.id} onClick={() => void toggleActive(entry)}>{entry.active ? 'Tạm ngưng' : 'Kích hoạt'}</Button></td></tr>)}
        </tbody></table></div>
      ) : <EmptyState title="Chưa có từ khóa" text="Hiện chưa có mục từ điển kiểm duyệt nào." />}
      {editing ? <ApiLexiconModal entry={editing === 'new' ? null : editing} saving={savingId !== null} onClose={() => setEditing(null)} onSave={(input) => void save(input)} /> : null}
    </Panel>
  );
}

function ApiLexiconModal({ entry, saving, onClose, onSave }: { entry: AdminLexiconEntry | null; saving: boolean; onClose: () => void; onSave: (input: AdminLexiconRequest) => void }) {
  const [keyword, setKeyword] = useState(entry?.keyword ?? '');
  const [group, setGroup] = useState(entry?.group ?? '');
  const [level, setLevel] = useState(entry?.level ?? '');
  const [scope, setScope] = useState(entry?.scope ?? '');
  const [matchMode, setMatchMode] = useState(entry?.matchMode ?? '');
  const [exceptions, setExceptions] = useState(entry?.exceptions.join(', ') ?? '');
  const valid = keyword.trim() && group.trim() && level.trim() && scope.trim();
  const messageScope = scope.trim().toUpperCase() === 'MESSAGE' ? 'MESSAGE' : 'CHAT';
  return <Modal title={entry ? 'Sửa từ khóa' : 'Thêm từ khóa'} onClose={onClose}>
    <div className="space-y-4"><Field label="Từ khóa" value={keyword} onChange={(event) => setKeyword(event.target.value)} /><Select label="Nhóm" value={group} onChange={(event) => setGroup(event.target.value)}><option value="">Chọn nhóm</option>{group && !lexiconGroupLabels[group.trim().toUpperCase()] ? <option value={group}>Khác</option> : null}{Object.entries(lexiconGroupLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</Select><Select label="Mức độ" value={level} onChange={(event) => setLevel(event.target.value)}><option value="">Chọn mức độ</option>{level && !lexiconLevelLabels[level.trim().toUpperCase()] ? <option value={level}>Khác</option> : null}{Object.entries(lexiconLevelLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</Select><Select label="Phạm vi" value={scope} onChange={(event) => setScope(event.target.value)}><option value="">Chọn phạm vi</option>{scope && !lexiconScopeLabels[scope.trim().toUpperCase()] ? <option value={scope}>Khác</option> : null}<option value="POST">Bài đăng</option><option value={messageScope}>Tin nhắn</option><option value="BOTH">Bài đăng và tin nhắn</option></Select><Field label="Chế độ khớp (không bắt buộc)" value={matchMode} onChange={(event) => setMatchMode(event.target.value)} /><Field label="Ngoại lệ, phân cách bằng dấu phẩy" value={exceptions} onChange={(event) => setExceptions(event.target.value)} /></div>
    <div className="mt-6 flex justify-end gap-2"><Button variant="ghost" disabled={saving} onClick={onClose}>Hủy</Button><Button disabled={saving || !valid} onClick={() => onSave({ keyword: keyword.trim(), group: group.trim(), level: level.trim(), scope: scope.trim(), ...(matchMode.trim() ? { matchMode: matchMode.trim() } : {}), exceptions: exceptions.split(',').map((value) => value.trim()).filter(Boolean) })}>{saving ? 'Đang lưu...' : 'Lưu'}</Button></div>
  </Modal>;
}

function MockAdminKeywords() {
  const data = useAppSelector(selectData);
  const admin = useAppSelector(selectCurrentUser)!;
  const dispatch = useAppDispatch();
  const [editing, setEditing] = useState<ForbiddenKeyword | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<ForbiddenKeyword | null>(null);
  return (
    <Panel
      title="Từ khóa cấm"
      description="Các cụm từ được dùng khi kiểm tra nội dung bài đăng."
      action={<Button icon="add" onClick={() => setEditing({ id: '', keyword: '', detections: 0, action: 'flag' })}>Thêm từ khóa</Button>}
    >
      <div className="table-wrap">
        <table className="data-table">
          <thead>
            <tr>
              <th>Từ khóa</th>
              <th>Số lần phát hiện</th>
              <th>Mức xử lý</th>
              <th>Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {data.keywords.map((keyword) => (
              <tr key={keyword.id}>
                <td className="font-semibold">{keyword.keyword}</td>
                <td>{keyword.detections}</td>
                <td>{keyword.action === 'block' ? 'Chặn bài' : 'Tự động gắn cờ'}</td>
                <td className="space-x-2">
                  <Button size="sm" variant="ghost" onClick={() => setEditing(keyword)}>Sửa</Button>
                  <Button size="sm" variant="danger" onClick={() => setConfirmDelete(keyword)}>Xóa</Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {editing ? (
        <KeywordModal
          keyword={editing}
          onClose={() => setEditing(null)}
          onSave={(keyword, action) => {
            if (editing.id) {
              dispatch(actions.updateKeyword({ adminId: admin.id, id: editing.id, keyword, action }));
            } else {
              dispatch(actions.addKeyword({ adminId: admin.id, keyword, action }));
            }
            setEditing(null);
          }}
        />
      ) : null}
      {confirmDelete ? (
        <ConfirmDialog
          title="Xóa từ khóa?"
          text={`Từ khóa "${confirmDelete.keyword}" sẽ không còn được dùng để kiểm tra bài đăng.`}
          onCancel={() => setConfirmDelete(null)}
          onConfirm={() => {
            dispatch(actions.deleteKeyword({ adminId: admin.id, id: confirmDelete.id }));
            setConfirmDelete(null);
          }}
        />
      ) : null}
    </Panel>
  );
}

function KeywordModal({
  keyword,
  onClose,
  onSave,
}: {
  keyword: ForbiddenKeyword;
  onClose: () => void;
  onSave: (..._args: [string, KeywordAction]) => void;
}) {
  const [value, setValue] = useState(keyword.keyword);
  const [action, setAction] = useState<KeywordAction>(keyword.action);
  return (
    <Modal title={keyword.id ? 'Sửa từ khóa' : 'Thêm từ khóa'} onClose={onClose}>
      <Field label="Từ khóa" value={value} onChange={(event) => setValue(event.target.value)} />
      <Select
        className="mt-4"
        label="Mức xử lý"
        value={action}
        onChange={(event) => setAction(event.target.value as KeywordAction)}
      >
        <option value="flag">Tự động gắn cờ</option>
        <option value="block">Chặn bài</option>
      </Select>
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="ghost" onClick={onClose}>Hủy</Button>
        <Button onClick={() => onSave(value, action)}>Lưu</Button>
      </div>
    </Modal>
  );
}

function AdminDistricts() {
  const admin = useAppSelector(selectCurrentUser)!;
  return toBackendUserId(admin.id) !== null && admin.role === 'admin'
    ? <ApiAdminAreas />
    : <DemoAdminDistricts />;
}

function ApiAdminAreas() {
  const [areas, setAreas] = useState<AdminAreaResponse[]>([]);
  const [editing, setEditing] = useState<AdminAreaResponse | null | 'new'>(null);
  const [loading, setLoading] = useState(true);
  const [savingId, setSavingId] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    adminAreaApi.list()
      .then((response) => { if (active) setAreas(response); })
      .catch((apiError: ApiError) => { if (active) setError(apiError.message || 'Không tải được danh sách khu vực.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [reloadKey]);

  const save = async (input: AdminAreaRequest) => {
    setSavingId(editing === 'new' ? 0 : editing?.id ?? null);
    setError('');
    setNotice('');
    try {
      const saved = editing === 'new'
        ? await adminAreaApi.create(input)
        : await adminAreaApi.update(editing!.id, input);
      setAreas((current) => editing === 'new'
        ? [...current, saved]
        : current.map((entry) => entry.id === saved.id ? saved : entry));
      setNotice(editing === 'new' ? 'Đã thêm khu vực.' : 'Đã cập nhật khu vực.');
      setEditing(null);
    } catch (apiError) {
      const response = apiError as ApiError;
      setError(response.message || 'Không lưu được khu vực.');
    } finally {
      setSavingId(null);
    }
  };

  const toggleActive = async (area: AdminAreaResponse) => {
    setSavingId(area.id);
    setError('');
    setNotice('');
    try {
      const updated = await adminAreaApi.setActive(area.id, !area.active);
      setAreas((current) => current.map((entry) => entry.id === updated.id ? updated : entry));
    } catch (apiError) {
      const response = apiError as ApiError;
      setError(response.message || 'Không cập nhật được trạng thái khu vực.');
    } finally {
      setSavingId(null);
    }
  };

  return <Panel title="Khu vực" description="Quản lý tỉnh, thành phố và khu vực con dùng cho tìm kiếm và đăng bài." action={<Button icon="add" onClick={() => setEditing('new')}>Thêm khu vực</Button>}>
    {notice ? <div className="mb-4"><Alert tone="success">{notice}</Alert></div> : null}
    {error ? <div className="mb-4 space-y-3"><Alert tone="error">{error}</Alert><Button size="sm" variant="outline" onClick={() => setReloadKey((key) => key + 1)}>Tải lại</Button></div> : null}
    {loading ? <EmptyState title="Đang tải khu vực" text="Vui lòng chờ trong giây lát." /> : areas.length ? <div className="table-wrap"><table className="data-table"><thead><tr><th>Tên</th><th>Khu vực cha</th><th>Cấp</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>{areas.map((area) => <tr key={area.id}><td className="font-semibold">{area.name}</td><td>{areas.find((entry) => entry.id === area.parentId)?.name ?? '—'}</td><td>{area.level}</td><td>{area.active ? 'Đang dùng' : 'Tạm ẩn'}</td><td className="space-x-2"><Button size="sm" variant="ghost" disabled={savingId === area.id} onClick={() => setEditing(area)}>Sửa</Button><Button size="sm" variant="outline" disabled={savingId === area.id} onClick={() => void toggleActive(area)}>{area.active ? 'Tạm ẩn' : 'Kích hoạt'}</Button></td></tr>)}</tbody></table></div> : <EmptyState title="Chưa có khu vực" text="Hiện chưa có khu vực nào." />}
    {editing ? <ApiAreaModal area={editing === 'new' ? null : editing} areas={areas} saving={savingId !== null} onClose={() => setEditing(null)} onSave={(input) => void save(input)} /> : null}
  </Panel>;
}

function ApiAreaModal({ area, areas, saving, onClose, onSave }: { area: AdminAreaResponse | null; areas: AdminAreaResponse[]; saving: boolean; onClose: () => void; onSave: (input: AdminAreaRequest) => void }) {
  const [name, setName] = useState(area?.name ?? '');
  const [parentId, setParentId] = useState(area?.parentId ? String(area.parentId) : '');
  return <Modal title={area ? 'Sửa khu vực' : 'Thêm khu vực'} onClose={onClose}>
    <div className="space-y-4">
      <Field label="Tên khu vực" value={name} onChange={(event) => setName(event.target.value)} disabled={saving} />
      <Select label="Khu vực cha" value={parentId} onChange={(event) => setParentId(event.target.value)} disabled={saving}><option value="">Không có</option>{areas.filter((entry) => entry.id !== area?.id && entry.active).map((entry) => <option key={entry.id} value={entry.id}>{entry.name}</option>)}</Select>
    </div>
    <div className="mt-6 flex justify-end gap-2"><Button variant="ghost" disabled={saving} onClick={onClose}>Hủy</Button><Button disabled={saving || !name.trim()} onClick={() => onSave({ name: name.trim(), parentId: parentId ? Number(parentId) : null })}>{saving ? 'Đang lưu...' : 'Lưu'}</Button></div>
  </Modal>;
}

function DemoAdminDistricts() {
  const data = useAppSelector(selectData);
  const admin = useAppSelector(selectCurrentUser)!;
  const dispatch = useAppDispatch();
  const [editing, setEditing] = useState<SupportedDistrict | null>(null);
  const [error, setError] = useState('');
  const [confirmDelete, setConfirmDelete] = useState<SupportedDistrict | null>(null);
  return (
    <Panel
      title="Khu vực"
      description="Danh sách khu vực dùng chung cho tìm đồ, trợ lý AI và đăng đồ."
      action={<Button icon="add" onClick={() => setEditing({ id: '', name: '', status: 'active' })}>Thêm khu vực</Button>}
    >
      {error ? <div className="mb-4 rounded-md bg-red-50 px-4 py-3 text-sm text-error">{error}</div> : null}
      <div className="table-wrap">
        <table className="data-table">
          <thead>
            <tr>
              <th>Khu vực</th>
              <th>Số món</th>
              <th>Trạng thái</th>
              <th>Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {data.districts.map((district) => (
              <tr key={district.id}>
                <td className="font-semibold">{district.name}</td>
                <td>{data.items.filter((item) => item.district === district.name).length}</td>
                <td>Đang hỗ trợ</td>
                <td className="space-x-2">
                  <Button size="sm" variant="ghost" onClick={() => setEditing(district)}>Sửa</Button>
                  <Button size="sm" variant="danger" onClick={() => setConfirmDelete(district)}>Xóa</Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {editing ? (
        <DistrictModal
          district={editing}
          onClose={() => setEditing(null)}
          onSave={(name) => {
            const duplicate = data.districts.some(
              (entry) => entry.id !== editing.id && entry.name.toLowerCase() === name.trim().toLowerCase(),
            );
            if (duplicate) {
              setError('Khu vực đã tồn tại.');
              return;
            }
            if (editing.id) dispatch(actions.updateDistrict({ adminId: admin.id, id: editing.id, name }));
            else dispatch(actions.addDistrict({ adminId: admin.id, name }));
            setError('');
            setEditing(null);
          }}
        />
      ) : null}
      {confirmDelete ? (
        <ConfirmDialog
          title="Xóa khu vực?"
          text={`Khu vực "${confirmDelete.name}" sẽ biến mất khỏi các dropdown tìm kiếm và đăng đồ.`}
          onCancel={() => setConfirmDelete(null)}
          onConfirm={() => {
            dispatch(actions.deleteDistrict({ adminId: admin.id, id: confirmDelete.id }));
            setConfirmDelete(null);
          }}
        />
      ) : null}
    </Panel>
  );
}

function DistrictModal({
  district,
  onClose,
  onSave,
}: {
  district: SupportedDistrict;
  onClose: () => void;
  onSave: (..._args: [string]) => void;
}) {
  const [name, setName] = useState(district.name);
  return (
    <Modal title={district.id ? 'Sửa khu vực' : 'Thêm khu vực'} onClose={onClose}>
      <Field label="Tên khu vực" value={name} onChange={(event) => setName(event.target.value)} />
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="ghost" onClick={onClose}>Hủy</Button>
        <Button onClick={() => onSave(name)}>Lưu</Button>
      </div>
    </Modal>
  );
}

function AdminRanks() {
  const data = useAppSelector(selectData);
  const admin = useAppSelector(selectCurrentUser)!;
  const dispatch = useAppDispatch();
  const [editing, setEditing] = useState<RankRule | null>(null);
  const [toast, setToast] = useState('');
  const rows = [...data.ranks].sort((a, b) => a.minPoints - b.minPoints);
  return (
    <Panel
      title="Mốc hạng"
      description="Cấu hình khoảng điểm dùng để xác định hạng thành viên."
      action={<Button icon="add" onClick={() => setEditing(emptyRank())}>Thêm mốc hạng</Button>}
    >
      <RankTable ranks={rows} onEdit={setEditing} />
      {editing ? (
        <RankModal
          rank={editing}
          onClose={() => setEditing(null)}
          onSave={(name, minPoints, maxPoints, benefits, status) => {
            if (editing.id) {
              dispatch(actions.updateRankRule({ adminId: admin.id, id: editing.id, name, minPoints, maxPoints, benefits, status }));
            } else {
              dispatch(actions.addRankRule({ adminId: admin.id, name, minPoints, maxPoints, benefits, status }));
            }
            setEditing(null);
            setToast('Đã cập nhật mốc hạng thành công.');
          }}
        />
      ) : null}
      {toast ? <Toast message={toast} onClose={() => setToast('')} /> : null}
    </Panel>
  );
}

function RankModal({
  rank,
  onClose,
  onSave,
}: {
  rank: RankRule;
  onClose: () => void;
  onSave: (..._args: [string, number, number | undefined, string, RankRule['status']]) => void;
}) {
  const [name, setName] = useState(rank.name);
  const [minPoints, setMinPoints] = useState(rank.minPoints);
  const [maxPoints, setMaxPoints] = useState(rank.maxPoints ?? '');
  const [benefits, setBenefits] = useState(rank.benefits ?? '');
  const [status, setStatus] = useState<RankRule['status']>(rank.status ?? 'active');
  return (
    <Modal title={rank.id ? 'Chỉnh sửa mốc hạng' : 'Thêm mốc hạng'} onClose={onClose}>
      <Field label="Tên hạng" value={name} onChange={(event) => setName(event.target.value)} />
      <div className="mt-4 grid gap-4 sm:grid-cols-2">
        <Field label="Điểm tối thiểu" type="number" value={minPoints} onChange={(event) => setMinPoints(Number(event.target.value))} />
        <Field label="Điểm tối đa" type="number" value={maxPoints} onChange={(event) => setMaxPoints(event.target.value === '' ? '' : Number(event.target.value))} />
      </div>
      <TextArea className="mt-4" label="Quyền lợi" value={benefits} onChange={(event) => setBenefits(event.target.value)} />
      <Select className="mt-4" label="Trạng thái" value={status} onChange={(event) => setStatus(event.target.value as RankRule['status'])}>
        <option value="active">Đang dùng</option>
        <option value="inactive">Tạm ẩn</option>
      </Select>
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="ghost" onClick={onClose}>Hủy</Button>
        <Button onClick={() => onSave(name, minPoints, maxPoints === '' ? undefined : Number(maxPoints), benefits, status)}>Lưu thay đổi</Button>
      </div>
    </Modal>
  );
}

function emptyRank(): RankRule {
  return { id: '', name: '', minPoints: 0, benefits: '', status: 'active' };
}

function RankTable({
  ranks,
  onEdit,
}: {
  ranks: RankRule[];
  onEdit: (..._args: [RankRule]) => void;
}) {
  return (
    <div className="table-wrap">
      <table className="data-table">
        <thead>
          <tr>
            <th>Tên hạng</th>
            <th>Điểm tối thiểu</th>
            <th>Quyền lợi</th>
            <th>Trạng thái</th>
            <th>Thao tác</th>
          </tr>
        </thead>
        <tbody>
          {ranks.map((rank) => (
            <tr key={rank.id}>
              <td className="font-semibold">{rank.name}</td>
              <td>{rank.minPoints}</td>
              <td>{rank.benefits || `${rank.minPoints} điểm trở lên`}</td>
              <td>{rank.status === 'inactive' ? 'Tạm ẩn' : 'Đang dùng'}</td>
              <td><Button size="sm" variant="ghost" onClick={() => onEdit(rank)}>Chỉnh sửa</Button></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function AdminReputation() {
  const data = useAppSelector(selectData);
  const admin = useAppSelector(selectCurrentUser)!;
  const dispatch = useAppDispatch();
  const [editingRank, setEditingRank] = useState<RankRule | null>(null);
  const [editingRule, setEditingRule] = useState<ReputationPointRule | null>(null);
  const [toast, setToast] = useState('');
  const rankRows = [...data.ranks].sort((a, b) => a.minPoints - b.minPoints);
  return (
    <Panel title="Uy tín & Hạng" description="TrustStars và điểm hạng được theo dõi riêng biệt.">
      <section>
        <h2 className="mb-3 section-title">Uy tín người dùng</h2>
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Người dùng</th>
                <th>TrustStars</th>
                <th>Điểm hạng</th>
                <th>Hạng hiện tại</th>
              </tr>
            </thead>
            <tbody>
              {data.users.filter((user) => user.role !== 'admin').map((user) => (
                <tr key={user.id}>
                  <td className="font-semibold">{user.name}</td>
                  <td>{user.reputationStars.toFixed(1)} ★</td>
                  <td>{user.rewardPoints}</td>
                  <td>{user.rank}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
      <section className="mt-8">
        <PageHeader
          title="Mốc hạng"
          action={<Button icon="add" onClick={() => setEditingRank(emptyRank())}>Thêm mốc hạng</Button>}
        />
        <RankTable ranks={rankRows} onEdit={setEditingRank} />
      </section>
      <section className="mt-8">
        <PageHeader
          title="Cấu hình cộng / trừ điểm"
          action={<Button icon="add" onClick={() => setEditingRule(emptyPointRule())}>Thêm quy tắc</Button>}
        />
        <PointRuleTable rules={data.pointRules} onEdit={setEditingRule} />
      </section>
      {editingRank ? (
        <RankModal
          rank={editingRank}
          onClose={() => setEditingRank(null)}
          onSave={(name, minPoints, maxPoints, benefits, status) => {
            if (editingRank.id) {
              dispatch(actions.updateRankRule({ adminId: admin.id, id: editingRank.id, name, minPoints, maxPoints, benefits, status }));
            } else {
              dispatch(actions.addRankRule({ adminId: admin.id, name, minPoints, maxPoints, benefits, status }));
            }
            setEditingRank(null);
            setToast('Đã cập nhật mốc hạng thành công.');
          }}
        />
      ) : null}
      {editingRule ? (
        <PointRuleModal
          rule={editingRule}
          onClose={() => setEditingRule(null)}
          onSave={(behavior, type, points, status, description) => {
            if (editingRule.id) {
              dispatch(actions.updatePointRule({ adminId: admin.id, id: editingRule.id, behavior, type, points, status, description }));
            } else {
              dispatch(actions.addPointRule({ adminId: admin.id, behavior, type, points, status, description }));
            }
            setEditingRule(null);
            setToast('Đã cập nhật quy tắc điểm thành công.');
          }}
        />
      ) : null}
      {toast ? <Toast message={toast} onClose={() => setToast('')} /> : null}
    </Panel>
  );
}

function emptyPointRule(): ReputationPointRule {
  return {
    id: '',
    key: `custom_${Date.now()}`,
    behavior: '',
    type: 'plus',
    points: 0,
    status: 'active',
    description: '',
  };
}

function PointRuleTable({
  rules,
  onEdit,
}: {
  rules: ReputationPointRule[];
  onEdit: (..._args: [ReputationPointRule]) => void;
}) {
  const typeLabel = { plus: 'Cộng', minus: 'Trừ' };
  const statusLabel = { active: 'Đang áp dụng', paused: 'Tạm dừng' };
  return (
    <div className="table-wrap">
      <table className="data-table">
        <thead>
          <tr>
            <th>Hành vi</th>
            <th>Loại</th>
            <th>Điểm thay đổi</th>
            <th>Trạng thái</th>
            <th>Thao tác</th>
          </tr>
        </thead>
        <tbody>
          {rules.map((rule) => (
            <tr key={rule.id}>
              <td className="font-semibold">{rule.behavior}</td>
              <td>{typeLabel[rule.type]}</td>
              <td className={rule.type === 'plus' ? 'text-success' : 'text-error'}>
                {rule.type === 'plus' ? '+' : '-'}{rule.points}
              </td>
              <td>{statusLabel[rule.status]}</td>
              <td><Button size="sm" variant="ghost" onClick={() => onEdit(rule)}>Chỉnh sửa</Button></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function PointRuleModal({
  rule,
  onClose,
  onSave,
}: {
  rule: ReputationPointRule;
  onClose: () => void;
  onSave: (..._args: [string, ReputationPointRule['type'], number, ReputationPointRule['status'], string]) => void;
}) {
  const [behavior, setBehavior] = useState(rule.behavior);
  const [type, setType] = useState<ReputationPointRule['type']>(rule.type);
  const [points, setPoints] = useState(rule.points);
  const [status, setStatus] = useState<ReputationPointRule['status']>(rule.status);
  const [description, setDescription] = useState(rule.description);
  return (
    <Modal title="Cấu hình quy tắc điểm" onClose={onClose}>
      <Field label="Tên hành vi" value={behavior} onChange={(event) => setBehavior(event.target.value)} />
      <div className="mt-4 grid gap-4 sm:grid-cols-2">
        <Select label="Loại" value={type} onChange={(event) => setType(event.target.value as ReputationPointRule['type'])}>
          <option value="plus">Cộng</option>
          <option value="minus">Trừ</option>
        </Select>
        <Field label="Số điểm" type="number" min={0} value={points} onChange={(event) => setPoints(Number(event.target.value))} />
      </div>
      <Select className="mt-4" label="Trạng thái" value={status} onChange={(event) => setStatus(event.target.value as ReputationPointRule['status'])}>
        <option value="active">Đang áp dụng</option>
        <option value="paused">Tạm dừng</option>
      </Select>
      <TextArea className="mt-4" label="Mô tả" value={description} onChange={(event) => setDescription(event.target.value)} />
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="ghost" onClick={onClose}>Hủy</Button>
        <Button onClick={() => onSave(behavior, type, points, status, description)}>Lưu thay đổi</Button>
      </div>
    </Modal>
  );
}

function AdminChecklist() {
  const admin = useAppSelector(selectCurrentUser)!;
  if (toBackendUserId(admin.id) === null || admin.role !== 'admin') {
    return (
      <Panel title="Checklist duyệt bài" description="Quản lý các tiêu chí kiểm duyệt từ Backend.">
        <Alert tone="warning">Vui lòng đăng nhập bằng tài khoản Admin Backend để quản lý checklist.</Alert>
      </Panel>
    );
  }
  return <ApiAdminChecklist />;
}

function sortAdminChecklist(entries: AdminChecklistEntry[]) {
  return [...entries].sort((left, right) => left.sortOrder - right.sortOrder || left.id - right.id);
}

function adminChecklistErrorMessage(error: unknown, fallback: string) {
  const apiError = error as ApiError;
  if (apiError.status === 400) {
    if (apiError.code === 'SETTING_CHECKLIST_AT_LEAST_ONE_REQUIRED') {
      return 'Phải luôn có ít nhất một tiêu chí đang hoạt động.';
    }
    return 'Dữ liệu checklist chưa hợp lệ. Vui lòng kiểm tra và thử lại.';
  }
  if (apiError.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
  if (apiError.status === 403) return 'Bạn không có quyền quản lý checklist.';
  if (apiError.status === 404) return 'Không tìm thấy tiêu chí checklist.';
  if (apiError.status === 0) return 'Không kết nối được máy chủ. Vui lòng thử lại.';
  return fallback;
}

function ApiAdminChecklist() {
  const [entries, setEntries] = useState<AdminChecklistEntry[]>([]);
  const [editing, setEditing] = useState<AdminChecklistEntry | null | 'new'>(null);
  const [loading, setLoading] = useState(true);
  const [savingId, setSavingId] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [editingError, setEditingError] = useState('');
  const [notice, setNotice] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  const mutationLock = useRef(false);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    adminChecklistApi.list()
      .then((response) => {
        if (active) setEntries(sortAdminChecklist(response));
      })
      .catch((apiError: ApiError) => {
        if (active) setError(adminChecklistErrorMessage(apiError, 'Không tải được checklist duyệt bài.'));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => { active = false; };
  }, [reloadKey]);

  const save = async (question: string) => {
    const trimmedQuestion = question.trim();
    const currentEditing = editing;
    if (
      mutationLock.current ||
      !currentEditing ||
      !trimmedQuestion ||
      trimmedQuestion.length > ADMIN_CHECKLIST_QUESTION_MAX_LENGTH
    ) return;
    mutationLock.current = true;
    setSavingId(currentEditing === 'new' ? 0 : currentEditing.id);
    setEditingError('');
    setNotice('');
    try {
      const saved = currentEditing === 'new'
        ? await adminChecklistApi.create({ question: trimmedQuestion })
        : await adminChecklistApi.update(currentEditing.id, { question: trimmedQuestion });
      setEntries((current) => sortAdminChecklist(currentEditing === 'new'
        ? [...current, saved]
        : current.map((entry) => entry.id === saved.id ? saved : entry)
      ));
      setNotice(currentEditing === 'new' ? 'Đã thêm tiêu chí.' : 'Đã cập nhật tiêu chí.');
      setEditing(null);
    } catch (apiError) {
      setEditingError(adminChecklistErrorMessage(apiError, 'Không lưu được tiêu chí.'));
    } finally {
      mutationLock.current = false;
      setSavingId(null);
    }
  };

  const toggleActive = async (entry: AdminChecklistEntry) => {
    if (mutationLock.current) return;
    mutationLock.current = true;
    setSavingId(entry.id);
    setError('');
    setNotice('');
    try {
      const updated = await adminChecklistApi.setActive(entry.id, !entry.active);
      setEntries((current) => current.map((item) => item.id === updated.id ? updated : item));
      setNotice(updated.active ? 'Đã kích hoạt tiêu chí.' : 'Đã tạm ngưng tiêu chí.');
    } catch (apiError) {
      setError(adminChecklistErrorMessage(apiError, 'Không cập nhật được trạng thái tiêu chí.'));
    } finally {
      mutationLock.current = false;
      setSavingId(null);
    }
  };

  const move = async (index: number, offset: -1 | 1) => {
    const targetIndex = index + offset;
    if (mutationLock.current || targetIndex < 0 || targetIndex >= entries.length) return;
    const reordered = [...entries];
    [reordered[index], reordered[targetIndex]] = [reordered[targetIndex], reordered[index]];
    const itemIds = reordered.map((entry) => entry.id);
    if (new Set(itemIds).size !== entries.length) {
      setError('Danh sách checklist có ID bị trùng. Vui lòng tải lại dữ liệu.');
      return;
    }
    mutationLock.current = true;
    setSavingId(-1);
    setError('');
    setNotice('');
    try {
      const updated = await adminChecklistApi.reorder(itemIds);
      setEntries(sortAdminChecklist(updated));
      setNotice('Đã cập nhật thứ tự checklist.');
    } catch (apiError) {
      setError(adminChecklistErrorMessage(apiError, 'Không cập nhật được thứ tự checklist. Thứ tự cũ được giữ nguyên.'));
    } finally {
      mutationLock.current = false;
      setSavingId(null);
    }
  };

  return (
    <Panel title="Checklist duyệt bài" description="Quản lý và sắp xếp các tiêu chí mà quản trị viên phải xác nhận khi duyệt bài." action={<Button icon="add" disabled={loading || savingId !== null} onClick={() => { setEditingError(''); setEditing('new'); }}>Thêm tiêu chí</Button>}>
      {notice ? <div className="mb-4"><Alert tone="success">{notice}</Alert></div> : null}
      {error ? <div className="mb-4 space-y-3"><Alert tone="error">{error}</Alert><Button size="sm" variant="outline" onClick={() => setReloadKey((key) => key + 1)}>Tải lại</Button></div> : null}
      {loading ? <EmptyState title="Đang tải checklist" text="Vui lòng chờ trong giây lát." /> : entries.length ? (
        <div className="table-wrap"><table className="data-table"><thead><tr><th>Thứ tự</th><th>Tiêu chí</th><th className="whitespace-nowrap">Trạng thái</th><th className="min-w-[430px] whitespace-nowrap">Thao tác</th></tr></thead><tbody>
          {entries.map((entry, index) => <tr key={entry.id}><td>{index + 1}</td><td className="font-semibold">{entry.question}</td><td className="whitespace-nowrap"><span className={`rounded-sm px-2 py-1 text-xs font-semibold ${entry.active ? 'bg-emerald-50 text-success' : 'bg-surface-low text-text-muted'}`}>{entry.active ? 'Đang hoạt động' : 'Không hoạt động'}</span></td><td><div className="flex flex-nowrap gap-1"><Button className="!px-2 whitespace-nowrap" size="sm" variant="ghost" title="Đưa mục này lên trên một vị trí" disabled={savingId !== null || index === 0} onClick={() => void move(index, -1)}>Chuyển lên</Button><Button className="!px-2 whitespace-nowrap" size="sm" variant="ghost" title="Đưa mục này xuống dưới một vị trí" disabled={savingId !== null || index === entries.length - 1} onClick={() => void move(index, 1)}>Chuyển xuống</Button><Button className="!px-2 whitespace-nowrap" size="sm" variant="outline" title="Chỉnh sửa câu tiêu chí" disabled={savingId !== null} onClick={() => { setEditingError(''); setEditing(entry); }}>Sửa nội dung</Button><Button className="!px-2 whitespace-nowrap" size="sm" variant="outline" title={entry.active ? 'Không hiển thị mục này khi duyệt bài (không xoá)' : 'Hiển thị lại mục này khi duyệt bài'} disabled={savingId !== null} onClick={() => void toggleActive(entry)}>{entry.active ? 'Tạm ẩn' : 'Kích hoạt'}</Button></div></td></tr>)}
        </tbody></table></div>
      ) : error ? null : <EmptyState title="Chưa có tiêu chí" text="Hiện chưa có tiêu chí duyệt bài nào." />}
      {editing ? <ChecklistEditModal entry={editing === 'new' ? null : editing} saving={savingId !== null} error={editingError} onClose={() => { if (savingId === null) { setEditing(null); setEditingError(''); } }} onSave={(question) => void save(question)} /> : null}
    </Panel>
  );
}

function ChecklistEditModal({ entry, saving, error, onClose, onSave }: { entry: AdminChecklistEntry | null; saving: boolean; error: string; onClose: () => void; onSave: (question: string) => void }) {
  const [question, setQuestion] = useState(entry?.question ?? '');
  const questionTooLong = question.length > ADMIN_CHECKLIST_QUESTION_MAX_LENGTH;
  return <Modal title={entry ? 'Sửa tiêu chí' : 'Thêm tiêu chí'} onClose={onClose}>
    <TextArea
      label="Nội dung tiêu chí"
      value={question}
      onChange={(event) => setQuestion(event.target.value)}
      disabled={saving}
      error={questionTooLong ? `Nội dung không được vượt quá ${ADMIN_CHECKLIST_QUESTION_MAX_LENGTH} ký tự.` : undefined}
    />
    <p className={`mt-1.5 text-right text-xs ${questionTooLong ? 'text-error' : 'text-text-muted'}`}>
      {question.length}/{ADMIN_CHECKLIST_QUESTION_MAX_LENGTH} ký tự
    </p>
    {error ? <div className="mt-4"><Alert tone="error">{error}</Alert></div> : null}
    <div className="mt-6 flex justify-end gap-2"><Button variant="ghost" disabled={saving} onClick={onClose}>Hủy</Button><Button disabled={saving || !question.trim() || questionTooLong} onClick={() => onSave(question.trim())}>{saving ? 'Đang lưu...' : 'Lưu'}</Button></div>
  </Modal>;
}

const SHOW_CATEGORY_ATTRIBUTES = false;

function AdminCategories() {
  const admin = useAppSelector(selectCurrentUser)!;
  return toBackendUserId(admin.id) !== null && admin.role === 'admin'
    ? <ApiAdminCategories />
    : <AdminStaticContent kind="categories" />;
}

function sortCategories(categories: AdminCategoryResponse[]) {
  return [...categories].sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id);
}

export function ApiAdminCategories() {
  const [categories, setCategories] = useState<AdminCategoryResponse[]>([]);
  const [editing, setEditing] = useState<AdminCategoryResponse | null | 'new'>(null);
  const [loading, setLoading] = useState(true);
  const [savingId, setSavingId] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  const mutationLock = useRef(false);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    adminCategoryApi.list()
      .then((response) => { if (active) setCategories(sortCategories(response)); })
      .catch((apiError: ApiError) => { if (active) setError(apiError.message || 'Không tải được danh mục quản trị.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [reloadKey]);

  const save = async (input: AdminCategoryRequest) => {
    if (mutationLock.current) return;
    mutationLock.current = true;
    setSavingId(editing === 'new' ? 0 : editing?.id ?? null);
    setError('');
    setNotice('');
    try {
      // Danh mục mới tự xuống cuối danh sách, Backend lo việc gán thứ tự.
      const saved = editing === 'new'
        ? await adminCategoryApi.create(input)
        : await adminCategoryApi.update(editing!.id, input);
      setCategories((current) => sortCategories(editing === 'new'
        ? [...current, saved]
        : current.map((entry) => entry.id === saved.id ? saved : entry)));
      setEditing(null);
      setNotice(editing === 'new' ? 'Đã thêm danh mục.' : 'Đã cập nhật danh mục.');
    } catch (apiError) {
      const response = apiError as ApiError;
      setError(response.message || 'Không lưu được danh mục.');
    } finally {
      mutationLock.current = false;
      setSavingId(null);
    }
  };

  const toggleActive = async (category: AdminCategoryResponse) => {
    if (mutationLock.current) return;
    mutationLock.current = true;
    setSavingId(category.id);
    setError('');
    try {
      const updated = await adminCategoryApi.setActive(category.id, !category.active);
      setCategories((current) => current.map((entry) => entry.id === updated.id ? updated : entry));
    } catch (apiError) {
      const response = apiError as ApiError;
      setError(response.message || 'Không cập nhật được trạng thái danh mục.');
    } finally {
      mutationLock.current = false;
      setSavingId(null);
    }
  };

  // Đổi chỗ hai danh mục kề nhau rồi gửi cả danh sách id theo thứ tự mới. Lỗi thì giữ nguyên thứ tự cũ.
  const move = async (index: number, offset: -1 | 1) => {
    const targetIndex = index + offset;
    if (mutationLock.current || targetIndex < 0 || targetIndex >= categories.length) return;
    const reordered = [...categories];
    [reordered[index], reordered[targetIndex]] = [reordered[targetIndex], reordered[index]];
    mutationLock.current = true;
    setSavingId(-1);
    setError('');
    setNotice('');
    try {
      const updated = await adminCategoryApi.reorder(reordered.map((entry) => entry.id));
      setCategories(sortCategories(updated));
      setNotice('Đã cập nhật thứ tự danh mục.');
    } catch (apiError) {
      const response = apiError as ApiError;
      setError(response.message || 'Không cập nhật được thứ tự danh mục. Thứ tự cũ được giữ nguyên.');
    } finally {
      mutationLock.current = false;
      setSavingId(null);
    }
  };

  const hasParentCategory = categories.some((category) => category.parentId != null);
  const busy = savingId !== null;

  return <Panel title="Danh mục" description="Thứ tự trong bảng cũng là thứ tự danh mục hiển thị cho người dùng." action={<Button icon="add" disabled={loading || busy} onClick={() => setEditing('new')}>Thêm danh mục</Button>}>
    {notice ? <div className="mb-4"><Alert tone="success">{notice}</Alert></div> : null}
    {error ? <div className="mb-4 space-y-3"><Alert tone="error">{error}</Alert><Button size="sm" variant="outline" onClick={() => setReloadKey((key) => key + 1)}>Tải lại</Button></div> : null}
    {loading ? <EmptyState title="Đang tải danh mục" text="Vui lòng chờ trong giây lát." /> : categories.length ? <div className="table-wrap"><table className="data-table"><thead><tr><th>Thứ tự</th><th>Tên</th>{hasParentCategory ? <th>Danh mục cha</th> : null}<th>Phân loại</th><th>Trạng thái</th><th className="whitespace-nowrap">Thao tác</th></tr></thead><tbody>{categories.map((category, index) => <tr key={category.id}><td>{index + 1}</td><td className="font-semibold">{category.name}</td>{hasParentCategory ? <td>{categories.find((entry) => entry.id === category.parentId)?.name ?? '—'}</td> : null}<td>{category.restricted ? 'Hạn chế' : 'Thông thường'}</td><td>{category.active ? 'Đang dùng' : 'Tạm ẩn'}</td><td><div className="flex flex-nowrap gap-1"><Button className="!px-2 whitespace-nowrap" size="sm" variant="ghost" title="Đưa danh mục này lên trên một vị trí" disabled={busy || index === 0} onClick={() => void move(index, -1)}>Chuyển lên</Button><Button className="!px-2 whitespace-nowrap" size="sm" variant="ghost" title="Đưa danh mục này xuống dưới một vị trí" disabled={busy || index === categories.length - 1} onClick={() => void move(index, 1)}>Chuyển xuống</Button><Button className="!px-2 whitespace-nowrap" size="sm" variant="outline" disabled={busy} onClick={() => setEditing(category)}>Sửa</Button><Button className="!px-2 whitespace-nowrap" size="sm" variant="outline" disabled={busy} onClick={() => void toggleActive(category)}>{category.active ? 'Tạm ẩn' : 'Kích hoạt'}</Button></div></td></tr>)}</tbody></table></div> : <EmptyState title="Chưa có danh mục" text="Hiện chưa có danh mục nào." />}
    {SHOW_CATEGORY_ATTRIBUTES && !loading && categories.length ? <ApiCategoryAttributes categories={categories} /> : null}
    {editing ? <ApiCategoryModal category={editing === 'new' ? null : editing} categories={categories} saving={savingId !== null} onClose={() => setEditing(null)} onSave={(input) => void save(input)} /> : null}
  </Panel>;
}

export function ApiCategoryModal({ category, categories, saving, onClose, onSave }: { category: AdminCategoryResponse | null; categories: AdminCategoryResponse[]; saving: boolean; onClose: () => void; onSave: (input: AdminCategoryRequest) => void }) {
  const [name, setName] = useState(category?.name ?? '');
  const [parentId, setParentId] = useState(category?.parentId ? String(category.parentId) : '');
  const [restricted, setRestricted] = useState(category?.restricted ?? false);
  const [boostable, setBoostable] = useState(category?.boostable ?? false);
  return <Modal title={category ? 'Sửa danh mục' : 'Thêm danh mục'} onClose={onClose}>
    <div className="space-y-4"><Field label="Tên danh mục" value={name} onChange={(event) => setName(event.target.value)} /><Select label="Danh mục cha" value={parentId} onChange={(event) => setParentId(event.target.value)}><option value="">Không có</option>{categories.filter((entry) => entry.id !== category?.id).map((entry) => <option key={entry.id} value={entry.id}>{entry.name}</option>)}</Select><label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={restricted} onChange={(event) => setRestricted(event.target.checked)} />Danh mục hạn chế</label><label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={boostable} onChange={(event) => setBoostable(event.target.checked)} />Cho phép đẩy tin</label>{category ? null : <p className="text-xs leading-5 text-text-muted">Danh mục mới tự xếp cuối danh sách. Dùng nút Chuyển lên hoặc Chuyển xuống để đổi vị trí.</p>}</div>
    <div className="mt-6 flex justify-end gap-2"><Button variant="ghost" disabled={saving} onClick={onClose}>Hủy</Button><Button disabled={saving || !name.trim()} onClick={() => onSave({ name: name.trim(), parentId: parentId ? Number(parentId) : null, restricted, boostable })}>{saving ? 'Đang lưu...' : 'Lưu'}</Button></div>
  </Modal>;
}

function ApiCategoryAttributes({ categories }: { categories: AdminCategoryResponse[] }) {
  const [categoryId, setCategoryId] = useState(categories[0]?.id ?? 0);
  const [attributes, setAttributes] = useState<AdminCategoryAttributeResponse[]>([]);
  const [editing, setEditing] = useState<AdminCategoryAttributeResponse | null | 'new'>(null);
  const [loading, setLoading] = useState(true);
  const [savingId, setSavingId] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    if (!categoryId || categories.some((category) => category.id === categoryId)) return;
    setCategoryId(categories[0]?.id ?? 0);
  }, [categories, categoryId]);

  useEffect(() => {
    if (!categoryId) {
      setAttributes([]);
      setLoading(false);
      return;
    }
    let active = true;
    setLoading(true);
    setError('');
    adminCategoryApi.listAttributes(categoryId)
      .then((response) => { if (active) setAttributes(response); })
      .catch((apiError: ApiError) => { if (active) setError(apiError.message || 'Không tải được thuộc tính danh mục.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [categoryId, reloadKey]);

  const save = async (input: AdminCategoryAttributeRequest) => {
    setSavingId(editing === 'new' ? 0 : editing?.id ?? null);
    setError('');
    setNotice('');
    try {
      const saved = editing === 'new'
        ? await adminCategoryApi.createAttribute(categoryId, input)
        : await adminCategoryApi.updateAttribute(categoryId, editing!.id, input);
      setAttributes((current) => editing === 'new'
        ? [...current, saved].sort((left, right) => left.sortOrder - right.sortOrder)
        : current.map((entry) => entry.id === saved.id ? saved : entry).sort((left, right) => left.sortOrder - right.sortOrder));
      setNotice(editing === 'new' ? 'Đã thêm thuộc tính.' : 'Đã cập nhật thuộc tính.');
      setEditing(null);
    } catch (apiError) {
      const response = apiError as ApiError;
      setError(response.message || 'Không lưu được thuộc tính danh mục.');
    } finally {
      setSavingId(null);
    }
  };

  const toggleActive = async (attribute: AdminCategoryAttributeResponse) => {
    setSavingId(attribute.id);
    setError('');
    setNotice('');
    try {
      const updated = await adminCategoryApi.setAttributeActive(categoryId, attribute.id, !attribute.active);
      setAttributes((current) => current.map((entry) => entry.id === updated.id ? updated : entry));
    } catch (apiError) {
      const response = apiError as ApiError;
      setError(response.message || 'Không cập nhật được trạng thái thuộc tính.');
    } finally {
      setSavingId(null);
    }
  };

  return <section className="mt-8 border-t border-border pt-6">
    <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <Select label="Thuộc tính của danh mục" value={categoryId} onChange={(event) => { setCategoryId(Number(event.target.value)); setEditing(null); setNotice(''); }}>
        {categories.map((category) => <option key={category.id} value={category.id}>{category.name}{category.active ? '' : ' (đang ẩn)'}</option>)}
      </Select>
      <Button icon="add" onClick={() => setEditing('new')}>Thêm thuộc tính</Button>
    </div>
    {notice ? <div className="mb-4"><Alert tone="success">{notice}</Alert></div> : null}
    {error ? <div className="mb-4 space-y-3"><Alert tone="error">{error}</Alert><Button size="sm" variant="outline" onClick={() => setReloadKey((key) => key + 1)}>Tải lại</Button></div> : null}
    {loading ? <EmptyState title="Đang tải thuộc tính" text="Vui lòng chờ trong giây lát." /> : attributes.length ? <div className="table-wrap"><table className="data-table"><thead><tr><th>Tên</th><th>Kiểu</th><th>Lựa chọn</th><th>Bắt buộc</th><th>Thứ tự</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>{attributes.map((attribute) => <tr key={attribute.id}><td className="font-semibold">{attribute.name}</td><td>{attribute.dataType}</td><td>{attribute.options.length ? attribute.options.join(', ') : '—'}</td><td>{attribute.required ? 'Có' : 'Không'}</td><td>{attribute.sortOrder}</td><td>{attribute.active ? 'Đang dùng' : 'Tạm ẩn'}</td><td className="space-x-2"><Button size="sm" variant="ghost" disabled={savingId === attribute.id} onClick={() => setEditing(attribute)}>Sửa</Button><Button size="sm" variant="outline" disabled={savingId === attribute.id} onClick={() => void toggleActive(attribute)}>{attribute.active ? 'Tạm ẩn' : 'Kích hoạt'}</Button></td></tr>)}</tbody></table></div> : <EmptyState title="Chưa có thuộc tính" text="Danh mục này chưa được cấu hình thuộc tính động." />}
    {editing ? <ApiCategoryAttributeModal attribute={editing === 'new' ? null : editing} saving={savingId !== null} onClose={() => setEditing(null)} onSave={(input) => void save(input)} /> : null}
  </section>;
}

function ApiCategoryAttributeModal({ attribute, saving, onClose, onSave }: { attribute: AdminCategoryAttributeResponse | null; saving: boolean; onClose: () => void; onSave: (input: AdminCategoryAttributeRequest) => void }) {
  const [name, setName] = useState(attribute?.name ?? '');
  const [dataType, setDataType] = useState<AttributeDataType>(attribute?.dataType ?? 'TEXT');
  const [options, setOptions] = useState(attribute?.options.join('\n') ?? '');
  const [required, setRequired] = useState(attribute?.required ?? false);
  const [sortOrder, setSortOrder] = useState(attribute?.sortOrder ?? 0);
  const parsedOptions = options.split(/[,\n]/).map((value) => value.trim()).filter(Boolean);
  const invalidSelect = dataType === 'SELECT' && parsedOptions.length === 0;
  return <Modal title={attribute ? 'Sửa thuộc tính' : 'Thêm thuộc tính'} onClose={onClose}>
    <div className="space-y-4">
      <Field label="Tên thuộc tính" value={name} onChange={(event) => setName(event.target.value)} disabled={saving} />
      <Select label="Kiểu dữ liệu" value={dataType} onChange={(event) => setDataType(event.target.value as AttributeDataType)} disabled={saving}><option value="TEXT">Văn bản</option><option value="NUMBER">Số</option><option value="SELECT">Danh sách lựa chọn</option><option value="BOOLEAN">Có / Không</option></Select>
      {dataType === 'SELECT' ? <TextArea label="Các lựa chọn (mỗi dòng hoặc phân cách bằng dấu phẩy)" value={options} onChange={(event) => setOptions(event.target.value)} disabled={saving} /> : null}
      <Field label="Thứ tự" type="number" min={0} value={sortOrder} onChange={(event) => setSortOrder(Number(event.target.value))} disabled={saving} />
      <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={required} onChange={(event) => setRequired(event.target.checked)} disabled={saving} />Bắt buộc nhập</label>
    </div>
    <div className="mt-6 flex justify-end gap-2"><Button variant="ghost" disabled={saving} onClick={onClose}>Hủy</Button><Button disabled={saving || !name.trim() || invalidSelect || !Number.isInteger(sortOrder) || sortOrder < 0} onClick={() => onSave({ name: name.trim(), dataType, options: dataType === 'SELECT' ? parsedOptions : [], required, sortOrder })}>{saving ? 'Đang lưu...' : 'Lưu'}</Button></div>
  </Modal>;
}

function AdminStaticContent({
  kind,
}: {
  kind: 'expired' | 'categories' | 'locked';
}) {
  const data = useAppSelector(selectData);
  const admin = useAppSelector(selectCurrentUser)!;
  const dispatch = useAppDispatch();
  const [categories, setCategories] = useState<CategoryRow[]>(() =>
    CATEGORIES.map((name) => ({
      id: name,
      name,
      code: name.toLowerCase().replace(/\s+/g, '-'),
      description: `Danh mục ${name}`,
      status: 'active' as const,
    })),
  );
  const [editingCategory, setEditingCategory] = useState<CategoryRow | null>(null);
  const [accountAction, setAccountAction] = useState<{ user: User; locked: boolean } | null>(null);
  const [selectedExpired, setSelectedExpired] = useState<Item | null>(null);
  const [toast, setToast] = useState('');
  const configs = {
    expired: ['Bài quá hạn', 'Theo dõi và xử lý các bài đã hết thời gian hiển thị.'],
    categories: ['Danh mục', 'Quản lý cấu trúc danh mục dùng chung toàn hệ thống.'],
    locked: ['Quản lý tài khoản', 'Theo dõi trạng thái, khóa hoặc mở khóa tài khoản người dùng.'],
  } as const;
  const [title, description] = configs[kind];
  const rows =
    kind === 'expired'
          ? data.items
              .filter((i) => i.status === 'expired')
              .map((i) => [
                i.title,
                i.district,
                i.expiresAt ? new Date(i.expiresAt).toLocaleDateString('vi-VN') : 'Không có',
              ])
          : [];
  if (kind === 'locked') {
    return (
      <Panel title={title} description={description}>
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Người dùng</th>
                <th>Email</th>
                <th>Trạng thái</th>
                <th>Lịch sử gần nhất</th>
                <th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {data.users.map((user) => {
                const lastLog = data.auditLogs.find((log) => log.targetType === 'user' && log.targetId === user.id && ['ACCOUNT_LOCKED', 'ACCOUNT_UNLOCKED'].includes(log.action));
                return (
                  <tr key={user.id}>
                    <td className="font-semibold">{user.name}</td>
                    <td>{user.email}</td>
                    <td><StatusBadge status={user.status} /></td>
                    <td className="text-xs text-text-muted">
                      {lastLog ? `${lastLog.detail} - ${new Date(lastLog.createdAt).toLocaleString('vi-VN')}` : 'Chưa có'}
                    </td>
                    <td>
                      {user.role !== 'admin' ? (
                        <Button
                          size="sm"
                          variant={user.status === 'locked' ? 'outline' : 'danger'}
                          onClick={() => setAccountAction({ user, locked: user.status !== 'locked' })}
                        >
                          {user.status === 'locked' ? 'Mở khóa' : 'Khóa'}
                        </Button>
                      ) : <span className="text-xs text-text-muted">Không áp dụng</span>}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        {accountAction ? (
          <AccountStatusModal
            user={accountAction.user}
            locked={accountAction.locked}
            history={data.auditLogs.filter((log) => log.targetType === 'user' && log.targetId === accountAction.user.id && ['ACCOUNT_LOCKED', 'ACCOUNT_UNLOCKED'].includes(log.action))}
            onClose={() => setAccountAction(null)}
            onConfirm={(reason) => {
              dispatch(actions.lockUser({ adminId: admin.id, userId: accountAction.user.id, locked: accountAction.locked, reason }));
              setAccountAction(null);
              setToast('Đã cập nhật trạng thái tài khoản.');
            }}
          />
        ) : null}
        {toast ? <Toast message={toast} onClose={() => setToast('')} /> : null}
      </Panel>
    );
  }
  if (kind === 'expired') {
    const expiredPosts = data.items.filter((item) => item.status === 'expired');
    return (
      <Panel title={title} description={description}>
        {expiredPosts.length ? (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Món đồ</th>
                  <th>Người đăng</th>
                  <th>Ngày đăng</th>
                  <th>Ngày hết hạn</th>
                  <th>Trạng thái hiện tại</th>
                  <th>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {expiredPosts.map((item) => {
                  const owner = data.users.find((user) => user.id === item.ownerId);
                  return (
                    <tr key={item.id}>
                      <td className="font-semibold">{item.title}</td>
                      <td>{owner?.name ?? item.ownerId}</td>
                      <td>{new Date(item.postedAt).toLocaleDateString('vi-VN')}</td>
                      <td>
                        {item.expiresAt ? new Date(item.expiresAt).toLocaleDateString('vi-VN') : 'Không có'}
                      </td>
                      <td><StatusBadge status={item.status} /></td>
                      <td><Button size="sm" variant="outline" onClick={() => setSelectedExpired(item)}>Kiểm tra</Button></td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : (
          <EmptyState title="Không có bài quá hạn" text="Bài hết hạn sẽ xuất hiện tại đây để Admin kiểm tra trước khi xử lý." />
        )}
        {selectedExpired ? (
          <ExpiredPostModal
            item={selectedExpired}
            owner={data.users.find((user) => user.id === selectedExpired.ownerId)}
            onClose={() => setSelectedExpired(null)}
            onMarkRemoved={() => {
              dispatch(actions.updateItemStatus({ itemId: selectedExpired.id, status: 'removed', adminId: admin.id }));
              setSelectedExpired(null);
              setToast('Đã đánh dấu bài quá hạn là đã gỡ.');
            }}
          />
        ) : null}
        {toast ? <Toast message={toast} onClose={() => setToast('')} /> : null}
      </Panel>
    );
  }
  if (kind === 'categories') {
    return (
      <Panel
        title={title}
        description={description}
      >
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Tên / Mã</th>
                <th>Thông tin</th>
                <th>Trạng thái</th>
                <th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {categories.map((category) => (
                <tr key={category.id}>
                  <td className="font-semibold">
                    {category.name}
                    <p className="mt-1 text-xs font-normal text-text-muted">{category.code}</p>
                  </td>
                  <td>{data.items.filter((x) => x.category === category.name).length} bài</td>
                  <td>{category.status === 'active' ? 'Đang dùng' : 'Tạm ẩn'}</td>
                  <td><Button size="sm" variant="ghost" onClick={() => setEditingCategory(category)}>Chỉnh sửa</Button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {editingCategory ? (
          <CategoryModal
            category={editingCategory}
            onClose={() => setEditingCategory(null)}
            onSave={(nextCategory) => {
              setCategories((current) => {
                return current.map((entry) => entry.id === editingCategory.id ? { ...nextCategory, id: editingCategory.id } : entry);
              });
              setEditingCategory(null);
              setToast('Đã cập nhật danh mục thành công.');
            }}
          />
        ) : null}
        {toast ? <Toast message={toast} onClose={() => setToast('')} /> : null}
      </Panel>
    );
  }
  return (
    <Panel title={title} description={description}>
      <SimpleRows rows={rows} />
    </Panel>
  );
}

function SimpleRows({ rows }: { rows: string[][] }) {
  return rows.length ? (
    <div className="table-wrap">
      <table className="data-table">
        <thead>
          <tr>
            <th>Tên / Mã</th>
            <th>Thông tin</th>
            <th>Trạng thái</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row, i) => (
            <tr key={row[0] + i}>
              <td className="font-semibold">{row[0]}</td>
              <td>{row[1]}</td>
              <td>{row[2]}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  ) : (
    <EmptyState title="Chưa có dữ liệu phù hợp" text="Các bản ghi mới sẽ xuất hiện tại đây." />
  );
}

type CategoryRow = {
  id: string;
  name: string;
  code: string;
  description: string;
  status: 'active' | 'hidden';
};

function CategoryModal({
  category,
  onClose,
  onSave,
}: {
  category: CategoryRow;
  onClose: () => void;
  onSave: (..._args: [CategoryRow]) => void;
}) {
  const [name, setName] = useState(category.name);
  const [code, setCode] = useState(category.code);
  const [description, setDescription] = useState(category.description);
  const [status, setStatus] = useState<CategoryRow['status']>(category.status);
  const protectedCategory = CATEGORIES.some((entry) => entry === category.id);
  return (
    <Modal title="Chỉnh sửa danh mục" onClose={onClose}>
      <Field label="Tên danh mục" value={name} disabled={protectedCategory} onChange={(event) => setName(event.target.value)} />
      <Field className="mt-4" label="Mã danh mục" value={code} disabled={protectedCategory} onChange={(event) => setCode(event.target.value)} />
      <TextArea className="mt-4" label="Mô tả" value={description} onChange={(event) => setDescription(event.target.value)} />
      <Select className="mt-4" label="Trạng thái" value={status} disabled={protectedCategory} onChange={(event) => setStatus(event.target.value as CategoryRow['status'])}>
        <option value="active">Đang dùng</option>
        <option value="hidden">Tạm ẩn</option>
      </Select>
      {protectedCategory ? <p className="mt-2 text-xs text-text-muted">Danh mục mặc định của ShareLoop được bảo vệ, chỉ cho phép cập nhật mô tả.</p> : null}
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="ghost" onClick={onClose}>Hủy</Button>
        <Button onClick={() => onSave({ ...category, name, code, description, status })}>Lưu thay đổi</Button>
      </div>
    </Modal>
  );
}

function ExpiredPostModal({
  item,
  owner,
  onClose,
  onMarkRemoved,
}: {
  item: Item;
  owner?: User;
  onClose: () => void;
  onMarkRemoved: () => void;
}) {
  return (
    <Modal title="Xử lý bài quá hạn" onClose={onClose}>
      <div className="grid gap-4 md:grid-cols-[160px_1fr]">
        <ItemImage src={item.images[0]} alt={item.title} className="aspect-square w-full rounded-md object-cover" />
        <div className="grid gap-3 text-sm sm:grid-cols-2">
          <Info label="Món đồ" value={item.title} />
          <Info label="Người đăng" value={owner?.name ?? item.ownerId} />
          <Info label="Ngày đăng" value={new Date(item.postedAt).toLocaleDateString('vi-VN')} />
          <Info
            label="Ngày hết hạn"
            value={item.expiresAt ? new Date(item.expiresAt).toLocaleDateString('vi-VN') : 'Không có'}
          />
          <Info label="Trạng thái hiện tại" value="Hết hạn" />
          <Info label="Khu vực" value={item.district} />
        </div>
      </div>
      <p className="mt-4 rounded-md bg-background p-3 text-sm leading-6 text-text-secondary">
        {item.description}
      </p>
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="ghost" onClick={onClose}>Đóng</Button>
        <Button variant="danger" onClick={onMarkRemoved}>Đánh dấu đã gỡ</Button>
      </div>
    </Modal>
  );
}

function Modal({ title, children, onClose }: { title: string; children: ReactNode; onClose: () => void }) {
  const titleId = useId();

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  return (
    <div className="fixed inset-0 z-40 grid place-items-end bg-black/30 sm:place-items-center sm:p-4">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className="max-h-[92dvh] w-full overflow-y-auto rounded-t-xl bg-white p-5 shadow-lg sm:max-w-xl sm:rounded-xl sm:p-6"
      >
        <div className="flex items-center justify-between gap-4">
          <h2 id={titleId} className="text-xl font-bold">{title}</h2>
          <button type="button" aria-label="Đóng" onClick={onClose} className="grid size-9 place-items-center rounded-md text-text-muted hover:bg-surface-low">
            <Icon name="close" className="size-5" />
          </button>
        </div>
        <div className="mt-5">{children}</div>
      </div>
    </div>
  );
}

function Toast({ message, onClose }: { message: string; onClose: () => void }) {
  return (
    <div className="fixed bottom-5 right-5 z-50 flex max-w-sm items-center gap-3 rounded-lg bg-text-primary px-4 py-3 text-sm font-semibold text-white shadow-lg">
      <span>{message}</span>
      <button onClick={onClose} className="rounded p-1 text-white/80 hover:bg-white/10 hover:text-white">
        <Icon name="close" className="size-4" />
      </button>
    </div>
  );
}

function ConfirmDialog({
  title,
  text,
  confirmLabel = 'Xóa',
  onCancel,
  onConfirm,
}: {
  title: string;
  text: string;
  confirmLabel?: string;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  return (
    <Modal title={title} onClose={onCancel}>
      <p className="text-sm leading-6 text-text-muted">{text}</p>
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="ghost" onClick={onCancel}>Hủy</Button>
        <Button variant={confirmLabel === 'Xóa' ? 'danger' : 'primary'} onClick={onConfirm}>{confirmLabel}</Button>
      </div>
    </Modal>
  );
}
function Info({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs font-medium text-text-muted">{label}</dt>
      <dd className="mt-1 text-sm font-semibold text-text-primary">{value}</dd>
    </div>
  );
}


