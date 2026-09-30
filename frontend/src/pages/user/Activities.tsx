import { useEffect, useMemo, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '../../app/hooks';
import { actions, selectCurrentUser, selectData } from '../../app/store';
import {
  Alert,
  Avatar,
  Button,
  ConditionBadge,
  EmptyState,
  Field,
  PageHeader,
  StatusBadge,
  TextArea,
  TypeBadge,
} from '../../components/ui';
import { RECEIVE_ITEM_FEE } from '../../utils/credit';

const requestStatusLabel = {
  PENDING: 'Chờ phản hồi',
  ACCEPTED: 'Đã được chọn',
  NOT_SELECTED: 'Không được chọn',
  CANCELLED: 'Đã hủy',
};

export function Activities() {
  const location = useLocation();
  const data = useAppSelector(selectData);
  const user = useAppSelector(selectCurrentUser)!;
  const dispatch = useAppDispatch();
  const [tab, setTab] = useState<'items' | 'transactions'>('items');
  const [editingId, setEditingId] = useState('');
  const [removeConfirmId, setRemoveConfirmId] = useState('');
  const [requestsItemId, setRequestsItemId] = useState('');
  const [acceptConfirmId, setAcceptConfirmId] = useState('');
  const [acceptError, setAcceptError] = useState('');
  const [editNotice, setEditNotice] = useState('');
  const [draftTitle, setDraftTitle] = useState('');
  const [draftDescription, setDraftDescription] = useState('');

  useEffect(() => {
    dispatch(actions.refreshExpiredItems());
  }, [dispatch]);

  const myItems = useMemo(() => data.items.filter((i) => i.ownerId === user.id), [data.items, user.id]);
  const txs = data.transactions.filter((t) => t.ownerId === user.id || t.requesterId === user.id);
  const acceptRequest = data.itemRequests.find((request) => request.id === acceptConfirmId);
  const acceptItem = data.items.find((item) => item.id === acceptRequest?.itemId);
  const acceptRequester = data.users.find((entry) => entry.id === acceptRequest?.requesterId);
  const acceptOfferedItem = data.items.find((item) => item.id === acceptRequest?.offeredItemId);

  return (
    <div className="page-shell">
      <PageHeader
        title="Hoạt động"
        description="Quản lý bài đăng, yêu cầu và tiến trình giao dịch của bạn."
        action={
          <Link to="/post">
            <Button icon="add">Đăng món mới</Button>
          </Link>
        }
      />
      {(location.state as { notice?: string } | null)?.notice ? (
        <div className="mb-5">
          <Alert tone="success">{(location.state as { notice: string }).notice}</Alert>
        </div>
      ) : null}
      {editNotice ? (
        <div className="mb-5">
          <Alert tone="warning">{editNotice}</Alert>
        </div>
      ) : null}
      <div className="mb-6 flex gap-1 border-b border-border">
        <button
          onClick={() => setTab('items')}
          className={`border-b-2 px-4 py-3 text-sm font-semibold ${tab === 'items' ? 'border-primary text-primary' : 'border-transparent text-text-muted'}`}
        >
          Món đã đăng <span className="ml-1 text-xs">({myItems.length})</span>
        </button>
        <button
          onClick={() => setTab('transactions')}
          className={`border-b-2 px-4 py-3 text-sm font-semibold ${tab === 'transactions' ? 'border-primary text-primary' : 'border-transparent text-text-muted'}`}
        >
          Giao dịch <span className="ml-1 text-xs">({txs.length})</span>
        </button>
      </div>

      {tab === 'items' ? (
        <section>
          {myItems.length ? (
            <div className="space-y-3">
              {myItems.map((item) => {
                const approved = item.status === 'approved' || item.status === 'APPROVED';
                const canEdit = !approved || (item.postApprovalEditCount ?? 0) < 1;
                const itemRequests = data.itemRequests.filter((request) => request.itemId === item.id);
                const hasAcceptedRequest = itemRequests.some((request) => request.status === 'ACCEPTED');
                return (
                  <div key={item.id}>
                    <article className="grid gap-4 rounded-lg bg-white p-3 ring-1 ring-border/80 sm:grid-cols-[120px_1fr_auto] sm:items-center">
                      <img
                        src={item.images[0]}
                        alt={item.title}
                        className="aspect-[4/3] w-full rounded-md object-cover sm:w-[120px]"
                      />
                      <div className="min-w-0">
                        <div className="flex flex-wrap items-center gap-2">
                          <TypeBadge type={item.type} />
                          <StatusBadge status={item.status} />
                        </div>
                        <Link
                          to={`/items/${item.id}`}
                          className="mt-2 block truncate font-bold hover:text-primary"
                        >
                          {item.title}
                        </Link>
                        <p className="mt-1 text-xs text-text-muted">
                          Hết hạn: {new Date(item.expiresAt).toLocaleDateString('vi-VN')} · {item.district}
                        </p>
                        {(item.status === 'rejected' || item.status === 'REJECTED' || item.status === 'VIOLATION') && (item.rejectionReason || item.moderationReason) ? (
                          <p className="mt-2 rounded-md bg-red-50 px-3 py-2 text-xs leading-5 text-error">
                            Lý do kiểm duyệt: {item.rejectionReason ?? item.moderationReason}
                          </p>
                        ) : null}
                      </div>
                      <div className="flex flex-wrap gap-2 sm:justify-end">
                        <Link to={`/items/${item.id}`}>
                          <Button size="sm" variant="outline" icon="eye">
                            Xem chi tiết
                          </Button>
                        </Link>
                        <Button
                          size="sm"
                          variant="outline"
                          icon="messages"
                          onClick={() => setRequestsItemId(requestsItemId === item.id ? '' : item.id)}
                        >
                          Yêu cầu ({itemRequests.length})
                        </Button>
                        <Button
                          size="sm"
                          variant="ghost"
                          icon="edit"
                          onClick={() => {
                            if (!canEdit) {
                              setEditNotice('Bạn đã sử dụng lượt sửa miễn phí sau khi bài được duyệt.');
                              return;
                            }
                            setEditNotice('');
                            setEditingId(item.id);
                            setDraftTitle(item.title);
                            setDraftDescription(item.description);
                          }}
                        >
                          Sửa
                        </Button>
                        <Button
                          size="sm"
                          variant="danger"
                          icon="trash"
                          disabled={item.status === 'removed'}
                          onClick={() => setRemoveConfirmId(item.id)}
                        >
                          Gỡ
                        </Button>
                        {item.status === 'expired' ? (
                          <Button
                            size="sm"
                            variant="outline"
                            icon="renew"
                            onClick={() =>
                              dispatch(actions.renewItem({ itemId: item.id, ownerId: user.id }))
                            }
                          >
                            Gia hạn
                          </Button>
                        ) : null}
                      </div>
                    </article>
                    {requestsItemId === item.id ? (
                      <RequestPanel
                        itemStatus={item.status}
                        hasAcceptedRequest={hasAcceptedRequest}
                        itemRequests={itemRequests}
                        data={data}
                        onAccept={(requestId) => {
                          setAcceptError('');
                          setAcceptConfirmId(requestId);
                        }}
                      />
                    ) : null}
                  </div>
                );
              })}
            </div>
          ) : (
            <EmptyState
              title="Bạn chưa đăng món nào"
              text="Đăng món đầu tiên để bắt đầu chia sẻ với cộng đồng."
              action={
                <Link to="/post">
                  <Button>Đăng món đồ</Button>
                </Link>
              }
            />
          )}
        </section>
      ) : (
        <section>
          {txs.length ? (
            <div className="space-y-3">
              {txs.map((tx) => {
                const item = data.items.find((i) => i.id === tx.itemId)!;
                const other = data.users.find(
                  (u) => u.id === (tx.ownerId === user.id ? tx.requesterId : tx.ownerId),
                );
                const conversation = data.conversations.find(
                  (entry) => entry.transactionId === tx.id,
                );
                return (
                  <article
                    key={tx.id}
                    className="rounded-lg bg-white p-4 ring-1 ring-border/80 sm:p-5"
                  >
                    <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                      <div className="flex min-w-0 items-center gap-3">
                        <img
                          src={item.images[0]}
                          alt=""
                          className="size-14 shrink-0 rounded-md object-cover"
                        />
                        <div className="min-w-0">
                          <Link
                            to="/messages"
                            className="block truncate font-bold hover:text-primary"
                          >
                            {item.title}
                          </Link>
                          <p className="mt-1 text-xs text-text-muted">
                            Với {other?.name} · {tx.id}
                          </p>
                        </div>
                      </div>
                      <StatusBadge status={tx.status} />
                    </div>
                    <div className="mt-4 flex flex-wrap items-center justify-between gap-3 border-t border-border/70 pt-4">
                      <p className="text-xs text-text-muted">
                        Giao dịch miễn phí. Credit không bị trừ trong quá trình giao nhận.
                      </p>
                      {conversation ? (
                        <Link to="/messages">
                          <Button size="sm" variant="outline" icon="messages">
                            Đi tới cuộc trò chuyện
                          </Button>
                        </Link>
                      ) : (
                        <Button size="sm" variant="outline" icon="messages" disabled>
                          Chưa mở chat
                        </Button>
                      )}
                    </div>
                  </article>
                );
              })}
            </div>
          ) : (
            <EmptyState
              title="Chưa có giao dịch"
              text="Giao dịch chỉ được tạo sau khi chủ bài chọn một yêu cầu."
            />
          )}
        </section>
      )}

      {editingId ? (
        <div className="fixed inset-0 z-40 grid place-items-end bg-black/30 sm:place-items-center sm:p-4">
          <div className="w-full rounded-t-xl bg-white p-5 shadow-lg sm:max-w-lg sm:rounded-xl sm:p-6">
            <h2 className="text-xl font-bold">Chỉnh sửa bài đăng</h2>
            <p className="mt-1 text-sm text-text-muted">Bài sẽ chờ duyệt lại sau khi lưu.</p>
            <div className="mt-5 space-y-4">
              <Field
                label="Tên món đồ"
                value={draftTitle}
                onChange={(event) => setDraftTitle(event.target.value)}
              />
              <TextArea
                label="Mô tả"
                value={draftDescription}
                onChange={(event) => setDraftDescription(event.target.value)}
              />
            </div>
            <div className="mt-6 flex justify-end gap-2">
              <Button variant="ghost" onClick={() => setEditingId('')}>
                Hủy
              </Button>
              <Button
                onClick={() => {
                  const item = data.items.find((entry) => entry.id === editingId);
                  if (!item) return;
                  const approved = item.status === 'approved' || item.status === 'APPROVED';
                  if (approved && (item.postApprovalEditCount ?? 0) >= 1) {
                    setEditNotice('Bạn đã sử dụng lượt sửa miễn phí sau khi bài được duyệt.');
                    setEditingId('');
                    return;
                  }
                  dispatch(
                    actions.updateItem({
                      itemId: item.id,
                      ownerId: user.id,
                      changes: {
                        title: draftTitle,
                        description: draftDescription,
                        category: item.category,
                        condition: item.condition,
                        district: item.district,
                        tradeFor: item.tradeFor,
                      },
                    }),
                  );
                  setEditingId('');
                }}
              >
                Lưu thay đổi
              </Button>
            </div>
          </div>
        </div>
      ) : null}

      {removeConfirmId ? (
        <div className="fixed inset-0 z-40 grid place-items-end bg-black/30 sm:place-items-center sm:p-4">
          <div
            role="dialog"
            aria-modal="true"
            className="w-full rounded-t-xl bg-white p-5 shadow-lg sm:max-w-lg sm:rounded-xl sm:p-6"
          >
            <h2 className="text-xl font-bold">Gỡ bài đăng?</h2>
            <p className="mt-2 text-sm leading-6 text-text-muted">
              Bài đăng sẽ không còn hiển thị với người dùng khác.
            </p>
            <div className="mt-6 flex justify-end gap-2">
              <Button variant="ghost" onClick={() => setRemoveConfirmId('')}>
                Hủy
              </Button>
              <Button
                variant="danger"
                onClick={() => {
                  dispatch(actions.removeItem({ itemId: removeConfirmId, ownerId: user.id }));
                  setRemoveConfirmId('');
                }}
              >
                Xác nhận gỡ
              </Button>
            </div>
          </div>
        </div>
      ) : null}

      {acceptRequest && acceptItem && acceptRequester ? (
        <div className="fixed inset-0 z-40 grid place-items-end bg-black/30 sm:place-items-center sm:p-4">
          <div
            role="dialog"
            aria-modal="true"
            className="w-full rounded-t-xl bg-white p-5 shadow-lg sm:max-w-lg sm:rounded-xl sm:p-6"
          >
            <h2 className="text-xl font-bold">Xác nhận chọn người</h2>
            <p className="mt-2 text-sm leading-6 text-text-muted">
              {acceptRequest.type === 'gift'
                ? `Bạn muốn chọn ${acceptRequester.name} để nhận ${acceptItem.title}?`
                : `Bạn muốn trao đổi ${acceptItem.title} với ${acceptOfferedItem?.title ?? 'món được đề nghị'}?`}
            </p>
            {acceptError ? (
              <div className="mt-3 rounded-md bg-amber-50 p-3 text-xs leading-5 text-warning">
                {acceptError}
              </div>
            ) : null}
            <div className="mt-6 flex justify-end gap-2">
              <Button
                variant="ghost"
                onClick={() => {
                  setAcceptError('');
                  setAcceptConfirmId('');
                }}
              >
                Hủy
              </Button>
              <Button
                onClick={() => {
                  if (acceptRequest.type === 'gift' && acceptRequester.availableCredit < RECEIVE_ITEM_FEE) {
                    setAcceptError('Người nhận không đủ Credit và cần nạp Credit trước khi được chọn.');
                    return;
                  }
                  dispatch(actions.acceptItemRequest({ requestId: acceptRequest.id, ownerId: user.id }));
                  setAcceptConfirmId('');
                }}
              >
                Xác nhận
              </Button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}

function RequestPanel({
  itemStatus,
  hasAcceptedRequest,
  itemRequests,
  data,
  onAccept,
}: {
  itemStatus: string;
  hasAcceptedRequest: boolean;
  itemRequests: ReturnType<typeof selectData>['itemRequests'];
  data: ReturnType<typeof selectData>;
  onAccept: (requestId: string) => void;
}) {
  return (
    <div className="mt-2 rounded-lg bg-white p-4 ring-1 ring-border/80">
      <h3 className="text-sm font-bold">Yêu cầu cho bài đăng</h3>
      {itemRequests.length ? (
        <div className="mt-3 space-y-3">
          {itemRequests.map((request) => {
            const requester = data.users.find((entry) => entry.id === request.requesterId);
            const offeredItem = request.offeredItemId
              ? data.items.find((entry) => entry.id === request.offeredItemId)
              : undefined;
            if (!requester) return null;
            return (
              <article key={request.id} className="rounded-md border border-border p-3">
                <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                  <div className="flex min-w-0 gap-3">
                    <Avatar user={requester} />
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <strong className="truncate text-sm">{requester.name}</strong>
                        <TypeBadge type={request.type} />
                        <span className="rounded-sm bg-amber-50 px-2 py-1 text-[11px] font-semibold text-warning">
                          {requestStatusLabel[request.status]}
                        </span>
                      </div>
                      <p className="mt-1 text-xs text-text-muted">
                        {new Date(request.createdAt).toLocaleString('vi-VN')}
                      </p>
                      {request.message ? (
                        <p className="mt-2 text-sm leading-6 text-text-secondary">{request.message}</p>
                      ) : null}
                    </div>
                  </div>
                  <div className="flex shrink-0 flex-wrap gap-2">
                    <Link to="/profile">
                      <Button size="sm" variant="ghost" icon="user">
                        Xem hồ sơ
                      </Button>
                    </Link>
                    {request.status === 'ACCEPTED' ? (
                      <Link to="/messages">
                        <Button size="sm" variant="outline" icon="messages">
                          Nhắn tin
                        </Button>
                      </Link>
                    ) : request.status === 'PENDING' && !hasAcceptedRequest && itemStatus !== 'IN_TRANSACTION' ? (
                      <Button
                        size="sm"
                        variant="outline"
                        icon="check"
                        onClick={() => onAccept(request.id)}
                      >
                        Chọn người này
                      </Button>
                    ) : null}
                  </div>
                </div>
                {offeredItem ? (
                  <div className="mt-3 rounded-md bg-surface-low p-3">
                    <p className="text-xs font-bold text-text-muted">Món đề nghị trao đổi</p>
                    <div className="mt-2 flex items-center gap-3">
                      <img src={offeredItem.images[0]} alt="" className="size-14 rounded-md object-cover" />
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-bold">{offeredItem.title}</p>
                        <div className="mt-1 flex flex-wrap gap-2">
                          <ConditionBadge condition={offeredItem.condition} />
                          <span className="text-xs text-text-muted">{offeredItem.district}</span>
                        </div>
                      </div>
                      <Link to={`/items/${offeredItem.id}`}>
                        <Button size="sm" variant="outline" icon="eye">
                          Xem chi tiết
                        </Button>
                      </Link>
                    </div>
                  </div>
                ) : null}
              </article>
            );
          })}
        </div>
      ) : (
        <p className="mt-3 text-sm text-text-muted">Chưa có yêu cầu nào cho món này.</p>
      )}
    </div>
  );
}
