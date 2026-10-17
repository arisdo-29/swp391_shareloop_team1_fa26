import { useEffect, useRef, useState } from 'react';
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
  ItemImage,
  PageHeader,
  StatusBadge,
  TextArea,
  TypeBadge,
} from '../../components/ui';
import { RECEIVE_ITEM_FEE } from '../../utils/credit';
import { catalogApi } from '../../services/catalogApi';
import { itemApi, type EditQuote, type ItemResponse } from '../../services/itemApi';
import { toAreaNameMap, toCategoryNameMap, toFeItem } from '../../services/mappers';
import { requestApi, toBackendUserId, toFeItemRequest } from '../../services/requestApi';
import type { Item, ItemRequest, User } from '../../types/domain';
import type { ApiError } from '../../services/http';
import { useWallet } from '../../app/useWallet';

const requestStatusLabel = {
  PENDING: 'Chờ phản hồi',
  RESERVED: 'Đã được chọn',
  REJECTED: 'Không được chọn',
  CANCELLED: 'Đã hủy',
};

export function Activities() {
  const location = useLocation();
  const data = useAppSelector(selectData);
  const user = useAppSelector(selectCurrentUser)!;
  const dispatch = useAppDispatch();
  const { refresh: refreshWallet } = useWallet();
  const [tab, setTab] = useState<'items' | 'transactions'>('items');
  const [editingId, setEditingId] = useState('');
  const [removeConfirmId, setRemoveConfirmId] = useState('');
  const [requestsItemId, setRequestsItemId] = useState('');
  const [acceptConfirmId, setAcceptConfirmId] = useState('');
  const [acceptError, setAcceptError] = useState('');
  const [selectNotice, setSelectNotice] = useState('');
  const [selectingRequestId, setSelectingRequestId] = useState('');
  const [editNotice, setEditNotice] = useState('');
  const [draftTitle, setDraftTitle] = useState('');
  const [draftDescription, setDraftDescription] = useState('');
  const [editQuote, setEditQuote] = useState<EditQuote | null>(null);
  const [editQuoteLoading, setEditQuoteLoading] = useState(false);
  const [editQuoteError, setEditQuoteError] = useState('');
  const [apiEditingItem, setApiEditingItem] = useState<ItemResponse | null>(null);
  const [editSaving, setEditSaving] = useState(false);
  const [myItems, setMyItems] = useState<Item[]>([]);
  const [apiRequestsByItem, setApiRequestsByItem] = useState<Record<string, ItemRequest[]>>({});
  const [apiSentRequests, setApiSentRequests] = useState<ItemRequest[]>([]);
  const [myItemsLoading, setMyItemsLoading] = useState(true);
  const [myItemsError, setMyItemsError] = useState('');
  const [cancelingRequestId, setCancelingRequestId] = useState('');
  const [renewingItemId, setRenewingItemId] = useState('');
  const [removingItemId, setRemovingItemId] = useState('');
  const selectInFlight = useRef(false);
  const currentUserId = toBackendUserId(user.id);

  const openEditor = async (item: Item) => {
    setEditNotice('');
    setEditingId(item.id);
    setDraftTitle(item.title);
    setDraftDescription(item.description);
    setEditQuote(null);
    setApiEditingItem(null);
    setEditQuoteError('');
    if (!/^\d+$/.test(item.id)) return;
    setEditQuoteLoading(true);
    try {
      const [quote, detail] = await Promise.all([
        itemApi.getEditQuote(Number(item.id)),
        itemApi.detail(Number(item.id)),
      ]);
      setEditQuote(quote);
      setApiEditingItem(detail);
      setDraftTitle(detail.title);
      setDraftDescription(detail.description);
    } catch (error) {
      const apiError = error as ApiError;
      setEditQuoteError(apiError.message || 'Không tải được báo giá chỉnh sửa.');
    } finally {
      setEditQuoteLoading(false);
    }
  };

  const saveApiItem = async () => {
    if (!apiEditingItem || editSaving || !draftTitle.trim() || !draftDescription.trim()) return;
    setEditSaving(true);
    setEditQuoteError('');
    try {
      const updated = await itemApi.update(apiEditingItem.id, {
        categoryId: apiEditingItem.categoryId,
        areaId: apiEditingItem.areaId,
        offerType: apiEditingItem.offerType,
        title: draftTitle.trim(),
        description: draftDescription.trim(),
        desiredItem: apiEditingItem.desiredItem,
        condition: apiEditingItem.condition,
        defectNote: apiEditingItem.defectNote,
        brand: apiEditingItem.brand,
        images: apiEditingItem.images,
        defectImages: apiEditingItem.defectImages ?? undefined,
        operatingStatus: apiEditingItem.operatingStatus ?? undefined,
      });
      const currentItem = myItems.find((item) => item.id === String(updated.id));
      const mapped = toFeItem(
        updated,
        new Map([[updated.categoryId, currentItem?.category ?? 'Danh mục']]),
        new Map([[updated.areaId, currentItem?.district ?? 'Khu vực']]),
      );
      setMyItems((current) => current.map((item) => item.id === mapped.id ? mapped : item));
      setEditingId('');
      setApiEditingItem(null);
      setEditNotice('Đã cập nhật bài đăng và gửi duyệt lại.');
      refreshWallet();
    } catch (error) {
      const apiError = error as ApiError;
      setEditQuoteError(apiError.message || 'Không cập nhật được bài đăng.');
    } finally {
      setEditSaving(false);
    }
  };

  const removeApiItem = async (itemId: string) => {
    const id = Number(itemId);
    if (!Number.isSafeInteger(id) || removingItemId) return;
    setRemovingItemId(itemId);
    setMyItemsError('');
    try {
      await itemApi.remove(id);
      setMyItems((current) => current.filter((item) => item.id !== itemId));
      setRemoveConfirmId('');
    } catch (error) {
      const apiError = error as ApiError;
      setMyItemsError(apiError.message || 'Không gỡ được bài đăng.');
    } finally {
      setRemovingItemId('');
    }
  };

  useEffect(() => {
    dispatch(actions.refreshExpiredItems());
  }, [dispatch]);

  useEffect(() => {
    let active = true;
    setMyItemsLoading(true);
    setMyItemsError('');
    if (!currentUserId) {
      setMyItems(data.items.filter((item) => item.ownerId === user.id));
      setApiRequestsByItem({});
      setApiSentRequests([]);
      setMyItemsLoading(false);
      return () => {
        active = false;
      };
    }
    Promise.all([catalogApi.categories(), catalogApi.areas(), itemApi.mine(), requestApi.mine(currentUserId)])
      .then(async ([categories, areas, items, sentRequests]) => {
        if (!active) return;
        const categoryNames = toCategoryNameMap(categories);
        const areaNames = toAreaNameMap(areas);
        const mappedItems = items.map((item) => toFeItem(item, categoryNames, areaNames));
        const requestEntries = await Promise.all(
          mappedItems.map(async (item) => {
            const requests = await requestApi.forItem(Number(item.id), currentUserId);
            return [item.id, requests.map((request) => toFeItemRequest(request))] as const;
          }),
        );
        if (!active) return;
        setMyItems(mappedItems);
        setApiRequestsByItem(Object.fromEntries(requestEntries));
        setApiSentRequests(sentRequests.map((request) => toFeItemRequest(request, user.id)));
      })
      .catch(() => {
        if (!active) return;
        setMyItems([]);
        setApiRequestsByItem({});
        setApiSentRequests([]);
        setMyItemsError('Không tải được bài đăng của bạn.');
      })
      .finally(() => {
        if (active) setMyItemsLoading(false);
      });
    return () => {
      active = false;
    };
  }, [currentUserId, data.items, user.id]);
  const txs = data.transactions.filter((t) => t.ownerId === user.id || t.requesterId === user.id);
  const acceptApiRequest = Object.values(apiRequestsByItem)
    .flat()
    .find((request) => request.id === acceptConfirmId);
  const acceptRequest = acceptApiRequest ?? data.itemRequests.find((request) => request.id === acceptConfirmId);
  const acceptItem = (acceptApiRequest ? myItems : data.items).find((item) => item.id === acceptRequest?.itemId);
  const acceptRequester = acceptRequest
    ? data.users.find((entry) => entry.id === acceptRequest.requesterId) ??
      apiDisplayUser(acceptRequest.requesterId, acceptRequest.requesterName)
    : undefined;
  const acceptOfferedItem = data.items.find((item) => item.id === acceptRequest?.offeredItemId);
  const cancelApiRequest = async (requestId: string) => {
    if (!currentUserId || cancelingRequestId) return;
    const numericRequestId = Number(requestId.replace(/^api_req_/, ''));
    if (!Number.isSafeInteger(numericRequestId)) return;
    setCancelingRequestId(requestId);
    try {
      const request = await requestApi.cancel(numericRequestId, currentUserId);
      setApiSentRequests((current) =>
        current.map((entry) => (entry.id === requestId ? toFeItemRequest(request, user.id) : entry)),
      );
    } catch {
      setMyItemsError('Không hủy được yêu cầu. Vui lòng thử lại.');
    } finally {
      setCancelingRequestId('');
    }
  };

  const renewApiItem = async (item: Item) => {
    const itemId = Number(item.id);
    if (!Number.isSafeInteger(itemId) || renewingItemId) return;
    setRenewingItemId(item.id);
    setMyItemsError('');
    try {
      const renewed = await itemApi.renewItem(itemId);
      setMyItems((current) => current.map((entry) =>
        entry.id === item.id
          ? { ...entry, status: 'APPROVED', expiresAt: renewed.expireAt }
          : entry,
      ));
      refreshWallet();
    } catch (error) {
      const apiError = error as ApiError;
      setMyItemsError(apiError.message || 'Không gia hạn được bài đăng. Vui lòng thử lại.');
    } finally {
      setRenewingItemId('');
    }
  };

  const selectApiRequest = async (request: ItemRequest) => {
    if (!currentUserId || selectInFlight.current || request.status !== 'PENDING') return;
    const numericRequestId = Number(request.id.replace(/^api_req_/, ''));
    const numericItemId = Number(request.itemId);
    if (!Number.isSafeInteger(numericRequestId) || !Number.isSafeInteger(numericItemId)) {
      setAcceptError('Yêu cầu hoặc bài đăng không hợp lệ.');
      return;
    }

    selectInFlight.current = true;
    setSelectingRequestId(request.id);
    setAcceptError('');
    setSelectNotice('');
    try {
      const selected = await requestApi.select(numericRequestId, currentUserId);
      setApiRequestsByItem((current) => ({
        ...current,
        [request.itemId]: (current[request.itemId] ?? []).map((entry) =>
          entry.id === request.id ? toFeItemRequest(selected) : entry,
        ),
      }));

      try {
        const [categories, areas, requests, item] = await Promise.all([
          catalogApi.categories(),
          catalogApi.areas(),
          requestApi.forItem(numericItemId, currentUserId),
          itemApi.detail(numericItemId),
        ]);
        setApiRequestsByItem((current) => ({
          ...current,
          [request.itemId]: requests.map((entry) => toFeItemRequest(entry)),
        }));
        const mappedItem = toFeItem(item, toCategoryNameMap(categories), toAreaNameMap(areas));
        setMyItems((current) => current.map((entry) => (entry.id === request.itemId ? mappedItem : entry)));
      } catch {
        setSelectNotice('Đã chọn người nhận, nhưng chưa thể làm mới đầy đủ trạng thái từ Backend.');
      }
      setAcceptConfirmId('');
    } catch {
      setAcceptError('Không chọn được người nhận. Vui lòng làm mới dữ liệu và thử lại.');
    } finally {
      selectInFlight.current = false;
      setSelectingRequestId('');
    }
  };

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
      {selectNotice ? (
        <div className="mb-5">
          <Alert tone="warning">{selectNotice}</Alert>
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
          {myItemsLoading ? (
            <EmptyState title="Đang tải bài đăng" text="Vui lòng chờ trong giây lát." />
          ) : myItemsError ? (
            <EmptyState title="Không tải được bài đăng" text={myItemsError} />
          ) : myItems.length ? (
            <div className="space-y-3">
              {myItems.map((item) => {
                const approved = item.status === 'approved' || item.status === 'APPROVED';
                const apiItem = /^\d+$/.test(item.id);
                const canEdit = apiItem || !approved || (item.postApprovalEditCount ?? 0) < 1;
                const itemRequests = apiItem
                  ? apiRequestsByItem[item.id] ?? []
                  : data.itemRequests.filter((request) => request.itemId === item.id);
                const hasAcceptedRequest = itemRequests.some((request) => request.status === 'RESERVED');
                return (
                  <div key={item.id}>
                    <article className="grid gap-4 rounded-lg bg-white p-3 ring-1 ring-border/80 sm:grid-cols-[120px_1fr_auto] sm:items-center">
                      <ItemImage
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
                          {item.expiresAt
                            ? `Hết hạn: ${new Date(item.expiresAt).toLocaleDateString('vi-VN')} · `
                            : ''}
                          {item.district}
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
                            void openEditor(item);
                          }}
                        >
                          Sửa
                        </Button>
                        <Button
                          size="sm"
                          variant="danger"
                          icon="trash"
                          disabled={item.status === 'removed' || removingItemId === item.id}
                          onClick={() => setRemoveConfirmId(item.id)}
                        >
                          Gỡ
                        </Button>
                        {item.status === 'expired' ? (
                          <Button
                            size="sm"
                            variant="outline"
                            icon="renew"
                            disabled={renewingItemId === item.id}
                            onClick={() => apiItem
                              ? void renewApiItem(item)
                              : dispatch(actions.renewItem({ itemId: item.id, ownerId: user.id }))}
                          >
                            {renewingItemId === item.id ? 'Đang gia hạn...' : 'Gia hạn'}
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
                        allowAccept={!apiItem || Boolean(currentUserId)}
                        onAccept={(requestId) => {
                          setAcceptError('');
                          setAcceptConfirmId(requestId);
                        }}
                      />
                    ) : null}
                  </div>
                );
              })}
              {currentUserId ? (
                <SentRequestPanel
                  itemRequests={apiSentRequests}
                  items={myItems}
                  cancelingRequestId={cancelingRequestId}
                  onCancel={(requestId) => void cancelApiRequest(requestId)}
                />
              ) : null}
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
                        <ItemImage
                          src={item.images[0]}
                          alt={item.title}
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
            {/^[0-9]+$/.test(editingId) ? (
              <div className="mt-4">
                {editQuoteLoading ? <Alert>Đang tải báo giá chỉnh sửa...</Alert> : null}
                {editQuoteError ? <Alert tone="error">{editQuoteError}</Alert> : null}
                {editQuote ? (
                  <Alert tone={editQuote.free ? 'success' : 'warning'}>
                    {editQuote.free
                      ? `Lượt sửa này miễn phí (${editQuote.reason}).`
                      : `Phí sửa do Backend báo giá: ${editQuote.amount} Credit (${editQuote.reason}).`}
                  </Alert>
                ) : null}
              </div>
            ) : null}
            <div className="mt-5 space-y-4">
              <Field
                label="Tên món đồ"
                value={draftTitle}
                onChange={(event) => setDraftTitle(event.target.value)}
                disabled={editSaving || editQuoteLoading}
              />
              <TextArea
                label="Mô tả"
                value={draftDescription}
                onChange={(event) => setDraftDescription(event.target.value)}
                disabled={editSaving || editQuoteLoading}
              />
            </div>
            <div className="mt-6 flex justify-end gap-2">
              <Button variant="ghost" onClick={() => setEditingId('')}>
                Hủy
              </Button>
              <Button
                disabled={editSaving || editQuoteLoading || !draftTitle.trim() || !draftDescription.trim()}
                onClick={() => {
                  if (/^[0-9]+$/.test(editingId)) {
                    void saveApiItem();
                    return;
                  }
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
                {editSaving ? 'Đang lưu...' : 'Lưu thay đổi'}
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
              <Button variant="ghost" disabled={Boolean(removingItemId)} onClick={() => setRemoveConfirmId('')}>
                Hủy
              </Button>
              <Button
                variant="danger"
                disabled={Boolean(removingItemId)}
                onClick={() => /^[0-9]+$/.test(removeConfirmId)
                  ? void removeApiItem(removeConfirmId)
                  : (() => {
                      dispatch(actions.removeItem({ itemId: removeConfirmId, ownerId: user.id }));
                      setRemoveConfirmId('');
                    })()}
              >
                {removingItemId ? 'Đang gỡ...' : 'Xác nhận gỡ'}
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
                disabled={selectingRequestId === acceptRequest.id}
                onClick={() => {
                  setAcceptError('');
                  setAcceptConfirmId('');
                }}
              >
                Hủy
              </Button>
              <Button
                disabled={selectingRequestId === acceptRequest.id}
                onClick={() => {
                  if (acceptApiRequest) {
                    void selectApiRequest(acceptApiRequest);
                    return;
                  }
                  if (acceptRequest.type === 'gift' && acceptRequester.availableCredit < RECEIVE_ITEM_FEE) {
                    setAcceptError('Người nhận không đủ Credit và cần nạp Credit trước khi được chọn.');
                    return;
                  }
                  dispatch(actions.acceptItemRequest({ requestId: acceptRequest.id, ownerId: user.id }));
                  setAcceptConfirmId('');
                }}
              >
                {selectingRequestId === acceptRequest.id ? 'Đang chọn...' : 'Xác nhận'}
              </Button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}

// Lấy chữ cái đầu của 2 từ cuối trong họ tên, ví dụ "Nguyễn Văn B" → "VB".
function nameInitials(name: string): string {
  const words = name.trim().split(/\s+/);
  const lastTwoWords = words.slice(-2);
  return lastTwoWords.map((word) => word.charAt(0)).join('').toUpperCase();
}

function apiDisplayUser(userId: string, name?: string, trustStars?: number, rank?: string): User {
  const apiId = userId.replace(/^api_/, '');
  const fallbackName = userId.startsWith('api_') ? `Người dùng #${apiId}` : 'Người dùng';
  const fallbackInitials = userId.startsWith('api_') ? apiId.slice(0, 2).toUpperCase() : 'ND';
  const trimmedName = name?.trim() ?? '';
  return {
    id: userId,
    username: userId,
    password: '',
    name: trimmedName ? trimmedName : fallbackName,
    email: '',
    phone: '',
    district: '',
    avatarInitials: trimmedName ? nameInitials(trimmedName) : fallbackInitials,
    totalCredit: 0,
    availableCredit: 0,
    holdCredit: 0,
    rewardPoints: 0,
    reputationStars: typeof trustStars === 'number' ? trustStars : 5,
    rank: rank ?? 'Thành viên',
    status: 'active',
    role: 'user',
    joinedAt: new Date().toISOString(),
    totalTx: 0,
  };
}

function SentRequestPanel({
  itemRequests,
  items,
  cancelingRequestId,
  onCancel,
}: {
  itemRequests: ItemRequest[];
  items: Item[];
  cancelingRequestId: string;
  onCancel: (requestId: string) => void;
}) {
  return (
    <div className="rounded-lg bg-white p-4 ring-1 ring-border/80">
      <h3 className="text-sm font-bold">Yêu cầu đã gửi</h3>
      {itemRequests.length ? (
        <div className="mt-3 space-y-3">
          {itemRequests.map((request) => {
            const item = items.find((entry) => entry.id === request.itemId);
            return (
              <article key={request.id} className="rounded-md border border-border p-3">
                <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <strong className="truncate text-sm">{item?.title ?? `Bài đăng #${request.itemId}`}</strong>
                      <TypeBadge type={request.type} />
                      <span className="rounded-sm bg-amber-50 px-2 py-1 text-[11px] font-semibold text-warning">
                        {requestStatusLabel[request.status]}
                      </span>
                    </div>
                    <p className="mt-1 text-xs text-text-muted">
                      {new Date(request.createdAt).toLocaleString('vi-VN')}
                    </p>
                    {request.offeredItemId ? (
                      <p className="mt-2 text-xs text-text-muted">
                        Món đề nghị trao đổi: #{request.offeredItemId}
                      </p>
                    ) : null}
                  </div>
                  <div className="flex shrink-0 flex-wrap gap-2">
                    <Link to={`/items/${request.itemId}`}>
                      <Button size="sm" variant="outline" icon="eye">
                        Xem bài
                      </Button>
                    </Link>
                    {request.status === 'PENDING' ? (
                      <Button
                        size="sm"
                        variant="ghost"
                        icon="close"
                        disabled={cancelingRequestId === request.id}
                        onClick={() => onCancel(request.id)}
                      >
                        Hủy yêu cầu
                      </Button>
                    ) : null}
                  </div>
                </div>
              </article>
            );
          })}
        </div>
      ) : (
        <p className="mt-3 text-sm text-text-muted">Bạn chưa gửi yêu cầu nào.</p>
      )}
    </div>
  );
}

function RequestPanel({
  itemStatus,
  hasAcceptedRequest,
  itemRequests,
  data,
  allowAccept,
  onAccept,
}: {
  itemStatus: string;
  hasAcceptedRequest: boolean;
  itemRequests: ReturnType<typeof selectData>['itemRequests'];
  data: ReturnType<typeof selectData>;
  allowAccept: boolean;
  onAccept: (requestId: string) => void;
}) {
  return (
    <div className="mt-2 rounded-lg bg-white p-4 ring-1 ring-border/80">
      <h3 className="text-sm font-bold">Yêu cầu cho bài đăng</h3>
      {itemRequests.length ? (
        <div className="mt-3 space-y-3">
          {itemRequests.map((request) => {
            const requester =
              data.users.find((entry) => entry.id === request.requesterId) ??
              apiDisplayUser(
                request.requesterId,
                request.requesterName,
                request.requesterTrustStars,
                request.requesterRank
              );
            const offeredItem = request.offeredItemId
              ? data.items.find((entry) => entry.id === request.offeredItemId)
              : undefined;
            return (
              <article key={request.id} className="rounded-md border border-border p-3">
                <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                  <div className="flex min-w-0 gap-3">
                    <Avatar user={requester} />
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <strong className="truncate text-sm">{requester.name}</strong>
                        {request.requesterTrustStars !== undefined ? (
                          <span
                            className="inline-flex items-center gap-0.5 rounded bg-amber-50 px-1.5 py-0.5 text-[11px] font-medium text-amber-700"
                            title="Điểm uy tín TrustStars"
                          >
                            ⭐ {request.requesterTrustStars}
                          </span>
                        ) : null}
                        {request.requesterRank ? (
                          <span
                            className="rounded bg-sky-50 px-1.5 py-0.5 text-[11px] font-medium text-sky-700"
                            title="Hạng thành viên"
                          >
                            {request.requesterRank}
                          </span>
                        ) : null}
                        {typeof request.requesterAsDescribedRate === 'number' ? (
                          <span
                            className="rounded bg-emerald-50 px-1.5 py-0.5 text-[11px] font-medium text-emerald-700"
                            title="Tỉ lệ đúng mô tả trong các giao dịch"
                          >
                            Đúng mô tả: {Math.round(request.requesterAsDescribedRate * 100)}%
                          </span>
                        ) : null}
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
                    {request.status === 'RESERVED' ? (
                      <Link to="/messages">
                        <Button size="sm" variant="outline" icon="messages">
                          Nhắn tin
                        </Button>
                      </Link>
                    ) : allowAccept && request.status === 'PENDING' && !hasAcceptedRequest && itemStatus !== 'IN_TRANSACTION' ? (
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
                      <ItemImage src={offeredItem.images[0]} alt={offeredItem.title} className="size-14 rounded-md object-cover" />
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
