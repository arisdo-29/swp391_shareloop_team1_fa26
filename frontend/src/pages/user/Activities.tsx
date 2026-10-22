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
  Icon,
  ItemImage,
  PageHeader,
  Select,
  StatusBadge,
  TextArea,
  TypeBadge,
} from '../../components/ui';
import { CONDITIONS } from '../../constants/domain';
import { RECEIVE_ITEM_FEE } from '../../utils/credit';
import { conditionLabel } from '../../utils/formatting';
import { fileToDataUrl } from '../../utils/files';
import { catalogApi, type CategoryAttributeResponse, type CategoryResponse } from '../../services/catalogApi';
import {
  itemApi,
  type ApiCondition,
  type EditQuote,
  type EditQuoteReason,
  type ItemResponse,
  type ListingFeeConfigResponse,
  type OfferType,
  type OperatingStatus,
  type UpdateItemRequest,
} from '../../services/itemApi';
import { mediaApi } from '../../services/mediaApi';
import { toAreaNameMap, toAreaOptions, toCategoryNameMap, toFeItem, type AreaOption } from '../../services/mappers';
import { requestApi, toBackendUserId, toFeItemRequest } from '../../services/requestApi';
import type { Item, ItemRequest, User } from '../../types/domain';
import type { ApiError } from '../../services/http';
import { useWallet } from '../../app/useWallet';
import { canChat, requestStatusLabel } from '../../utils/request';

const editQuoteReasonLabel: Record<EditQuoteReason, string> = {
  NOT_YET_APPROVED: 'Bài chưa được duyệt lần đầu',
  REPOST_FEE: 'Gửi lại bài sau khi bị từ chối',
  PENDING_REVIEW_OVERWRITE: 'Cập nhật bản sửa đang chờ duyệt',
  FIRST_FREE_EDIT: 'Lượt sửa miễn phí đầu tiên',
  PAID_EDIT: 'Lượt sửa có phí',
};

function getEditQuoteReasonLabel(reason: string) {
  return editQuoteReasonLabel[reason as EditQuoteReason] ?? 'Theo quy định của ShareLoop';
}

type ListingFeeAction = { type: 'renew' | 'boost'; item: Item };
const WALLET_INSUFFICIENT_BALANCE = 'WALLET_INSUFFICIENT_BALANCE';

const ITEM_IMAGES_MIN = 3;
const ITEM_IMAGES_MAX = 8;

const OPERATING_STATUS_LABELS: Record<OperatingStatus, string> = {
  NORMAL: 'Hoạt động bình thường',
  PARTIAL: 'Hoạt động một phần',
  NOT_WORKING: 'Không hoạt động',
};

const isOperatingStatus = (value: string): value is OperatingStatus =>
  value === 'NORMAL' || value === 'PARTIAL' || value === 'NOT_WORKING';

type EditFormErrors = Partial<Record<
  'title' | 'description' | 'desiredItem' | 'brand' | 'images' | 'defectNote' | 'defectImages' | 'operatingStatus',
  string
>>;

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
  const [editQuote, setEditQuote] = useState<EditQuote | null>(null);
  const [editQuoteLoading, setEditQuoteLoading] = useState(false);
  const [editQuoteError, setEditQuoteError] = useState('');
  const [editQuoteNeedsTopUp, setEditQuoteNeedsTopUp] = useState(false);
  const [apiEditingItem, setApiEditingItem] = useState<ItemResponse | null>(null);
  const [editSaving, setEditSaving] = useState(false);
  const [myItems, setMyItems] = useState<Item[]>([]);
  const [apiRequestsByItem, setApiRequestsByItem] = useState<Record<string, ItemRequest[]>>({});
  const [apiSentRequests, setApiSentRequests] = useState<ItemRequest[]>([]);
  const [myItemsLoading, setMyItemsLoading] = useState(true);
  const [myItemsError, setMyItemsError] = useState('');
  const [cancelingRequestId, setCancelingRequestId] = useState('');
  const [renewingItemId, setRenewingItemId] = useState('');
  const [boostingItemId, setBoostingItemId] = useState('');
  const [listingFeeAction, setListingFeeAction] = useState<ListingFeeAction | null>(null);
  const [listingFees, setListingFees] = useState<ListingFeeConfigResponse | null>(null);
  const [listingFeesLoading, setListingFeesLoading] = useState(false);
  const [listingFeeError, setListingFeeError] = useState('');
  const [listingFeeNeedsTopUp, setListingFeeNeedsTopUp] = useState(false);
  const [listingFeeNotice, setListingFeeNotice] = useState('');
  const [removingItemId, setRemovingItemId] = useState('');
  const [itemsReloadKey, setItemsReloadKey] = useState(0);
  const [editCategories, setEditCategories] = useState<CategoryResponse[]>([]);
  const [boostableCategoryIds, setBoostableCategoryIds] = useState<number[]>([]);
  const [editAreas, setEditAreas] = useState<AreaOption[]>([]);
  const selectInFlight = useRef(false);
  const listingFeeInFlight = useRef(false);
  const currentUserId = toBackendUserId(user.id);

  useEffect(() => {
    if (!currentUserId || !listingFeeAction) return;
    let active = true;
    setListingFees(null);
    setListingFeesLoading(true);
    itemApi.getListingFees()
      .then((fees) => {
        if (active) setListingFees(fees);
      })
      .catch(() => {
        if (active) setListingFees(null);
      })
      .finally(() => {
        if (active) setListingFeesLoading(false);
      });
    return () => {
      active = false;
    };
  }, [currentUserId, listingFeeAction]);

  const openEditor = async (item: Item) => {
    setEditNotice('');
    setEditingId(item.id);
    setEditQuote(null);
    setApiEditingItem(null);
    setEditQuoteError('');
    setEditQuoteNeedsTopUp(false);
    if (!/^\d+$/.test(item.id)) return;
    setEditQuoteLoading(true);
    try {
      const [quote, detail] = await Promise.all([
        itemApi.getEditQuote(Number(item.id)),
        itemApi.detail(Number(item.id)),
      ]);
      setEditQuote(quote);
      setApiEditingItem(detail);
    } catch (error) {
      const apiError = error as ApiError;
      setEditQuoteError(apiError.message || 'Không tải được báo giá chỉnh sửa.');
    } finally {
      setEditQuoteLoading(false);
    }
  };

  const saveApiItem = async (input: UpdateItemRequest) => {
    if (!apiEditingItem || editSaving) return;
    setEditSaving(true);
    setEditQuoteError('');
    setEditQuoteNeedsTopUp(false);
    try {
      const latestQuote = await itemApi.getEditQuote(apiEditingItem.id);
      setEditQuote(latestQuote);
      const updated = await itemApi.update(apiEditingItem.id, input);
      const mapped = toFeItem(
        updated,
        toCategoryNameMap(editCategories),
        new Map(editAreas.map((area) => [area.id, area.label])),
      );
      setMyItems((current) => current.map((item) => item.id === mapped.id
        ? { ...mapped, expiresAt: item.expiresAt, boostedUntil: item.boostedUntil }
        : item));
      setEditingId('');
      setApiEditingItem(null);
      setEditNotice('Đã cập nhật bài đăng và gửi duyệt lại.');
      refreshWallet();
    } catch (error) {
      const apiError = error as ApiError;
      const insufficientCredit = apiError.code === WALLET_INSUFFICIENT_BALANCE;
      setEditQuoteNeedsTopUp(insufficientCredit);
      setEditQuoteError(insufficientCredit
        ? 'Số dư khả dụng không đủ để chỉnh sửa bài. Vui lòng nạp thêm Credit.'
        : apiError.message || 'Không cập nhật được bài đăng.');
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
      setMyItems([]);
      setApiRequestsByItem({});
      setApiSentRequests([]);
      setMyItemsError('Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.');
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
        setEditCategories(categories.filter((category) => !category.restricted));
        setBoostableCategoryIds(categories.filter((category) => category.boostable).map((category) => category.id));
        setEditAreas(toAreaOptions(areas));
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
      .catch((caught) => {
        if (!active) return;
        const apiError = caught as ApiError;
        setMyItems([]);
        setEditCategories([]);
        setBoostableCategoryIds([]);
        setEditAreas([]);
        setApiRequestsByItem({});
        setApiSentRequests([]);
        setMyItemsError(apiError.message || 'Không tải được bài đăng của bạn.');
      })
      .finally(() => {
        if (active) setMyItemsLoading(false);
      });
    return () => {
      active = false;
    };
  }, [currentUserId, itemsReloadKey, user.id]);
  const txs = data.transactions.filter((t) => t.ownerId === user.id || t.requesterId === user.id);
  const apiTransactions = [...new Map([
    ...apiSentRequests,
    ...Object.values(apiRequestsByItem).flat(),
  ].map((request) => [request.id, request])).values()];
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

  const confirmListingFeeAction = async () => {
    if (!listingFeeAction || listingFeeInFlight.current) return;
    const { item, type } = listingFeeAction;
    const itemId = Number(item.id);
    if (!Number.isSafeInteger(itemId)) return;
    listingFeeInFlight.current = true;
    setListingFeeError('');
    setListingFeeNeedsTopUp(false);
    setListingFeeNotice('');
    if (type === 'renew') setRenewingItemId(item.id);
    else setBoostingItemId(item.id);
    try {
      if (type === 'renew') {
        const renewed = await itemApi.renewItem(itemId);
        setMyItems((current) => current.map((entry) =>
          entry.id === item.id ? { ...entry, expiresAt: renewed.expireAt } : entry,
        ));
        setListingFeeNotice(
          renewed.fee > 0
            ? `Đã gia hạn bài và trừ ${renewed.fee} Credit.`
            : 'Đã gia hạn bài miễn phí.',
        );
      } else {
        const boosted = await itemApi.boostItem(itemId);
        setMyItems((current) => current.map((entry) =>
          entry.id === item.id ? { ...entry, boostedUntil: boosted.boostedUntil } : entry,
        ));
        setListingFeeNotice('Đã đẩy bài thành công.');
      }
      refreshWallet();
      setListingFeeAction(null);
    } catch (error) {
      const apiError = error as ApiError;
      const insufficientCredit = apiError.code === WALLET_INSUFFICIENT_BALANCE;
      setListingFeeNeedsTopUp(insufficientCredit);
      setListingFeeError(insufficientCredit
        ? `Số dư khả dụng không đủ để ${type === 'renew' ? 'gia hạn' : 'đẩy'} bài. Vui lòng nạp thêm Credit.`
        : apiError.message || `Không ${type === 'renew' ? 'gia hạn' : 'đẩy'} được bài đăng. Vui lòng thử lại.`);
    } finally {
      listingFeeInFlight.current = false;
      setRenewingItemId('');
      setBoostingItemId('');
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
        setSelectNotice('Đã chọn người nhận, nhưng chưa thể cập nhật đầy đủ trạng thái mới nhất.');
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
      {listingFeeNotice ? (
        <div className="mb-5">
          <Alert tone="success">{listingFeeNotice}</Alert>
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
          Giao dịch <span className="ml-1 text-xs">({currentUserId ? apiTransactions.length : txs.length})</span>
        </button>
      </div>

      {tab === 'items' ? (
        <section>
          {myItemsLoading ? (
            <EmptyState title="Đang tải bài đăng" text="Vui lòng chờ trong giây lát." />
          ) : myItemsError ? (
            <EmptyState
              title="Không tải được bài đăng"
              text={myItemsError}
              action={<Button variant="outline" onClick={() => setItemsReloadKey((key) => key + 1)}>Thử lại</Button>}
            />
          ) : myItems.length ? (
            <div className="space-y-3">
              {myItems.map((item) => {
                const apiItem = /^\d+$/.test(item.id);
                const canEdit = ['PENDING_REVIEW', 'APPROVED', 'REJECTED'].includes(item.status);
                const canUseListingFee = apiItem && item.status === 'APPROVED';
                const canBoost = canUseListingFee && item.categoryId !== undefined &&
                  boostableCategoryIds.includes(item.categoryId);
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
                        {item.boostedUntil ? (
                          <p className="mt-1 text-xs font-medium text-primary">
                            Đang được đẩy đến {new Date(item.boostedUntil).toLocaleString('vi-VN')}
                          </p>
                        ) : null}
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
                          disabled={!canEdit}
                          onClick={() => void openEditor(item)}
                        >
                          Sửa
                        </Button>
                        <Button
                          size="sm"
                          variant="danger"
                          icon="trash"
                          disabled={['RESERVED', 'TRADED', 'EXPIRED', 'removed'].includes(item.status) || removingItemId === item.id}
                          onClick={() => setRemoveConfirmId(item.id)}
                        >
                          Gỡ
                        </Button>
                        {canUseListingFee ? (
                          <Button
                            size="sm"
                            variant="outline"
                            icon="renew"
                            disabled={Boolean(renewingItemId || boostingItemId)}
                            onClick={() => {
                              setListingFeeError('');
                              setListingFeeNeedsTopUp(false);
                              setListingFeeAction({ type: 'renew', item });
                            }}
                          >
                            {renewingItemId === item.id ? 'Đang gia hạn...' : 'Gia hạn'}
                          </Button>
                        ) : null}
                        {canBoost ? (
                          <Button
                            size="sm"
                            variant="outline"
                            icon="arrow"
                            disabled={Boolean(renewingItemId || boostingItemId)}
                            onClick={() => {
                              setListingFeeError('');
                              setListingFeeNeedsTopUp(false);
                              setListingFeeAction({ type: 'boost', item });
                            }}
                          >
                            {boostingItemId === item.id ? 'Đang đẩy...' : 'Đẩy bài'}
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
          {currentUserId ? (
            myItemsLoading ? <EmptyState title="Đang tải giao dịch" text="Vui lòng chờ trong giây lát." /> : myItemsError ? (
              <EmptyState title="Không tải được giao dịch" text={myItemsError} action={<Button variant="outline" onClick={() => setItemsReloadKey((key) => key + 1)}>Thử lại</Button>} />
            ) : (
              <SentRequestPanel itemRequests={apiTransactions} items={myItems} cancelingRequestId={cancelingRequestId} onCancel={(requestId) => void cancelApiRequest(requestId)} />
            )
          ) : txs.length ? (
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
          <div className="max-h-[calc(100vh-2rem)] w-full overflow-y-auto rounded-t-xl bg-white p-5 shadow-lg sm:max-w-lg sm:rounded-xl sm:p-6">
            <h2 className="text-xl font-bold">Chỉnh sửa bài đăng</h2>
            <p className="mt-1 text-sm text-text-muted">Bài sẽ chờ duyệt lại sau khi lưu.</p>
            {/^[0-9]+$/.test(editingId) ? (
              <div className="mt-4">
                {editQuoteLoading ? <Alert>Đang tải báo giá chỉnh sửa...</Alert> : null}
                {editQuoteError ? <Alert tone="error">{editQuoteError}</Alert> : null}
                {editQuote ? (
                  <Alert tone={editQuote.free ? 'success' : 'warning'}>
                    {editQuote.free
                      ? `Lượt sửa này miễn phí. ${getEditQuoteReasonLabel(editQuote.reason)}.`
                      : `Sửa bài sẽ giữ ${editQuote.amount} Credit. ${getEditQuoteReasonLabel(editQuote.reason)}.`}
                  </Alert>
                ) : null}
                {editQuoteNeedsTopUp ? (
                  <Link className="mt-3 inline-block" to="/credit">
                    <Button size="sm">Nạp Credit</Button>
                  </Link>
                ) : null}
              </div>
            ) : null}
            {apiEditingItem ? (
              <EditItemForm
                key={apiEditingItem.id}
                item={apiEditingItem}
                categories={editCategories}
                areas={editAreas}
                disabled={editSaving || editQuoteLoading}
                onCancel={() => setEditingId('')}
                onSave={saveApiItem}
              />
            ) : editQuoteLoading ? null : (
              <div className="mt-6 flex justify-end">
                <Button variant="ghost" onClick={() => setEditingId('')}>Hủy</Button>
              </div>
            )}
          </div>
        </div>
      ) : null}

      {listingFeeAction ? (
        <div className="fixed inset-0 z-40 grid place-items-end bg-black/30 sm:place-items-center sm:p-4">
          <div
            role="dialog"
            aria-modal="true"
            className="w-full rounded-t-xl bg-white p-5 shadow-lg sm:max-w-lg sm:rounded-xl sm:p-6"
          >
            <h2 className="text-xl font-bold">
              {listingFeeAction.type === 'renew' ? 'Xác nhận gia hạn bài' : 'Xác nhận đẩy bài'}
            </h2>
            <p className="mt-2 text-sm font-semibold">{listingFeeAction.item.title}</p>
            <p className="mt-2 text-sm leading-6 text-text-muted">
              {listingFeesLoading
                ? 'Đang tải mức phí hiện hành...'
                : listingFees
                  ? listingFeeAction.type === 'renew'
                    ? `Phí gia hạn: ${listingFees.renewFee} Credit.`
                    : `Phí đẩy bài: ${listingFees.boostFee} Credit, hiệu lực ${listingFees.boostDays} ngày.`
                  : 'Không tải được mức phí hiện hành. Vui lòng đóng hộp thoại và thử lại.'}
            </p>
            {listingFeeError ? (
              <div className="mt-4">
                <Alert tone="error">{listingFeeError}</Alert>
                {listingFeeNeedsTopUp ? (
                  <Link className="mt-3 inline-block" to="/credit">
                    <Button size="sm">Nạp Credit</Button>
                  </Link>
                ) : null}
              </div>
            ) : null}
            <div className="mt-6 flex justify-end gap-2">
              <Button
                variant="ghost"
                disabled={Boolean(renewingItemId || boostingItemId)}
                onClick={() => setListingFeeAction(null)}
              >
                Hủy
              </Button>
              <Button
                disabled={Boolean(renewingItemId || boostingItemId) || listingFeesLoading || !listingFees}
                onClick={() => void confirmListingFeeAction()}
              >
                {renewingItemId || boostingItemId
                  ? 'Đang xử lý...'
                  : listingFeeAction.type === 'renew' ? 'Gia hạn' : 'Đẩy bài'}
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
                onClick={() => void removeApiItem(removeConfirmId)}
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

function EditItemForm({
  item,
  categories,
  areas,
  disabled,
  onCancel,
  onSave,
}: {
  item: ItemResponse;
  categories: CategoryResponse[];
  areas: AreaOption[];
  disabled: boolean;
  onCancel: () => void;
  onSave: (input: UpdateItemRequest) => Promise<void>;
}) {
  const [offerType, setOfferType] = useState<OfferType>(item.offerType);
  const [title, setTitle] = useState(item.title);
  const [description, setDescription] = useState(item.description);
  const [desiredItem, setDesiredItem] = useState(item.desiredItem ?? '');
  const [categoryId, setCategoryId] = useState(item.categoryId);
  const [condition, setCondition] = useState<ApiCondition>(item.condition);
  const [defectNote, setDefectNote] = useState(item.defectNote ?? '');
  const [brand, setBrand] = useState(item.brand ?? '');
  const [areaId, setAreaId] = useState(item.areaId);
  const [images, setImages] = useState(item.images);
  const [newImages, setNewImages] = useState<Array<{ file: File; preview: string }>>([]);
  const [defectImages, setDefectImages] = useState(item.defectImages ?? []);
  const [newDefectImages, setNewDefectImages] = useState<Array<{ file: File; preview: string }>>([]);
  const [operatingStatus, setOperatingStatus] = useState<OperatingStatus | ''>(item.operatingStatus ?? '');
  const [categoryAttributes, setCategoryAttributes] = useState<CategoryAttributeResponse[]>([]);
  const [attributesLoading, setAttributesLoading] = useState(false);
  const [attributesError, setAttributesError] = useState('');
  const [errors, setErrors] = useState<EditFormErrors>({});
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState('');
  const totalImageCount = images.length + newImages.length;
  const totalDefectImageCount = defectImages.length + newDefectImages.length;
  const sortedCategoryAttributes = [...categoryAttributes].sort(
    (left, right) => left.sortOrder - right.sortOrder || left.id - right.id,
  );
  const operatingStatusAttribute = sortedCategoryAttributes.find(
    (attribute) =>
      attribute.dataType === 'SELECT' &&
      ['NORMAL', 'PARTIAL', 'NOT_WORKING'].every((option) => attribute.options.includes(option)),
  );
  const operatingStatusRequired = operatingStatusAttribute?.required ?? false;
  const operatingStatusOptions = operatingStatusAttribute?.options.filter(isOperatingStatus) ?? [];
  const formDisabled = disabled || uploading;

  useEffect(() => {
    let active = true;
    setAttributesLoading(true);
    setAttributesError('');
    catalogApi.categoryAttributes(categoryId)
      .then((response) => {
        if (active) setCategoryAttributes(response);
      })
      .catch(() => {
        if (!active) return;
        setCategoryAttributes([]);
        setAttributesError('Không tải được thông tin bổ sung của danh mục.');
      })
      .finally(() => {
        if (active) setAttributesLoading(false);
      });
    return () => {
      active = false;
    };
  }, [categoryId]);

  const validate = () => {
    const nextErrors: EditFormErrors = {};
    if (!title.trim()) nextErrors.title = 'Vui lòng nhập tiêu đề.';
    else if (title.trim().length > 150) nextErrors.title = 'Tiêu đề tối đa 150 ký tự.';
    if (!description.trim()) nextErrors.description = 'Vui lòng nhập mô tả.';
    if (offerType === 'SWAP' && !desiredItem.trim()) {
      nextErrors.desiredItem = 'Vui lòng nhập món đồ mong muốn.';
    }
    if (brand.trim().length > 80) nextErrors.brand = 'Thương hiệu tối đa 80 ký tự.';
    if (totalImageCount < ITEM_IMAGES_MIN) {
      nextErrors.images = `Vui lòng chọn ít nhất ${ITEM_IMAGES_MIN} ảnh.`;
    } else if (totalImageCount > ITEM_IMAGES_MAX) {
      nextErrors.images = `Chỉ được chọn tối đa ${ITEM_IMAGES_MAX} ảnh.`;
    }
    if (condition === 'DEFECTIVE' && !defectNote.trim()) {
      nextErrors.defectNote = 'Vui lòng mô tả lỗi của món đồ.';
    }
    if (condition === 'DEFECTIVE' && totalDefectImageCount === 0) {
      nextErrors.defectImages = 'Vui lòng chọn ít nhất một ảnh lỗi.';
    }
    if (operatingStatusRequired && !operatingStatus) {
      nextErrors.operatingStatus = 'Vui lòng chọn trạng thái hoạt động.';
    }
    setErrors(nextErrors);
    return Object.keys(nextErrors).length === 0;
  };

  const addImages = async (files: File[]) => {
    setUploadError('');
    if (files.some((file) => !file.type.startsWith('image/'))) {
      setErrors((current) => ({ ...current, images: 'Vui lòng chỉ chọn tệp hình ảnh.' }));
      return;
    }
    if (totalImageCount + files.length > ITEM_IMAGES_MAX) {
      setErrors((current) => ({ ...current, images: `Chỉ được chọn tối đa ${ITEM_IMAGES_MAX} ảnh.` }));
      return;
    }
    try {
      const previews = await Promise.all(files.map(fileToDataUrl));
      setNewImages((current) => [...current, ...files.map((file, index) => ({ file, preview: previews[index] }))]);
      const nextCount = totalImageCount + files.length;
      setErrors((current) => ({
        ...current,
        images: nextCount < ITEM_IMAGES_MIN ? `Vui lòng chọn ít nhất ${ITEM_IMAGES_MIN} ảnh.` : undefined,
      }));
    } catch {
      setUploadError('Không đọc được ảnh đã chọn. Vui lòng chọn lại.');
    }
  };

  const addDefectImages = async (files: File[]) => {
    setUploadError('');
    if (files.some((file) => !file.type.startsWith('image/'))) {
      setErrors((current) => ({ ...current, defectImages: 'Vui lòng chỉ chọn tệp hình ảnh.' }));
      return;
    }
    try {
      const previews = await Promise.all(files.map(fileToDataUrl));
      setNewDefectImages((current) => [
        ...current,
        ...files.map((file, index) => ({ file, preview: previews[index] })),
      ]);
      setErrors((current) => ({ ...current, defectImages: undefined }));
    } catch {
      setUploadError('Không đọc được ảnh lỗi đã chọn. Vui lòng chọn lại.');
    }
  };

  const submit = async () => {
    if (formDisabled || !validate()) return;
    setUploading(true);
    setUploadError('');
    try {
      const [uploadedImages, uploadedDefectImages] = await Promise.all([
        Promise.all(newImages.map(async ({ file }) => (await mediaApi.upload(file)).url)),
        condition === 'DEFECTIVE'
          ? Promise.all(newDefectImages.map(async ({ file }) => (await mediaApi.upload(file)).url))
          : Promise.resolve([]),
      ]);
      await onSave({
        categoryId,
        areaId,
        offerType,
        title: title.trim(),
        description: description.trim(),
        desiredItem: offerType === 'SWAP' ? desiredItem.trim() : null,
        condition,
        defectNote: condition === 'DEFECTIVE' ? defectNote.trim() : null,
        brand: brand.trim() || null,
        images: [...images, ...uploadedImages],
        defectImages: condition === 'DEFECTIVE' ? [...defectImages, ...uploadedDefectImages] : [],
        operatingStatus: operatingStatusRequired
          ? operatingStatus || null
          : categoryId === item.categoryId
            ? item.operatingStatus
            : null,
      });
    } catch (caught) {
      const apiError = caught as ApiError;
      setUploadError(apiError.message || 'Không tải được ảnh lên. Vui lòng thử lại.');
    } finally {
      setUploading(false);
    }
  };

  return (
    <form
      className="mt-5 space-y-4"
      onSubmit={(event) => {
        event.preventDefault();
        void submit();
      }}
    >
      <div>
        <Select label="Hình thức" value={offerType} disabled={formDisabled} onChange={(event) => setOfferType(event.target.value as OfferType)}>
          <option value="GIVE">Cho tặng</option>
          <option value="SWAP" disabled={item.offerType === 'GIVE'}>Trao đổi</option>
        </Select>
        {item.offerType === 'GIVE' ? <p className="mt-1 text-xs text-text-muted">Muốn trao đổi thì đăng bài mới.</p> : null}
      </div>
      {offerType === 'SWAP' ? (
        <Field label="Món muốn đổi" value={desiredItem} disabled={formDisabled} error={errors.desiredItem} onChange={(event) => setDesiredItem(event.target.value)} />
      ) : null}
      <Field label="Tên món đồ" value={title} disabled={formDisabled} error={errors.title} onChange={(event) => setTitle(event.target.value)} />
      <Select label="Danh mục" value={categoryId} disabled={formDisabled} onChange={(event) => setCategoryId(Number(event.target.value))}>
        {!categories.some((category) => category.id === categoryId) ? <option value={categoryId}>Danh mục hiện tại</option> : null}
        {[...categories]
          .sort((left, right) => left.sortOrder - right.sortOrder || left.name.localeCompare(right.name, 'vi'))
          .map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
      </Select>
      <Select label="Tình trạng" value={condition} disabled={formDisabled} onChange={(event) => setCondition(event.target.value as ApiCondition)}>
        {CONDITIONS.map((value) => <option key={value} value={value}>{conditionLabel[value]}</option>)}
      </Select>
      {condition === 'DEFECTIVE' ? (
        <>
          <TextArea label="Ghi chú lỗi" value={defectNote} disabled={formDisabled} error={errors.defectNote} onChange={(event) => setDefectNote(event.target.value)} />
          <ImageEditor
            label="Ảnh lỗi"
            hint="Cần ít nhất một ảnh thể hiện rõ lỗi."
            images={defectImages}
            newImages={newDefectImages}
            disabled={formDisabled}
            error={errors.defectImages}
            onAdd={addDefectImages}
            onRemoveExisting={(index) => {
              setDefectImages((current) => current.filter((_, itemIndex) => itemIndex !== index));
              if (totalDefectImageCount - 1 === 0) setErrors((current) => ({ ...current, defectImages: 'Vui lòng chọn ít nhất một ảnh lỗi.' }));
            }}
            onRemoveNew={(index) => {
              setNewDefectImages((current) => current.filter((_, itemIndex) => itemIndex !== index));
              if (totalDefectImageCount - 1 === 0) setErrors((current) => ({ ...current, defectImages: 'Vui lòng chọn ít nhất một ảnh lỗi.' }));
            }}
          />
        </>
      ) : null}
      <Field label="Thương hiệu (không bắt buộc)" value={brand} disabled={formDisabled} error={errors.brand} onChange={(event) => setBrand(event.target.value)} />
      <Select label="Khu vực" value={areaId} disabled={formDisabled} onChange={(event) => setAreaId(Number(event.target.value))}>
        {!areas.some((area) => area.id === areaId) ? <option value={areaId}>Khu vực hiện tại</option> : null}
        {areas.map((area) => <option key={area.id} value={area.id}>{area.label}</option>)}
      </Select>
      <TextArea label="Mô tả" value={description} disabled={formDisabled} error={errors.description} onChange={(event) => setDescription(event.target.value)} />
      {attributesLoading ? <p className="text-xs text-text-muted">Đang tải thuộc tính danh mục...</p> : null}
      {attributesError ? <p className="text-xs text-error">{attributesError}</p> : null}
      {!attributesLoading && !attributesError && sortedCategoryAttributes.length ? (
        <div className="flex flex-wrap gap-2">
          {sortedCategoryAttributes.map((attribute) => (
            <span key={attribute.id} className="rounded-full bg-surface-low px-3 py-1 text-xs ring-1 ring-border">
              {attribute.name}
              {attribute.required ? ' · bắt buộc' : ''}
              {attribute.options.length ? ` · ${attribute.options.join(', ')}` : ` · ${attribute.dataType}`}
            </span>
          ))}
        </div>
      ) : null}
      {operatingStatusAttribute ? (
        <div>
          <Select label={`${operatingStatusAttribute.name}${operatingStatusRequired ? '' : ' (không bắt buộc)'}`} value={operatingStatus} disabled={formDisabled} onChange={(event) => setOperatingStatus(event.target.value as OperatingStatus | '')}>
            <option value="">Chọn trạng thái hoạt động</option>
            {operatingStatusOptions.map((option) => (
              <option key={option} value={option}>{OPERATING_STATUS_LABELS[option]}</option>
            ))}
          </Select>
          {errors.operatingStatus ? <span className="mt-1 block text-xs text-error">{errors.operatingStatus}</span> : null}
        </div>
      ) : null}
      <ImageEditor
        label="Ảnh"
        hint={`Từ ${ITEM_IMAGES_MIN} đến ${ITEM_IMAGES_MAX} ảnh.`}
        images={images}
        newImages={newImages}
        disabled={formDisabled}
        error={errors.images}
        onAdd={addImages}
        onRemoveExisting={(index) => {
          setImages((current) => current.filter((_, itemIndex) => itemIndex !== index));
          const nextCount = totalImageCount - 1;
          setErrors((current) => ({ ...current, images: nextCount < ITEM_IMAGES_MIN ? `Vui lòng chọn ít nhất ${ITEM_IMAGES_MIN} ảnh.` : undefined }));
        }}
        onRemoveNew={(index) => {
          setNewImages((current) => current.filter((_, itemIndex) => itemIndex !== index));
          const nextCount = totalImageCount - 1;
          setErrors((current) => ({ ...current, images: nextCount < ITEM_IMAGES_MIN ? `Vui lòng chọn ít nhất ${ITEM_IMAGES_MIN} ảnh.` : undefined }));
        }}
      />
      {uploadError ? <Alert tone="error">{uploadError}</Alert> : null}
      <div className="flex justify-end gap-2 pt-2">
        <Button type="button" variant="ghost" disabled={formDisabled} onClick={onCancel}>Hủy</Button>
        <Button type="submit" disabled={formDisabled || attributesLoading}>{uploading || disabled ? 'Đang lưu...' : 'Lưu thay đổi'}</Button>
      </div>
    </form>
  );
}

function ImageEditor({
  label,
  hint,
  images,
  newImages,
  disabled,
  error,
  onAdd,
  onRemoveExisting,
  onRemoveNew,
}: {
  label: string;
  hint: string;
  images: string[];
  newImages: Array<{ file: File; preview: string }>;
  disabled: boolean;
  error?: string;
  onAdd: (files: File[]) => Promise<void>;
  onRemoveExisting: (index: number) => void;
  onRemoveNew: (index: number) => void;
}) {
  return (
    <div>
      <label className={`block rounded-lg border border-dashed p-4 text-sm ${error ? 'border-error' : 'border-border'}`}>
        <span className="font-semibold">{label}</span>
        <span className="mt-1 block text-xs text-text-muted">{hint}</span>
        <input
          className="mt-3 block w-full text-xs"
          type="file"
          accept="image/jpeg,image/png,image/webp"
          multiple
          disabled={disabled}
          onChange={(event) => {
            const files = Array.from(event.target.files ?? []);
            event.currentTarget.value = '';
            void onAdd(files);
          }}
        />
      </label>
      {error ? <span className="mt-1.5 block text-xs text-error">{error}</span> : null}
      {images.length || newImages.length ? (
        <div className="mt-3 grid grid-cols-4 gap-2">
          {images.map((image, index) => (
            <EditableImage
              key={`${image}-${index}`}
              src={image}
              alt={`${label} ${index + 1}`}
              disabled={disabled}
              onRemove={() => onRemoveExisting(index)}
            />
          ))}
          {newImages.map(({ preview }, index) => (
            <EditableImage
              key={`${preview.slice(-16)}-${index}`}
              src={preview}
              alt={`${label} ${images.length + index + 1}`}
              disabled={disabled}
              onRemove={() => onRemoveNew(index)}
            />
          ))}
        </div>
      ) : null}
    </div>
  );
}

function EditableImage({
  src,
  alt,
  disabled,
  onRemove,
}: {
  src: string;
  alt: string;
  disabled: boolean;
  onRemove: () => void;
}) {
  return (
    <div className="relative">
      <ItemImage src={src} alt={alt} className="aspect-square w-full rounded-md object-cover" />
      <button
        type="button"
        aria-label="Xóa ảnh"
        disabled={disabled}
        onClick={onRemove}
        className="absolute right-1 top-1 grid size-6 place-items-center rounded-full bg-white text-error shadow-sm"
      >
        <Icon name="close" className="size-3" />
      </button>
    </div>
  );
}

// Lấy chữ cái đầu của 2 từ cuối trong họ tên, ví dụ "Nguyễn Văn B" → "VB".
function nameInitials(name: string): string {
  const words = name.trim().split(/\s+/);
  const lastTwoWords = words.slice(-2);
  return lastTwoWords.map((word) => word.charAt(0)).join('').toUpperCase();
}

function apiDisplayUser(userId: string, name?: string): User {
  const trimmedName = name?.trim() ?? '';
  return {
    id: userId,
    username: userId,
    password: '',
    name: trimmedName || 'Người dùng',
    email: '',
    phone: '',
    district: '',
    avatarInitials: trimmedName ? nameInitials(trimmedName) : '',
    totalCredit: 0,
    availableCredit: 0,
    holdCredit: 0,
    rewardPoints: 0,
    reputationStars: 5,
    rank: 'Thành viên',
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
  const currentUser = useAppSelector(selectCurrentUser);
  return (
    <div className="rounded-lg bg-white p-4 ring-1 ring-border/80">
      <h3 className="text-sm font-bold">Yêu cầu giao dịch</h3>
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
                    <Link to={`/requests/${request.id.replace(/^api_req_/, '')}`}>
                      <Button size="sm" variant="outline">Chi tiết giao dịch</Button>
                    </Link>
                    <Link to={`/items/${request.itemId}`}>
                      <Button size="sm" variant="outline" icon="eye">
                        Xem bài
                      </Button>
                    </Link>
                    {request.status === 'PENDING' && request.requesterId === currentUser?.id ? (
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
              apiDisplayUser(request.requesterId, request.requesterName);
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
                    {canChat(request.status) ? (
                      <Link to={`/requests/${request.id.replace(/^api_req_/, '')}`}>
                        <Button size="sm" variant="outline" icon="messages">
                          Nhắn tin
                        </Button>
                      </Link>
                    ) : allowAccept && request.status === 'PENDING' && !hasAcceptedRequest && !['IN_TRANSACTION', 'RESERVED'].includes(itemStatus) ? (
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
