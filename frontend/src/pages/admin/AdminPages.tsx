import { useEffect, useState, type ReactNode } from 'react';
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
import { adminConfigApi, type AdminConfigResponse } from '../../services/adminConfigApi';
import { adminModerationApi, type ReviewQueueItem } from '../../services/adminModerationApi';
import { adminLexiconApi, type AdminLexiconEntry, type AdminLexiconRequest, type LexiconMatch } from '../../services/adminLexiconApi';
import { adminCategoryApi, type AdminCategoryRequest, type AdminCategoryResponse } from '../../services/adminCategoryApi';
import { adminChecklistApi, type AdminChecklistEntry } from '../../services/adminChecklistApi';
import type { ApiError } from '../../services/http';
import { toBackendUserId } from '../../services/requestApi';
import type { AppStateData, ComplaintStatus, ForbiddenKeyword, Item, KeywordAction, RankRule, ReputationPointRule, SupportedDistrict, Transaction, User } from '../../types/domain';

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

export function AdminModeration() {
  const data = useAppSelector(selectData);
  const admin = useAppSelector(selectCurrentUser)!;
  const dispatch = useAppDispatch();
  const backendAdminId = toBackendUserId(admin.id);
  const isApiAdmin = backendAdminId !== null && admin.role === 'admin';
  const [query, setQuery] = useState('');
  const [reviewing, setReviewing] = useState<Item | null>(null);
  const [approving, setApproving] = useState<Item | null>(null);
  const [rejecting, setRejecting] = useState<Item | null>(null);
  const [toast, setToast] = useState('');
  const [checklist, setChecklist] = useState<Record<string, Record<string, boolean>>>({});
  const [apiPending, setApiPending] = useState<ReviewQueueItem[]>([]);
  const [apiChecklist, setApiChecklist] = useState<string[]>([]);
  const [apiAnswers, setApiAnswers] = useState<Record<number, boolean[]>>({});
  const [apiRejecting, setApiRejecting] = useState<ReviewQueueItem | null>(null);
  const [apiRejectReason, setApiRejectReason] = useState('');
  const [apiRejectReasons, setApiRejectReasons] = useState<string[]>([]);
  const [apiActionId, setApiActionId] = useState<number | null>(null);
  const [apiLoading, setApiLoading] = useState(false);
  const [apiError, setApiError] = useState('');
  const [apiLegacyNotice, setApiLegacyNotice] = useState('');
  const [moderationReloadKey, setModerationReloadKey] = useState(0);

  useEffect(() => {
    if (!isApiAdmin) return;
    let active = true;
    setApiLoading(true);
    setApiError('');
    setApiLegacyNotice('');
    Promise.all([
      adminModerationApi.pendingReviews({ page: 0, size: 100 }),
      adminModerationApi.rejectReasons(),
    ])
      .then(([response, rejectReasons]) => {
        if (!active) return;
        setApiPending(response.items);
        setApiChecklist(response.checklist);
        setApiRejectReasons(rejectReasons);
        setApiAnswers(Object.fromEntries(response.items.map((item) => [item.id, response.checklist.map(() => false)])));
      })
      .catch(async (error: ApiError) => {
        if (!active) return;
        if (error.status === 404) {
          try {
            const legacyItems = await adminModerationApi.pendingItems({ page: 0, size: 100 });
            if (!active) return;
            setApiPending(legacyItems);
            setApiChecklist([]);
            setApiRejectReasons([]);
            setApiLegacyNotice('Backend đang dùng hàng chờ cũ; chức năng duyệt và từ chối chưa khả dụng.');
            return;
          } catch (fallbackError) {
            error = fallbackError as ApiError;
          }
        }
        setApiPending([]);
        setApiRejectReasons([]);
        setApiError(error.status === 403 ? 'Tài khoản hiện tại không có quyền xem hàng chờ kiểm duyệt.' : 'Không tải được danh sách bài chờ duyệt từ Backend.');
      })
      .finally(() => {
        if (active) setApiLoading(false);
      });
    return () => {
      active = false;
    };
  }, [isApiAdmin, moderationReloadKey]);

  const approveApiReview = async (item: ReviewQueueItem) => {
    const answers = apiAnswers[item.id] ?? [];
    if (!apiChecklist.length || answers.length !== apiChecklist.length || answers.some((answer) => !answer)) return;
    setApiActionId(item.id);
    setApiError('');
    try {
      await adminModerationApi.approve(item.id, { answers });
      setApiPending((current) => current.filter((entry) => entry.id !== item.id));
      setToast(`Đã duyệt bài #${item.id}.`);
    } catch (error) {
      const apiError = error as ApiError;
      setApiError(apiError.status === 403 ? 'Tài khoản hiện tại không có quyền duyệt bài.' : apiError.message || 'Không duyệt được bài đăng.');
    } finally {
      setApiActionId(null);
    }
  };

  const rejectApiReview = async () => {
    const reason = apiRejectReason.trim();
    if (!apiRejecting || !reason) return;
    setApiActionId(apiRejecting.id);
    setApiError('');
    try {
      await adminModerationApi.reject(apiRejecting.id, { reason });
      setApiPending((current) => current.filter((entry) => entry.id !== apiRejecting.id));
      setToast(`Đã từ chối bài #${apiRejecting.id}.`);
      setApiRejecting(null);
      setApiRejectReason('');
    } catch (error) {
      const apiError = error as ApiError;
      setApiError(apiError.status === 403 ? 'Tài khoản hiện tại không có quyền từ chối bài.' : apiError.message || 'Không từ chối được bài đăng.');
    } finally {
      setApiActionId(null);
    }
  };

  if (isApiAdmin) {
    const apiRows = apiPending.filter((item) => `${item.title} ${item.donorId} ${item.aiRisk ?? ''} ${item.moderationFlag ?? ''}`.toLowerCase().includes(query.trim().toLowerCase()));
    return (
      <Panel title="Duyệt bài" description="Danh sách bài chờ kiểm duyệt được tải trực tiếp từ Backend.">
        <FilterBar>
          <div className="flex-1">
            <SearchField placeholder="Tìm theo tiêu đề, người đăng hoặc mức rủi ro..." value={query} onChange={(event) => setQuery(event.target.value)} />
          </div>
          <span className="text-xs text-text-muted">{apiPending.length} bài đăng chờ</span>
        </FilterBar>
        {toast ? <div className="mb-4"><Alert tone="success">{toast}</Alert></div> : null}
        {apiLegacyNotice ? <div className="mb-4"><Alert tone="warning">{apiLegacyNotice}</Alert></div> : null}
        {apiLoading ? (
          <EmptyState title="Đang tải bài chờ duyệt" text="Vui lòng chờ trong giây lát." />
        ) : apiError ? (
          <div className="space-y-3 rounded-md bg-red-50 p-4 text-sm text-error">
            <p>{apiError}</p>
            <Button variant="outline" size="sm" onClick={() => setModerationReloadKey((key) => key + 1)}>
              Thử tải lại
            </Button>
          </div>
        ) : apiRows.length ? (
          <div className="space-y-3">
            {apiRows.map((item) => (
              <article key={item.id} className="grid gap-4 rounded-lg bg-white p-4 ring-1 ring-border/80 lg:grid-cols-[1fr_280px]">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2 text-xs text-text-muted">
                    <span>#{item.id}</span>
                    <span>Người đăng #{item.donorId}</span>
                    {item.aiRisk ? <span className="rounded-sm bg-amber-50 px-2 py-1 font-semibold text-warning">Rủi ro: {item.aiRisk}</span> : null}
                  </div>
                  <h2 className="mt-2 font-bold">{item.title}</h2>
                  <p className="mt-2 text-xs leading-5 text-text-muted">{item.flagReason || item.moderationFlag || 'Backend không cung cấp lý do gắn cờ.'}</p>
                  <p className="mt-2 text-xs text-text-muted">Gửi lúc {new Date(item.createdAt).toLocaleString('vi-VN')}</p>
                </div>
                <div className="space-y-3">
                  <p className="text-xs text-text-muted">Điểm tin cậy: {item.donorTrustStars ?? '--'}</p>
                  <div className="space-y-2 rounded-md bg-surface-low p-3">
                    {apiChecklist.map((label, index) => (
                      <label key={label} className="flex items-start gap-2 text-xs leading-5">
                        <input
                          type="checkbox"
                          checked={apiAnswers[item.id]?.[index] ?? false}
                          disabled={apiActionId === item.id}
                          onChange={(event) => setApiAnswers((current) => ({
                            ...current,
                            [item.id]: apiChecklist.map((_, answerIndex) => answerIndex === index ? event.target.checked : (current[item.id]?.[answerIndex] ?? false)),
                          }))}
                        />
                        <span>{label}</span>
                      </label>
                    ))}
                  </div>
                  {apiChecklist.length ? <div className="flex justify-end gap-2">
                    <Button size="sm" variant="danger" disabled={apiActionId === item.id} onClick={() => { setApiRejecting(item); setApiRejectReason(''); }}>
                      Từ chối
                    </Button>
                    <Button
                      size="sm"
                      disabled={apiActionId === item.id || !apiChecklist.length || !(apiAnswers[item.id] ?? []).every(Boolean)}
                      onClick={() => void approveApiReview(item)}
                    >
                      {apiActionId === item.id ? 'Đang xử lý...' : 'Duyệt bài'}
                    </Button>
                  </div> : null}
                </div>
              </article>
            ))}
          </div>
        ) : (
          <EmptyState icon="shield" title={query ? 'Không tìm thấy bài phù hợp' : 'Không có bài chờ duyệt'} text={query ? 'Hãy thử từ khóa khác.' : 'Backend chưa trả về bài đăng nào trong hàng chờ.'} />
        )}
        {apiRejecting ? (
          <Modal title={`Từ chối bài #${apiRejecting.id}`} onClose={() => { if (apiActionId === null) setApiRejecting(null); }}>
            <Select label="Lý do từ chối" value={apiRejectReason} onChange={(event) => setApiRejectReason(event.target.value)} disabled={apiActionId !== null}>
              <option value="">Chọn lý do từ Backend</option>
              {apiRejectReasons.map((reason) => <option key={reason} value={reason}>{reason}</option>)}
            </Select>
            <div className="mt-5 flex justify-end gap-2">
              <Button variant="ghost" onClick={() => setApiRejecting(null)} disabled={apiActionId !== null}>Hủy</Button>
              <Button variant="danger" onClick={() => void rejectApiReview()} disabled={!apiRejectReason.trim() || apiActionId !== null}>
                {apiActionId !== null ? 'Đang xử lý...' : 'Xác nhận từ chối'}
              </Button>
            </div>
          </Modal>
        ) : null}
      </Panel>
    );
  }

  const pendingPosts = data.items.filter((i) => i.status === 'pending' || i.status === 'PENDING_REVIEW' || i.status === 'VIOLATION');
  const rows = pendingPosts.filter((item) => {
    const owner = data.users.find((user) => user.id === item.ownerId);
    const searchable = `${item.title} ${owner?.name ?? ''} ${item.category}`.toLowerCase();
    return searchable.includes(query.trim().toLowerCase());
  });
  const checkedCount = (itemId: string) => moderationChecklist.filter((entry) => checklist[itemId]?.[entry.key]).length;
  const readyToApprove = (itemId: string) => checkedCount(itemId) === moderationChecklist.length;
  const approvePost = (item: Item) => {
    dispatch(
      actions.updateItemStatus({
        itemId: item.id,
        status: 'approved',
        adminId: admin.id,
      }),
    );
    setApproving(null);
    setReviewing(null);
    setToast('Đã duyệt bài thành công.');
    setChecklist((current) => {
      const next = { ...current };
      delete next[item.id];
      return next;
    });
  };
  return (
    <Panel title="Duyệt bài" description="Kiểm tra nội dung, hình ảnh và tính phù hợp trước khi hiển thị.">
      <FilterBar>
        <div className="flex-1">
          <SearchField placeholder="Tìm theo tiêu đề..." value={query} onChange={(e) => setQuery(e.target.value)} />
        </div>
        <span className="text-xs text-text-muted">{pendingPosts.length} bài đăng chờ</span>
      </FilterBar>
      <div className="space-y-3">
        {rows.map((item) => {
          const owner = data.users.find((u) => u.id === item.ownerId);
          return (
            <article key={item.id} className="grid gap-4 rounded-lg bg-white p-4 ring-1 ring-border/80 md:grid-cols-[96px_1fr_auto] md:items-center">
              <img src={item.images[0]} alt={item.title} className="aspect-square w-full rounded-md object-cover md:size-24" />
              <div className="min-w-0">
                <div className="flex gap-2">
                  <TypeBadge type={item.type} />
                  <span className="text-xs text-text-muted">{item.category}</span>
                </div>
                <h2 className="mt-2 truncate font-bold">{item.title}</h2>
                <p className="mt-1 line-clamp-2 text-xs leading-5 text-text-muted">{item.description}</p>
                <p className="mt-2 text-xs font-medium">
                  {owner?.name} · {item.district}
                </p>
              </div>
              <div className="flex flex-wrap justify-end gap-2">
                <Button size="sm" variant="outline" onClick={() => setReviewing(item)}>
                  Xem chi tiết
                </Button>
                <Button size="sm" variant="danger" onClick={() => setRejecting(item)}>
                  Từ chối
                </Button>
                <Button size="sm" onClick={() => setApproving(item)} disabled={!readyToApprove(item.id)}>
                  Duyệt bài
                </Button>
                <p className="basis-full text-right text-[11px] text-text-muted">
                  Checklist {checkedCount(item.id)}/{moderationChecklist.length}
                </p>
              </div>
            </article>
          );
        })}
      </div>
      {toast ? <Toast message={toast} onClose={() => setToast('')} /> : null}
      {reviewing ? (
        <ModerationDetailModal
          item={reviewing}
          owner={data.users.find((user) => user.id === reviewing.ownerId)}
          checked={checklist[reviewing.id] ?? {}}
          onToggle={(key, value) =>
            setChecklist((current) => ({
              ...current,
              [reviewing.id]: { ...(current[reviewing.id] ?? {}), [key]: value },
            }))
          }
          onClose={() => setReviewing(null)}
          onReject={() => {
            setReviewing(null);
            setRejecting(reviewing);
          }}
          onApprove={() => {
            setApproving(reviewing);
          }}
        />
      ) : null}
      {approving ? <ConfirmDialog title="Duyệt bài đăng?" text="Bạn xác nhận bài đăng này đáp ứng đầy đủ tiêu chí của SHARELOOP?" confirmLabel="Xác nhận duyệt" onCancel={() => setApproving(null)} onConfirm={() => approvePost(approving)} /> : null}
      {rejecting ? (
        <RejectPostModal
          item={rejecting}
          onClose={() => setRejecting(null)}
          onConfirm={(reason) => {
            dispatch(
              actions.updateItemStatus({
                itemId: rejecting.id,
                status: 'rejected',
                adminId: admin.id,
                rejectionReason: reason,
              }),
            );
            setRejecting(null);
            setToast('Đã từ chối bài đăng.');
          }}
        />
      ) : null}
    </Panel>
  );
}

const moderationChecklist = [
  { key: 'category', label: 'Không thuộc danh mục bị cấm' },
  { key: 'keyword', label: 'Không chứa từ khóa cấm' },
  { key: 'content', label: 'Nội dung phù hợp' },
  { key: 'image', label: 'Hình ảnh phù hợp' },
  { key: 'complete', label: 'Thông tin đầy đủ' },
] as const;

function ModerationDetailModal({
  item,
  owner,
  checked,
  onToggle,
  onClose,
  onReject,
  onApprove,
}: {
  item: Item;
  owner?: AppStateData['users'][number];
  checked: Record<string, boolean>;
  onToggle: (key: string, value: boolean) => void;
  onClose: () => void;
  onReject: () => void;
  onApprove: () => void;
}) {
  const isReady = moderationChecklist.every((entry) => checked[entry.key]);
  const progress = moderationChecklist.filter((entry) => checked[entry.key]).length;
  const automaticResult =
    item.status === 'VIOLATION'
      ? (item.moderationReason ?? 'Bài đăng cần Admin kiểm tra do không vượt qua kiểm tra tự động.')
      : 'Không phát hiện vi phạm tự động.';
  return (
    <Modal title="Chi tiết duyệt bài" onClose={onClose}>
      <div className="grid gap-4 md:grid-cols-[180px_1fr]">
        <img
          src={item.images[0]}
          alt={item.title}
          className="aspect-square w-full rounded-md object-cover"
        />
        <div className="grid gap-3 text-sm sm:grid-cols-2">
          <Info label="Tên món đồ" value={item.title} />
          <Info label="Người đăng" value={owner?.name ?? item.ownerId} />
          <Info label="Hình thức" value={item.type === 'gift' ? 'Cho tặng' : 'Trao đổi'} />
          <Info label="Danh mục" value={item.category} />
          <Info label="Khu vực" value={item.district} />
          <Info label="Tình trạng" value={conditionLabels[item.condition]} />
          <Info label="Ngày đăng" value={new Date(item.postedAt).toLocaleDateString('vi-VN')} />
          <Info
            label="Ngày hết hạn"
            value={item.expiresAt ? new Date(item.expiresAt).toLocaleDateString('vi-VN') : 'Không có'}
          />
          <Info label="Kiểm tra tự động" value={automaticResult} />
        </div>
      </div>
      <h3 className="mt-5 text-sm font-bold">Mô tả</h3>
      <div className="mt-4 rounded-md bg-background p-3 text-sm leading-6 text-text-secondary">
        {item.description}
      </div>
      <div className="mt-5 flex items-center justify-between gap-3">
        <h3 className="text-sm font-bold">Checklist kiểm duyệt</h3>
        <span className="text-xs font-semibold text-text-muted">Checklist {progress}/{moderationChecklist.length}</span>
      </div>
      <div className="mt-3 grid gap-2">
        {moderationChecklist.map((entry) => (
          <label key={entry.key} className="flex cursor-pointer items-center gap-2 rounded-md border border-border px-3 py-2 text-sm">
            <input
              type="checkbox"
              checked={Boolean(checked[entry.key])}
              onChange={(event) => onToggle(entry.key, event.target.checked)}
            />
            <span>{entry.label}</span>
          </label>
        ))}
      </div>
      <div className="mt-6 flex flex-wrap justify-end gap-2">
        <Button variant="ghost" onClick={onClose}>Đóng</Button>
        <Button variant="danger" onClick={onReject}>Từ chối</Button>
        <Button disabled={!isReady} onClick={onApprove}>Duyệt bài</Button>
      </div>
    </Modal>
  );
}

const rejectionOptions = [
  'Nội dung không phù hợp',
  'Hình ảnh không phù hợp',
  'Thuộc danh mục bị hạn chế',
  'Chứa thông tin không được phép',
  'Thông tin bài đăng chưa đầy đủ',
  'Lý do khác',
];

const conditionLabels: Record<Item['condition'], string> = {
  new: 'Mới',
  good: 'Còn tốt',
  used: 'Đã sử dụng',
};

function RejectPostModal({
  item,
  onClose,
  onConfirm,
}: {
  item: Item;
  onClose: () => void;
  onConfirm: (..._args: [string]) => void;
}) {
  const [selectedReason, setSelectedReason] = useState('');
  const [detail, setDetail] = useState('');
  const [error, setError] = useState('');
  const needsDetail = selectedReason === 'Lý do khác';
  const reason = needsDetail ? detail.trim() : selectedReason;
  return (
    <Modal title="Từ chối bài đăng" onClose={onClose}>
      <p className="text-sm leading-6 text-text-muted">
        Vui lòng cho biết lý do bài đăng không được duyệt.
      </p>
      <div className="mt-4 grid gap-2">
        {rejectionOptions.map((option) => (
          <label key={option} className="flex cursor-pointer items-center gap-2 rounded-md border border-border px-3 py-2 text-sm">
            <input
              type="radio"
              name={`reject-${item.id}`}
              checked={selectedReason === option}
              onChange={() => {
                setSelectedReason(option);
                setError('');
              }}
            />
            <span>{option}</span>
          </label>
        ))}
      </div>
      {needsDetail ? (
        <TextArea
          className="mt-4"
          label="Lý do chi tiết"
          placeholder="Nhập lý do từ chối để người đăng biết cần chỉnh sửa nội dung nào..."
          value={detail}
          error={error}
          onChange={(event) => {
            setDetail(event.target.value);
            setError('');
          }}
        />
      ) : error ? <p className="mt-3 text-xs text-error">{error}</p> : null}
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="ghost" onClick={onClose}>Hủy</Button>
        <Button
          variant="danger"
          onClick={() => {
            if (!reason.trim()) {
              setError('Vui lòng nhập lý do từ chối.');
              return;
            }
            onConfirm(reason);
          }}
        >
          Xác nhận từ chối
        </Button>
      </div>
    </Modal>
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
              <img
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
  const [configs, setConfigs] = useState<AdminConfigResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  const [editingConfig, setEditingConfig] = useState<AdminConfigResponse | null>(null);
  const [savingConfig, setSavingConfig] = useState(false);
  const [updateError, setUpdateError] = useState('');
  const [notice, setNotice] = useState('');

  useEffect(() => {
    if (!isApiAdmin) {
      setConfigs([]);
      setLoading(false);
      setError('');
      return;
    }

    let active = true;
    setLoading(true);
    setError('');
    adminConfigApi
      .list()
      .then((response) => {
        if (active) setConfigs(response);
      })
      .catch((apiError: ApiError) => {
        if (!active) return;
        setConfigs([]);
        setError(apiError.status === 403 ? 'Tài khoản hiện tại không đủ quyền xem cấu hình hệ thống.' : 'Không tải được cấu hình hệ thống từ Backend.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [isApiAdmin, reloadKey]);

  const updateConfig = async (value: string, reason: string) => {
    if (!editingConfig || backendAdminId === null || savingConfig) return;
    setSavingConfig(true);
    setUpdateError('');
    setNotice('');
    try {
      let updated: AdminConfigResponse;
      try {
        updated = await adminConfigApi.updateByBody(backendAdminId, {
          key: editingConfig.key,
          value,
          reason,
        });
      } catch (apiError) {
        const response = apiError as ApiError;
        if (response.status !== 404 && response.status !== 405) throw apiError;
        updated = await adminConfigApi.update(editingConfig.key, backendAdminId, { value, reason });
      }
      setConfigs((current) => current.map((entry) => (entry.key === updated.key ? updated : entry)));
      setEditingConfig(null);
      setNotice(`Đã cập nhật cấu hình ${updated.key}.`);
    } catch (apiError) {
      const errorResponse = apiError as ApiError;
      setUpdateError(errorResponse.status === 403 ? 'Tài khoản hiện tại không có quyền sửa cấu hình.' : errorResponse.message || 'Không cập nhật được cấu hình. Vui lòng thử lại.');
    } finally {
      setSavingConfig(false);
    }
  };

  if (isApiAdmin) {
    return (
      <Panel title="Cấu hình hệ thống" description="Xem và cập nhật các cấu hình được Backend hỗ trợ.">
        <div className="max-w-4xl rounded-lg bg-white p-6 ring-1 ring-border/80">
          <div className="rounded-md bg-primary-faint p-4 text-sm leading-6 text-text-secondary">Mỗi thay đổi cần nhập lý do và được gửi trực tiếp tới Backend. Cấu hình nhạy cảm cần xác nhận bổ sung trước khi lưu.</div>

          {notice ? <div className="mt-4 rounded-md bg-emerald-50 p-3 text-sm text-success">{notice}</div> : null}

          {loading ? (
            <EmptyState title="Đang tải cấu hình" text="Vui lòng chờ trong giây lát." />
          ) : error ? (
            <div className="mt-5 rounded-md bg-red-50 p-4 text-sm leading-6 text-error">
              <p>{error}</p>
              <Button className="mt-3" size="sm" variant="outline" onClick={() => setReloadKey((current) => current + 1)}>
                Thử lại
              </Button>
            </div>
          ) : configs.length ? (
            <div className="table-wrap mt-5">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Key</th>
                    <th>Value</th>
                    <th>Default Value</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {configs.map((config) => (
                    <tr key={config.key}>
                      <td className="font-mono text-xs">{config.key}</td>
                      <td className="font-mono text-xs">{config.value}</td>
                      <td className="font-mono text-xs">{config.defaultValue}</td>
                      <td className="text-right">
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => {
                            setUpdateError('');
                            setNotice('');
                            setEditingConfig(config);
                          }}
                        >
                          Chỉnh sửa
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <EmptyState title="Chưa có cấu hình" text="Backend không trả về cấu hình hệ thống nào." />
          )}
        </div>
        {editingConfig ? (
          <ConfigEditModal
            config={editingConfig}
            saving={savingConfig}
            error={updateError}
            onClose={() => {
              if (!savingConfig) setEditingConfig(null);
            }}
            onSave={(value, reason) => void updateConfig(value, reason)}
          />
        ) : null}
      </Panel>
    );
  }

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

function isSensitiveConfig(key: string) {
  return /(secret|password|token|credential|private|api[_-]?key|jwt|smtp|mail)/i.test(key);
}

function ConfigEditModal({
  config,
  saving,
  error,
  onClose,
  onSave,
}: {
  config: AdminConfigResponse;
  saving: boolean;
  error: string;
  onClose: () => void;
  onSave: (value: string, reason: string) => void;
}) {
  const [value, setValue] = useState(config.value);
  const [reason, setReason] = useState('');
  const [confirmedSensitive, setConfirmedSensitive] = useState(false);
  const sensitive = isSensitiveConfig(config.key);
  const canSave = value.trim().length > 0 && value !== config.value && reason.trim().length > 0 && (!sensitive || confirmedSensitive) && !saving;

  return (
    <Modal title="Chỉnh sửa cấu hình" onClose={onClose}>
      <div className="rounded-md bg-surface-low p-4 text-sm">
        <p className="text-xs text-text-muted">Key</p>
        <p className="mt-1 break-all font-mono font-semibold">{config.key}</p>
        <p className="mt-3 text-xs text-text-muted">Giá trị mặc định: {config.defaultValue}</p>
      </div>
      <div className="mt-4 space-y-4">
        <Field label="Giá trị mới" value={value} onChange={(event) => setValue(event.target.value)} disabled={saving} />
        <TextArea label="Lý do thay đổi" value={reason} onChange={(event) => setReason(event.target.value)} placeholder="Mô tả lý do cần cập nhật cấu hình..." disabled={saving} />
      </div>
      {sensitive ? (
        <label className="mt-4 flex items-start gap-2 rounded-md bg-amber-50 p-3 text-sm leading-5 text-warning">
          <input className="mt-1" type="checkbox" checked={confirmedSensitive} onChange={(event) => setConfirmedSensitive(event.target.checked)} disabled={saving} />
          <span>Tôi xác nhận đây là cấu hình nhạy cảm và đã kiểm tra kỹ giá trị mới.</span>
        </label>
      ) : null}
      {error ? <p className="mt-4 text-sm text-error">{error}</p> : null}
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="ghost" onClick={onClose} disabled={saving}>
          Hủy
        </Button>
        <Button onClick={() => onSave(value.trim(), reason.trim())} disabled={!canSave}>
          {saving ? 'Đang lưu...' : 'Xác nhận cập nhật'}
        </Button>
      </div>
    </Modal>
  );
}

export function AdminAuditLogs() {
  const data = useAppSelector(selectData);
  return (
    <Panel title="Audit Log" description="Dấu vết bất biến của các thao tác quản trị.">
      <div className="table-wrap">
        <table className="data-table">
          <thead>
            <tr>
              <th>Thời gian</th>
              <th>Hành động</th>
              <th>Đối tượng</th>
              <th>Chi tiết</th>
              <th>Admin</th>
            </tr>
          </thead>
          <tbody>
            {data.auditLogs.map((log) => (
              <tr key={log.id}>
                <td className="whitespace-nowrap">
                  {new Date(log.createdAt).toLocaleString('vi-VN')}
                </td>
                <td className="font-mono text-xs">{log.action}</td>
                <td>
                  {log.targetType} · {log.targetId}
                </td>
                <td>{log.detail}</td>
                <td>{data.users.find((u) => u.id === log.adminId)?.name ?? log.adminId}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Panel>
  );
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
  return <AdminStaticContent kind={kind} />;
}

function AdminKeywords() {
  const admin = useAppSelector(selectCurrentUser)!;
  return toBackendUserId(admin.id) !== null && admin.role === 'admin'
    ? <ApiAdminKeywords />
    : <MockAdminKeywords />;
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
      description="Quản lý từ điển kiểm duyệt trực tiếp từ Backend. Từ khóa không bị xóa; hãy tạm ngưng khi không còn sử dụng."
      action={<Button icon="add" onClick={() => setEditing('new')}>Thêm từ khóa</Button>}
    >
      {notice ? <div className="mb-4"><Alert tone="success">{notice}</Alert></div> : null}
      {error ? <div className="mb-4 space-y-3"><Alert tone="error">{error}</Alert><Button size="sm" variant="outline" onClick={() => setReloadKey((key) => key + 1)}>Tải lại</Button></div> : null}
      <section className="mb-5 rounded-lg bg-white p-4 ring-1 ring-border/80">
        <TextArea label="Kiểm tra nội dung" value={testText} onChange={(event) => setTestText(event.target.value)} placeholder="Nhập nội dung cần đối chiếu với từ điển..." />
        <Button className="mt-3" variant="outline" disabled={testing || !testText.trim()} onClick={() => void testLexicon()}>{testing ? 'Đang kiểm tra...' : 'Kiểm tra'}</Button>
        {testMatches.length ? <div className="mt-3 space-y-1 text-xs text-text-secondary">{testMatches.map((match, index) => <p key={`${match.keyword}-${index}`}><strong>{match.keyword}</strong> · {match.level} · {match.group} · {match.matchedAs}</p>)}</div> : null}
      </section>
      {loading ? <EmptyState title="Đang tải từ điển" text="Vui lòng chờ trong giây lát." /> : entries.length ? (
        <div className="table-wrap"><table className="data-table"><thead><tr><th>Từ khóa</th><th>Nhóm</th><th>Mức độ</th><th>Phạm vi</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>
          {entries.map((entry) => <tr key={entry.id}><td className="font-semibold">{entry.keyword}</td><td>{entry.group}</td><td>{entry.level}</td><td>{entry.scope}</td><td>{entry.active ? 'Đang dùng' : 'Tạm ngưng'}</td><td className="space-x-2"><Button size="sm" variant="ghost" disabled={savingId === entry.id} onClick={() => setEditing(entry)}>Sửa</Button><Button size="sm" variant="outline" disabled={savingId === entry.id} onClick={() => void toggleActive(entry)}>{entry.active ? 'Tạm ngưng' : 'Kích hoạt'}</Button></td></tr>)}
        </tbody></table></div>
      ) : <EmptyState title="Chưa có từ khóa" text="Backend chưa có mục từ điển kiểm duyệt nào." />}
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
  return <Modal title={entry ? 'Sửa từ khóa' : 'Thêm từ khóa'} onClose={onClose}>
    <div className="space-y-4"><Field label="Từ khóa" value={keyword} onChange={(event) => setKeyword(event.target.value)} /><Field label="Nhóm" value={group} onChange={(event) => setGroup(event.target.value)} /><Field label="Mức độ" value={level} onChange={(event) => setLevel(event.target.value)} /><Field label="Phạm vi" value={scope} onChange={(event) => setScope(event.target.value)} /><Field label="Chế độ khớp (không bắt buộc)" value={matchMode} onChange={(event) => setMatchMode(event.target.value)} /><Field label="Ngoại lệ, phân cách bằng dấu phẩy" value={exceptions} onChange={(event) => setExceptions(event.target.value)} /></div>
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

function AdminComplaints() {
  const data = useAppSelector(selectData);
  const admin = useAppSelector(selectCurrentUser)!;
  const dispatch = useAppDispatch();
  const [selectedId, setSelectedId] = useState('');
  const [note, setNote] = useState('');
  const [resolution, setResolution] = useState('');
  const selected = data.complaints.find((entry) => entry.id === selectedId);
  const statusLabel: Record<ComplaintStatus, string> = {
    received: 'Đã tiếp nhận',
    processing: 'Đang xử lý',
    resolved: 'Đã xử lý',
    rejected: 'Đã bác bỏ khiếu nại',
    violation_confirmed: 'Đã xác nhận vi phạm',
  };
  return (
    <Panel title="Khiếu nại" description="Hồ sơ giao dịch cần quản trị viên xem xét và xử lý.">
      <div className="table-wrap">
        <table className="data-table">
          <thead>
            <tr>
              <th>Mã</th>
              <th>Người gửi</th>
              <th>Giao dịch</th>
              <th>Lý do</th>
              <th>Ngày gửi</th>
              <th>Trạng thái</th>
              <th>Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {data.complaints.map((complaint) => (
              <tr key={complaint.id}>
                <td className="font-semibold">{complaint.id}</td>
                <td>{data.users.find((user) => user.id === complaint.reporterId)?.name}</td>
                <td>{complaint.transactionId}</td>
                <td>{complaint.reason}</td>
                <td>{new Date(complaint.createdAt).toLocaleDateString('vi-VN')}</td>
                <td>{statusLabel[complaint.status]}</td>
                <td>
                  <Button size="sm" variant="outline" onClick={() => {
                    setSelectedId(complaint.id);
                    setNote(complaint.adminNote ?? '');
                    setResolution(complaint.resolution ?? '');
                  }}>
                    Xem chi tiết
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {selected ? (
        <Modal title={`Khiếu nại ${selected.id}`} onClose={() => setSelectedId('')}>
          <div className="grid gap-3 text-sm">
            <Info label="Người gửi" value={data.users.find((user) => user.id === selected.reporterId)?.name ?? selected.reporterId} />
            <Info label="Người bị khiếu nại" value={data.users.find((user) => user.id === selected.reportedUserId)?.name ?? selected.reportedUserId} />
            <Info label="Giao dịch liên quan" value={selected.transactionId} />
            <Info label="Trạng thái" value={statusLabel[selected.status]} />
          </div>
          <div className="mt-4 rounded-md bg-background p-3 text-sm leading-6 text-text-secondary">
            <strong className="text-text-primary">{selected.reason}</strong>
            <p className="mt-1">{selected.content}</p>
          </div>
          {selected.evidence.length ? (
            <div className="mt-4 grid grid-cols-3 gap-2">
              {selected.evidence.map((source, index) => (
                source.startsWith('data:video') ? (
                  <video key={source.slice(-16) + index} src={source} className="aspect-square rounded-md object-cover" controls />
                ) : (
                  <img key={source.slice(-16) + index} src={source} alt="Bằng chứng" className="aspect-square rounded-md object-cover" />
                )
              ))}
            </div>
          ) : null}
          <TextArea className="mt-4" label="Ghi chú xử lý" value={note} onChange={(event) => setNote(event.target.value)} />
          <TextArea className="mt-4" label="Kết quả xử lý" value={resolution} onChange={(event) => setResolution(event.target.value)} />
          {selected.response ? (
            <div className="mt-4 rounded-md bg-amber-50 p-3 text-sm leading-6 text-amber-900">
              <strong>Phản hồi của người bị khiếu nại</strong>
              <p className="mt-1">{selected.response}</p>
            </div>
          ) : selected.responseDueAt ? (
            <div className="mt-4 rounded-md bg-background p-3 text-sm text-text-muted">
              Người bị khiếu nại có 3 ngày để phản hồi. Hạn phản hồi: {new Date(selected.responseDueAt).toLocaleString('vi-VN')}.
            </div>
          ) : null}
          <div className="mt-6 flex flex-wrap justify-end gap-2">
            <Button variant="ghost" onClick={() => setSelectedId('')}>Đóng</Button>
            {selected.status === 'received' ? (
              <Button
                onClick={() => dispatch(actions.updateComplaintStatus({ adminId: admin.id, complaintId: selected.id, status: 'processing', adminNote: note }))}
              >
                Chuyển đang xử lý
              </Button>
            ) : null}
            {selected.status === 'processing' ? (
              <>
              <Button variant="outline" onClick={() => {
                dispatch(actions.updateComplaintStatus({ adminId: admin.id, complaintId: selected.id, status: 'rejected', adminNote: note, resolution }));
                setSelectedId('');
              }}>
                Bác bỏ khiếu nại
              </Button>
              <Button
                onClick={() => {
                  dispatch(actions.updateComplaintStatus({ adminId: admin.id, complaintId: selected.id, status: 'violation_confirmed', adminNote: note, resolution }));
                  setSelectedId('');
                }}
              >
                Đánh dấu đã xử lý
              </Button>
              <Button
                variant="danger"
                onClick={() =>
                  dispatch(actions.adminAdjustReputationStars({
                    adminId: admin.id,
                    userId: selected.reportedUserId,
                    change: -1,
                    reason: resolution || `Xử lý khiếu nại ${selected.id}`,
                  }))
                }
              >
                Trừ 1 sao uy tín
              </Button>
              </>
            ) : null}
            {data.users.find((user) => user.id === selected.reportedUserId)?.reputationStars === 0 ? (
              <Button
                variant="outline"
                disabled={Boolean(data.users.find((user) => user.id === selected.reportedUserId)?.reputationRestoreUsed)}
                onClick={() =>
                  dispatch(actions.restoreOneReputationStar({
                    adminId: admin.id,
                    userId: selected.reportedUserId,
                    reason: resolution || `Cấp lại uy tín sau khi xem xét ${selected.id}`,
                  }))
                }
              >
                {data.users.find((user) => user.id === selected.reportedUserId)?.reputationRestoreUsed
                  ? 'Đã sử dụng quyền cấp lại uy tín'
                  : 'Cấp lại 1 sao'}
              </Button>
            ) : null}
          </div>
        </Modal>
      ) : null}
    </Panel>
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
      <Panel title="Checklist duyệt bài" description="Checklist mẫu dành cho tài khoản demo.">
        <div className="space-y-2">
          {moderationChecklist.map((entry, index) => (
            <div key={entry.key} className="rounded-md bg-white p-4 text-sm ring-1 ring-border/80">
              <span className="mr-3 text-text-muted">{index + 1}.</span>{entry.label}
            </div>
          ))}
        </div>
      </Panel>
    );
  }
  return <ApiAdminChecklist />;
}

function ApiAdminChecklist() {
  const [entries, setEntries] = useState<AdminChecklistEntry[]>([]);
  const [editing, setEditing] = useState<AdminChecklistEntry | null | 'new'>(null);
  const [loading, setLoading] = useState(true);
  const [savingId, setSavingId] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    adminChecklistApi.list()
      .then((response) => {
        if (active) setEntries([...response].sort((a, b) => a.sortOrder - b.sortOrder));
      })
      .catch((apiError: ApiError) => {
        if (active) setError(apiError.message || 'Không tải được checklist duyệt bài.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => { active = false; };
  }, [reloadKey]);

  const save = async (question: string) => {
    setSavingId(editing === 'new' ? 0 : editing?.id ?? null);
    setError('');
    try {
      const saved = editing === 'new'
        ? await adminChecklistApi.create({ question })
        : await adminChecklistApi.update(editing!.id, { question });
      setEntries((current) => (editing === 'new'
        ? [...current, saved]
        : current.map((entry) => entry.id === saved.id ? saved : entry)
      ).sort((a, b) => a.sortOrder - b.sortOrder));
      setNotice(editing === 'new' ? 'Đã thêm tiêu chí.' : 'Đã cập nhật tiêu chí.');
      setEditing(null);
    } catch (apiError) {
      setError((apiError as ApiError).message || 'Không lưu được tiêu chí.');
    } finally {
      setSavingId(null);
    }
  };

  const toggleActive = async (entry: AdminChecklistEntry) => {
    setSavingId(entry.id);
    setError('');
    try {
      const updated = await adminChecklistApi.setActive(entry.id, !entry.active);
      setEntries((current) => current.map((item) => item.id === updated.id ? updated : item));
      setNotice(updated.active ? 'Đã kích hoạt tiêu chí.' : 'Đã tạm ngưng tiêu chí.');
    } catch (apiError) {
      setError((apiError as ApiError).message || 'Không cập nhật được trạng thái tiêu chí.');
    } finally {
      setSavingId(null);
    }
  };

  const move = async (index: number, offset: -1 | 1) => {
    const targetIndex = index + offset;
    if (targetIndex < 0 || targetIndex >= entries.length) return;
    const reordered = [...entries];
    [reordered[index], reordered[targetIndex]] = [reordered[targetIndex], reordered[index]];
    setSavingId(-1);
    setError('');
    try {
      const updated = await adminChecklistApi.reorder(reordered.map((entry) => entry.id));
      setEntries([...updated].sort((a, b) => a.sortOrder - b.sortOrder));
      setNotice('Đã cập nhật thứ tự checklist.');
    } catch (apiError) {
      setError((apiError as ApiError).message || 'Không cập nhật được thứ tự checklist.');
    } finally {
      setSavingId(null);
    }
  };

  return (
    <Panel title="Checklist duyệt bài" description="Quản lý và sắp xếp các tiêu chí mà quản trị viên phải xác nhận khi duyệt bài." action={<Button icon="add" onClick={() => setEditing('new')}>Thêm tiêu chí</Button>}>
      {notice ? <div className="mb-4"><Alert tone="success">{notice}</Alert></div> : null}
      {error ? <div className="mb-4 space-y-3"><Alert tone="error">{error}</Alert><Button size="sm" variant="outline" onClick={() => setReloadKey((key) => key + 1)}>Tải lại</Button></div> : null}
      {loading ? <EmptyState title="Đang tải checklist" text="Vui lòng chờ trong giây lát." /> : entries.length ? (
        <div className="table-wrap"><table className="data-table"><thead><tr><th>Thứ tự</th><th>Mã</th><th>Tiêu chí</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>
          {entries.map((entry, index) => <tr key={entry.id}><td>{index + 1}</td><td className="font-mono text-xs">{entry.key}</td><td className="font-semibold">{entry.question}</td><td>{entry.active ? 'Đang dùng' : 'Tạm ngưng'}</td><td><div className="flex flex-wrap gap-2"><Button size="sm" variant="ghost" disabled={savingId !== null || index === 0} onClick={() => void move(index, -1)}>Lên</Button><Button size="sm" variant="ghost" disabled={savingId !== null || index === entries.length - 1} onClick={() => void move(index, 1)}>Xuống</Button><Button size="sm" variant="outline" disabled={savingId !== null} onClick={() => setEditing(entry)}>Sửa</Button><Button size="sm" variant="outline" disabled={savingId !== null} onClick={() => void toggleActive(entry)}>{entry.active ? 'Tạm ngưng' : 'Kích hoạt'}</Button></div></td></tr>)}
        </tbody></table></div>
      ) : <EmptyState title="Chưa có tiêu chí" text="Backend chưa có tiêu chí duyệt bài nào." />}
      {editing ? <ChecklistEditModal entry={editing === 'new' ? null : editing} saving={savingId !== null} onClose={() => setEditing(null)} onSave={(question) => void save(question)} /> : null}
    </Panel>
  );
}

function ChecklistEditModal({ entry, saving, onClose, onSave }: { entry: AdminChecklistEntry | null; saving: boolean; onClose: () => void; onSave: (question: string) => void }) {
  const [question, setQuestion] = useState(entry?.question ?? '');
  return <Modal title={entry ? 'Sửa tiêu chí' : 'Thêm tiêu chí'} onClose={onClose}>
    <TextArea label="Nội dung tiêu chí" value={question} onChange={(event) => setQuestion(event.target.value)} disabled={saving} />
    <div className="mt-6 flex justify-end gap-2"><Button variant="ghost" disabled={saving} onClick={onClose}>Hủy</Button><Button disabled={saving || !question.trim()} onClick={() => onSave(question.trim())}>{saving ? 'Đang lưu...' : 'Lưu'}</Button></div>
  </Modal>;
}

function AdminCategories() {
  const admin = useAppSelector(selectCurrentUser)!;
  return toBackendUserId(admin.id) !== null && admin.role === 'admin'
    ? <ApiAdminCategories />
    : <AdminStaticContent kind="categories" />;
}

function ApiAdminCategories() {
  const [categories, setCategories] = useState<AdminCategoryResponse[]>([]);
  const [editing, setEditing] = useState<AdminCategoryResponse | null | 'new'>(null);
  const [loading, setLoading] = useState(true);
  const [savingId, setSavingId] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    adminCategoryApi.list()
      .then((response) => { if (active) setCategories(response); })
      .catch((apiError: ApiError) => { if (active) setError(apiError.message || 'Không tải được danh mục quản trị.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [reloadKey]);

  const save = async (input: AdminCategoryRequest) => {
    setSavingId(editing === 'new' ? 0 : editing?.id ?? null);
    setError('');
    try {
      const saved = editing === 'new'
        ? await adminCategoryApi.create(input)
        : await adminCategoryApi.update(editing!.id, input);
      setCategories((current) => editing === 'new'
        ? [...current, saved]
        : current.map((entry) => entry.id === saved.id ? saved : entry));
      setEditing(null);
      setNotice(editing === 'new' ? 'Đã thêm danh mục.' : 'Đã cập nhật danh mục.');
    } catch (apiError) {
      const response = apiError as ApiError;
      setError(response.message || 'Không lưu được danh mục.');
    } finally {
      setSavingId(null);
    }
  };

  const toggleActive = async (category: AdminCategoryResponse) => {
    setSavingId(category.id);
    setError('');
    try {
      const updated = await adminCategoryApi.setActive(category.id, !category.active);
      setCategories((current) => current.map((entry) => entry.id === updated.id ? updated : entry));
    } catch (apiError) {
      const response = apiError as ApiError;
      setError(response.message || 'Không cập nhật được trạng thái danh mục.');
    } finally {
      setSavingId(null);
    }
  };

  return <Panel title="Danh mục" description="Quản lý danh mục thật từ Backend. Danh mục được ẩn hoặc kích hoạt, không xóa dữ liệu." action={<Button icon="add" onClick={() => setEditing('new')}>Thêm danh mục</Button>}>
    {notice ? <div className="mb-4"><Alert tone="success">{notice}</Alert></div> : null}
    {error ? <div className="mb-4 space-y-3"><Alert tone="error">{error}</Alert><Button size="sm" variant="outline" onClick={() => setReloadKey((key) => key + 1)}>Tải lại</Button></div> : null}
    {loading ? <EmptyState title="Đang tải danh mục" text="Vui lòng chờ trong giây lát." /> : categories.length ? <div className="table-wrap"><table className="data-table"><thead><tr><th>Tên</th><th>Danh mục cha</th><th>Thuộc tính</th><th>Thứ tự</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>{categories.map((category) => <tr key={category.id}><td className="font-semibold">{category.name}</td><td>{categories.find((entry) => entry.id === category.parentId)?.name ?? '—'}</td><td>{category.restricted ? 'Hạn chế' : 'Thông thường'} · {category.boostable ? 'Có boost' : 'Không boost'}</td><td>{category.sortOrder}</td><td>{category.active ? 'Đang dùng' : 'Tạm ẩn'}</td><td className="space-x-2"><Button size="sm" variant="ghost" disabled={savingId === category.id} onClick={() => setEditing(category)}>Sửa</Button><Button size="sm" variant="outline" disabled={savingId === category.id} onClick={() => void toggleActive(category)}>{category.active ? 'Tạm ẩn' : 'Kích hoạt'}</Button></td></tr>)}</tbody></table></div> : <EmptyState title="Chưa có danh mục" text="Backend chưa có danh mục nào." />}
    {editing ? <ApiCategoryModal category={editing === 'new' ? null : editing} categories={categories} saving={savingId !== null} onClose={() => setEditing(null)} onSave={(input) => void save(input)} /> : null}
  </Panel>;
}

function ApiCategoryModal({ category, categories, saving, onClose, onSave }: { category: AdminCategoryResponse | null; categories: AdminCategoryResponse[]; saving: boolean; onClose: () => void; onSave: (input: AdminCategoryRequest) => void }) {
  const [name, setName] = useState(category?.name ?? '');
  const [parentId, setParentId] = useState(category?.parentId ? String(category.parentId) : '');
  const [restricted, setRestricted] = useState(category?.restricted ?? false);
  const [boostable, setBoostable] = useState(category?.boostable ?? false);
  const [sortOrder, setSortOrder] = useState(category?.sortOrder ?? 0);
  return <Modal title={category ? 'Sửa danh mục' : 'Thêm danh mục'} onClose={onClose}>
    <div className="space-y-4"><Field label="Tên danh mục" value={name} onChange={(event) => setName(event.target.value)} /><Select label="Danh mục cha" value={parentId} onChange={(event) => setParentId(event.target.value)}><option value="">Không có</option>{categories.filter((entry) => entry.id !== category?.id).map((entry) => <option key={entry.id} value={entry.id}>{entry.name}</option>)}</Select><Field label="Thứ tự" type="number" min={0} value={sortOrder} onChange={(event) => setSortOrder(Number(event.target.value))} /><label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={restricted} onChange={(event) => setRestricted(event.target.checked)} />Danh mục hạn chế</label><label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={boostable} onChange={(event) => setBoostable(event.target.checked)} />Cho phép boost</label></div>
    <div className="mt-6 flex justify-end gap-2"><Button variant="ghost" disabled={saving} onClick={onClose}>Hủy</Button><Button disabled={saving || !name.trim() || !Number.isInteger(sortOrder) || sortOrder < 0} onClick={() => onSave({ name: name.trim(), parentId: parentId ? Number(parentId) : null, restricted, boostable, sortOrder })}>{saving ? 'Đang lưu...' : 'Lưu'}</Button></div>
  </Modal>;
}

function AdminStaticContent({
  kind,
}: {
  kind: 'expired' | 'categories' | 'locked' | 'alerts';
}) {
  const data = useAppSelector(selectData);
  const admin = useAppSelector(selectCurrentUser)!;
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
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
  const [selectedAlertId, setSelectedAlertId] = useState('');
  const [inspectedAlerts, setInspectedAlerts] = useState<string[]>([]);
  const [dismissedAlerts, setDismissedAlerts] = useState<string[]>([]);
  const [toast, setToast] = useState('');
  const configs = {
    expired: ['Bài quá hạn', 'Theo dõi và xử lý các bài đã hết thời gian hiển thị.'],
    categories: ['Danh mục', 'Quản lý cấu trúc danh mục dùng chung toàn hệ thống.'],
    locked: ['Quản lý tài khoản', 'Theo dõi trạng thái, khóa hoặc mở khóa tài khoản người dùng.'],
    alerts: ['Cảnh báo bất thường', 'Tín hiệu cần kiểm tra từ hoạt động giao dịch và Credit.'],
  } as const;
  const [title, description] = configs[kind];
  const alertRows = data.transactions
    .filter((t) => t.status === 'DISPUTED' )
    .filter((t) => !dismissedAlerts.includes(`alert_${t.id}`));
  const selectedTx = alertRows.find((tx) => `alert_${tx.id}` === selectedAlertId);
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
  if (kind === 'alerts') {
    return (
      <Panel title={title} description={description}>
        {alertRows.length ? (
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
                {alertRows.map((tx) => {
                  const alertId = `alert_${tx.id}`;
                  const isInspected = inspectedAlerts.includes(alertId);
                  return (
                    <tr key={alertId} className="cursor-pointer" onClick={() => setSelectedAlertId(alertId)}>
                      <td className="font-semibold">{alertId}</td>
                      <td>{tx.status === 'DISPUTED' ? 'Giao dịch có khiếu nại' : 'Nhiều bên đã giữ Credit'}</td>
                      <td>{isInspected ? 'Đã kiểm tra' : tx.status}</td>
                      <td>
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={(event) => {
                            event.stopPropagation();
                            setSelectedAlertId(alertId);
                          }}
                        >
                          Xem chi tiết
                        </Button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : (
          <EmptyState title="Chưa có dữ liệu phù hợp" text="Các bản ghi mới sẽ xuất hiện tại đây." />
        )}
        {selectedTx ? (
          <AlertDetailModal
            transaction={selectedTx}
            data={data}
            inspected={inspectedAlerts.includes(`alert_${selectedTx.id}`)}
            onClose={() => setSelectedAlertId('')}
            onInspect={() => {
              setInspectedAlerts((current) => [...new Set([...current, `alert_${selectedTx.id}`])]);
              setToast('Đã đánh dấu cảnh báo đã kiểm tra.');
            }}
            onDismiss={() => {
              setDismissedAlerts((current) => [...new Set([...current, `alert_${selectedTx.id}`])]);
              setSelectedAlertId('');
              setToast('Đã bỏ qua cảnh báo.');
            }}
            onOpenTransaction={() => navigate(`/admin/transactions/${selectedTx.id}`)}
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
        <img src={item.images[0]} alt={item.title} className="aspect-square w-full rounded-md object-cover" />
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

function AlertDetailModal({
  transaction,
  data,
  inspected,
  onClose,
  onInspect,
  onOpenTransaction,
  onDismiss,
}: {
  transaction: Transaction;
  data: AppStateData;
  inspected: boolean;
  onClose: () => void;
  onInspect: () => void;
  onOpenTransaction: () => void;
  onDismiss: () => void;
}) {
  const item = data.items.find((entry) => entry.id === transaction.itemId);
  const owner = data.users.find((entry) => entry.id === transaction.ownerId);
  const requester = data.users.find((entry) => entry.id === transaction.requesterId);
  const alertId = `alert_${transaction.id}`;
  const alertType = transaction.status === 'DISPUTED' ? 'Giao dịch có khiếu nại' : 'Nhiều bên đã giữ Credit';
  const relatedLogs = data.auditLogs
    .filter((log) => log.targetId === transaction.id || log.targetId === transaction.itemId)
    .slice(0, 5);
  return (
    <Modal title="Chi tiết cảnh báo bất thường" onClose={onClose}>
      <div className="grid gap-3 text-sm sm:grid-cols-2">
        <Info label="Mã cảnh báo" value={alertId} />
        <Info label="Mã giao dịch" value={transaction.id} />
        <Info label="Người liên quan" value={[owner?.name, requester?.name].filter(Boolean).join(' ↔ ')} />
        <Info label="Loại cảnh báo" value={alertType} />
        <Info label="Thời gian phát hiện" value={new Date(transaction.createdAt).toLocaleString('vi-VN')} />
        <Info label="Trạng thái giao dịch" value={transaction.status} />
        <Info label="Liên hệ" value={['WAITING_HANDOVER', 'SENDER_CONFIRMED', 'RECEIVER_CONFIRMED', 'COMPLETED'].includes(transaction.status) ? 'Đã mở khóa' : 'Chưa mở khóa'} />
        <Info label="Trạng thái kiểm tra" value={inspected ? 'Đã kiểm tra' : 'Chưa kiểm tra'} />
      </div>
      <div className="mt-4 rounded-md bg-background p-3 text-sm leading-6 text-text-secondary">
        <p><strong className="text-text-primary">Nội dung cảnh báo:</strong> {item?.title ?? transaction.itemId}</p>
        <p className="mt-1"><strong className="text-text-primary">Lý do hệ thống đánh dấu:</strong> {alertType}</p>
      </div>
      <div className="mt-4">
        <h3 className="text-sm font-bold">Lịch sử hoạt động liên quan</h3>
        <div className="mt-2 space-y-2">
          {relatedLogs.length ? relatedLogs.map((log) => (
            <div key={log.id} className="rounded-md border border-border px-3 py-2 text-xs text-text-muted">
              <p className="font-semibold text-text-primary">{log.detail}</p>
              <p className="mt-0.5">{new Date(log.createdAt).toLocaleString('vi-VN')}</p>
            </div>
          )) : (
            <p className="text-sm text-text-muted">Chưa có lịch sử liên quan trong audit log.</p>
          )}
        </div>
      </div>
      <div className="mt-6 flex flex-wrap justify-end gap-2">
        <Button variant="ghost" onClick={onClose}>Đóng</Button>
        <Button variant="outline" onClick={onInspect}>Đánh dấu đã kiểm tra</Button>
        <Button variant="secondary" onClick={onOpenTransaction}>Xem giao dịch liên quan</Button>
        <Button variant="danger" onClick={onDismiss}>Bỏ qua cảnh báo</Button>
      </div>
    </Modal>
  );
}

function Modal({ title, children, onClose }: { title: string; children: ReactNode; onClose: () => void }) {
  return (
    <div className="fixed inset-0 z-40 grid place-items-end bg-black/30 sm:place-items-center sm:p-4">
      <div className="max-h-[92dvh] w-full overflow-y-auto rounded-t-xl bg-white p-5 shadow-lg sm:max-w-xl sm:rounded-xl sm:p-6">
        <div className="flex items-center justify-between gap-4">
          <h2 className="text-xl font-bold">{title}</h2>
          <button onClick={onClose} className="grid size-9 place-items-center rounded-md text-text-muted hover:bg-surface-low">
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


