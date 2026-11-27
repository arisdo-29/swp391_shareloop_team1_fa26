import { type ReactNode, useEffect, useMemo, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { useAppSelector } from '../../app/hooks';
import { selectCurrentUser, selectData } from '../../app/store';
import {
  Alert,
  Avatar,
  Skeleton,
  Button,
  ConditionBadge,
  Icon,
  type IconName,
  ItemImage,
  StatusBadge,
  TypeBadge,
} from '../../components/ui';
import { catalogApi } from '../../services/catalogApi';
import { itemApi } from '../../services/itemApi';
import { requestApi, toBackendUserId, toFeItemRequest } from '../../services/requestApi';
import { toAreaNameMap, toCategoryNameMap, toFeItem } from '../../services/mappers';
import type { Item, ItemRequest } from '../../types/domain';
import type { ApiError } from '../../services/http';
import { ReportDialog } from '../../components/ReportDialog';
import { ReputationBadge } from '../../components/ReputationBadge';

import { requestStatusLabel } from '../../utils/request';
import { userProfilePath } from '../../utils/chatMessages';
import { reputationBlockedMessage } from '../../utils/apiErrorMessages';
import { loginPath } from '../../utils/authRedirect';
import { productDetailBackLink } from '../../utils/productDetailNavigation';
import { findActiveSwapPair, isActiveRequestStatus } from '../../utils/swapRequest';
import { requestOfferLabel } from '../../utils/requestOffer';
import { usePublicConfig } from '../../utils/usePublicConfig';
import { formatReferencePrice, rankOfferedItems } from '../../utils/referencePrice';
import { OfferedItemOptions } from '../../components/OfferedItemOptions';
import { useRealtimeRefresh } from '../../utils/useRealtimeRefresh';

const DUPLICATE_SWAP_MESSAGE =
  'Hai món đồ này đã có yêu cầu trao đổi. Xem yêu cầu hiện có trước khi tiếp tục.';

export function ProductDetail() {
  const { id } = useParams();
  const data = useAppSelector(selectData);
  const user = useAppSelector(selectCurrentUser);
  const navigate = useNavigate();
  const location = useLocation();
  const { swap_similar_price_percent: similarPricePercent } = usePublicConfig();
  const [requestError, setRequestError] = useState('');
  const [requestSuccess, setRequestSuccess] = useState('');
  const [offeredItemId, setOfferedItemId] = useState('');
  // Bài Trao đổi: người dùng đã chủ động chọn "Chỉ xin nhận, không kèm món".
  const [receiveOnlyChosen, setReceiveOnlyChosen] = useState(false);
  const [apiItem, setApiItem] = useState<Item | null>(null);
  const [apiOfferedItems, setApiOfferedItems] = useState<Item[]>([]);
  const [apiSentRequests, setApiSentRequests] = useState<ItemRequest[]>([]);
  const [loadingItem, setLoadingItem] = useState(false);
  const [itemError, setItemError] = useState('');
  const [itemReloadKey, setItemReloadKey] = useState(0);
  useRealtimeRefresh(['ITEM', 'CATALOG', 'REQUEST'], (update) => {
    if (update.kind === 'READY' || update.resourceId === Number(id)) {
      setItemReloadKey((value) => value + 1);
    }
  });
  const [requestSubmitting, setRequestSubmitting] = useState(false);
  const [duplicateSwapRequest, setDuplicateSwapRequest] = useState<ItemRequest | null>(null);
  const [selectedImageIndex, setSelectedImageIndex] = useState(0);
  const [reportOpen, setReportOpen] = useState(false);
  const [reportNotice, setReportNotice] = useState('');
  const isApiId = Boolean(id && /^\d+$/.test(id));
  const item = apiItem;
  const currentUserId = toBackendUserId(user?.id);
  const backLink = productDetailBackLink(location.search);

  useEffect(() => {
    if (!id || !/^\d+$/.test(id)) return;
    let active = true;
    setLoadingItem(true);
    setItemError('');
    itemApi.detail(Number(id))
      .then(async (detail) => {
        const [category, areas] = await Promise.all([catalogApi.category(detail.categoryId), catalogApi.areas()]);
        if (!active) return;
        setSelectedImageIndex(0);
        setApiItem(toFeItem(detail, new Map([[category.id, category.name]]), toAreaNameMap(areas)));
      })
      .catch((caught) => {
        if (!active) return;
        const apiError = caught as ApiError;
        setApiItem(null);
        setItemError(apiError.message || 'Không tải được chi tiết bài đăng.');
      })
      .finally(() => {
        if (active) setLoadingItem(false);
      });
    return () => {
      active = false;
    };
  }, [id, itemReloadKey]);

  useEffect(() => {
    if (!isApiId) return;
    if (!currentUserId) {
      setApiOfferedItems([]);
      setApiSentRequests([]);
      return;
    }
    let active = true;
    Promise.all([catalogApi.categories(), catalogApi.areas(), itemApi.mine(), requestApi.mine(currentUserId)])
      .then(([categories, areas, mine, requests]) => {
        if (!active) return;
        setApiOfferedItems(mine.map((entry) => toFeItem(entry, toCategoryNameMap(categories), toAreaNameMap(areas))));
        setApiSentRequests(requests.map((entry) => toFeItemRequest(entry, user?.id)));
      })
      .catch(() => {
        if (active) setApiOfferedItems([]);
        if (active) setApiSentRequests([]);
      });
    return () => {
      active = false;
    };
  }, [currentUserId, isApiId, user?.id]);

  const activeTransactionItemIds = useMemo(() => {
    const ids = new Set<string>();
    data.transactions
      .filter((tx) => !['COMPLETED', 'CANCELLED', 'DISPUTED'].includes(tx.status))
      .forEach((tx) => {
        ids.add(tx.itemId);
        if (tx.offeredItemId) ids.add(tx.offeredItemId);
        if (tx.sourceItemId) ids.add(tx.sourceItemId);
      });
    return ids;
  }, [data.transactions]);

  if (loadingItem)
    return (
      <div className="page-shell">
        {itemError ? (
          <Alert tone="warning">{itemError}</Alert>
        ) : (
          <div
            role="status"
            aria-label="Đang tải chi tiết bài đăng"
            className="grid grid-cols-1 gap-8 lg:grid-cols-[minmax(0,1fr)_360px]"
          >
            <div className="space-y-4">
              <Skeleton className="aspect-[4/3] w-full" />
              <Skeleton className="h-8 w-2/3" />
              <Skeleton className="h-4 w-full" />
              <Skeleton className="h-4 w-5/6" />
            </div>
            <Skeleton className="h-64 w-full" />
          </div>
        )}
      </div>
    );

  if (isApiId && itemError)
    return (
      <div className="page-shell space-y-4">
        <Alert tone="error">{itemError}</Alert>
        <Button variant="outline" onClick={() => setItemReloadKey((key) => key + 1)}>
          Thử tải lại
        </Button>
      </div>
    );

  if (!item)
    return (
      <div className="page-shell">
        <Alert tone="error">Món đồ này không tồn tại hoặc đã được gỡ.</Alert>
      </div>
    );

  const own = user?.id === item.ownerId;
  const isDonorLocked = Boolean(item.donorLocked);
  // Bài đã giao dịch thành công: chỉ xem. Dựa vào cờ requestable backend trả, không tự suy từ trạng thái.
  const isTradedReadOnly = item.status === 'TRADED' && item.requestable === false;
  // Bài đã chọn người khác: người từng gửi yêu cầu vẫn xem lại được bài nhưng không gửi yêu cầu mới.
  const isReservedReadOnly = item.status === 'RESERVED' && item.requestable === false && !own;
  const ownerProfileId = toBackendUserId(item.ownerId);
  const activeListing =
    (item.status === 'approved' || item.status === 'APPROVED') &&
    item.requestable !== false &&
    !isDonorLocked &&
    (!item.expiresAt || new Date(item.expiresAt).getTime() >= Date.now());
  const existingRequest = user
    ? apiSentRequests.find(
        (request) =>
          request.itemId === item.id &&
          request.requesterId === user.id &&
          isActiveRequestStatus(request.status),
      )
    : undefined;
  const eligibleOfferedItems = user
    ? apiOfferedItems.filter(
        (entry) =>
          entry.ownerId === user.id &&
          entry.id !== item.id &&
          (entry.status === 'approved' || entry.status === 'APPROVED') &&
          (!entry.expiresAt || new Date(entry.expiresAt).getTime() >= Date.now()) &&
          !entry.hidden &&
          !activeTransactionItemIds.has(entry.id),
      )
    : [];

  const swapOffers = rankOfferedItems(
    eligibleOfferedItems,
    item.type === 'trade' ? item.referencePrice : undefined,
    similarPricePercent,
  );

  // Bài Cho: bấm lại món đang chọn để bỏ kèm món. Bài Trao đổi: chọn món thì bỏ lựa chọn "Chỉ xin nhận".
  const chooseOfferedItem = (itemId: string) => {
    const isUnselectingGiftItem = item.type === 'gift' && offeredItemId === itemId;
    setOfferedItemId(isUnselectingGiftItem ? '' : itemId);
    setReceiveOnlyChosen(false);
    setDuplicateSwapRequest(null);
    setRequestError('');
  };

  const chooseReceiveOnly = () => {
    setOfferedItemId('');
    setReceiveOnlyChosen(true);
    setDuplicateSwapRequest(null);
    setRequestError('');
  };

  const submitButtonLabel =
    item.type === 'gift' || receiveOnlyChosen ? 'Gửi yêu cầu, miễn phí' : 'Gửi đề nghị, miễn phí';

  // Kiểm tra giống backend trước khi gửi để báo lỗi sớm; backend vẫn là nơi quyết định cuối cùng.
  const submitRequest = async () => {
    setRequestError('');
    setRequestSuccess('');
    setDuplicateSwapRequest(null);
    if (!user) {
      navigate(loginPath(`${location.pathname}${location.search}${location.hash}`));
      return;
    }
    if (!currentUserId) {
      setRequestError('Vui lòng đăng nhập bằng tài khoản thật để gửi yêu cầu.');
      return;
    }
    if (own || !activeListing || requestSubmitting) return;
    if (user.reputationStars <= 0) {
      setRequestError('Uy tín của bạn đang ở mức 0 nên hiện không thể gửi yêu cầu.');
      return;
    }
    // Bài Trao đổi bắt chọn rõ một món hoặc "Chỉ xin nhận" để người dùng không gửi nhầm loại yêu cầu.
    if (item.type === 'trade' && !offeredItemId && !receiveOnlyChosen) {
      setRequestError('Vui lòng chọn một món để trao đổi hoặc chọn "Chỉ xin nhận, không kèm món".');
      return;
    }
    const directSwapRequest = offeredItemId
      ? findActiveSwapPair(apiSentRequests, item.id, offeredItemId)
      : undefined;
    if (directSwapRequest) {
      setDuplicateSwapRequest(directSwapRequest);
      setRequestError(DUPLICATE_SWAP_MESSAGE);
      return;
    }
    if (existingRequest) {
      setRequestError(`Bạn đã gửi yêu cầu cho món đồ này. Trạng thái hiện tại: ${requestStatusLabel[existingRequest.status]}.`);
      return;
    }
    setRequestSubmitting(true);
    try {
      if (offeredItemId) {
        let incomingRequests: ItemRequest[] = [];
        try {
          const response = await requestApi.forItem(Number(offeredItemId), currentUserId);
          incomingRequests = response.map((request) => toFeItemRequest(request));
        } catch {
          // Không chặn khi không thể xác minh chiều ngược lại bằng API hiện có.
        }
        const reverseSwapRequest = findActiveSwapPair(
          incomingRequests,
          item.id,
          offeredItemId,
        );
        if (reverseSwapRequest) {
          setDuplicateSwapRequest(reverseSwapRequest);
          setRequestError(DUPLICATE_SWAP_MESSAGE);
          return;
        }
      }
      const request = await requestApi.create(
        Number(item.id),
        currentUserId,
        offeredItemId ? Number(offeredItemId) : undefined,
      );
      const sentRequest = toFeItemRequest(request, user.id);
      setApiSentRequests((current) => [sentRequest, ...current]);
      setOfferedItemId('');
      setReceiveOnlyChosen(false);
      // Thông báo theo chú thích thật của yêu cầu (có kèm món hay không), không theo loại bài.
      setRequestSuccess(
        requestOfferLabel(sentRequest) === 'Trao đổi' ? 'Đã gửi đề nghị trao đổi.' : 'Đã gửi yêu cầu xin nhận.',
      );
    } catch (caught) {
      const apiError = caught as ApiError;
      setRequestError(
        reputationBlockedMessage(apiError.code) ?? (apiError.message || 'Không gửi được yêu cầu. Vui lòng thử lại.'),
      );
    } finally {
      setRequestSubmitting(false);
    }
  };

  const cancelApiRequest = async (requestId: string) => {
    if (!currentUserId || requestSubmitting) return;
    const numericRequestId = Number(requestId.replace(/^api_req_/, ''));
    if (!Number.isSafeInteger(numericRequestId)) return;
    setRequestSubmitting(true);
    setRequestError('');
    try {
      const request = await requestApi.cancel(numericRequestId, currentUserId);
      setApiSentRequests((current) =>
        current.map((entry) => (entry.id === requestId ? toFeItemRequest(request, user?.id) : entry)),
      );
      setRequestSuccess('Đã hủy yêu cầu.');
    } catch {
      setRequestError('Không hủy được yêu cầu. Vui lòng thử lại.');
    } finally {
      setRequestSubmitting(false);
    }
  };

  const images = item.images.length ? item.images : [''];
  const operatingLabel = item.operatingStatus
    ? { NORMAL: 'Hoạt động bình thường', PARTIAL: 'Hoạt động một phần', NOT_WORKING: 'Không hoạt động' }[item.operatingStatus]
    : '';

  return (
    <div className="page-shell pt-6">
      <Link to={backLink.target} className="inline-flex min-h-11 items-center text-sm font-semibold text-link hover:text-secondary">
        {backLink.label}
      </Link>
      <div className="mt-2 grid grid-cols-1 items-start gap-8 lg:grid-cols-[minmax(0,1fr)_380px]">
        <article className="min-w-0 space-y-4">
          <div className="grid gap-3 md:grid-cols-[88px_minmax(0,1fr)]">
            {images.length > 1 ? (
              <div className="order-2 flex gap-2 overflow-x-auto md:order-1 md:flex-col md:overflow-visible">
                {images.map((image, index) => (
                  <button
                    key={`${image}-${index}`}
                    type="button"
                    aria-label={`Xem ảnh ${index + 1}`}
                    aria-pressed={selectedImageIndex === index}
                    onClick={() => setSelectedImageIndex(index)}
                    className={`shrink-0 overflow-hidden rounded-md ${selectedImageIndex === index ? 'ring-2 ring-primary' : 'ring-1 ring-border'}`}
                  >
                    <ItemImage src={image} alt={`${item.title} - ảnh ${index + 1}`} className="h-16 w-20 object-cover md:w-full" />
                  </button>
                ))}
              </div>
            ) : null}
            <div className={`order-1 overflow-hidden rounded-xl bg-surface-container md:order-2 ${images.length > 1 ? '' : 'md:col-span-2'}`}>
              <ItemImage
                src={images[selectedImageIndex] ?? images[0]}
                alt={item.title}
                className="aspect-[4/3] w-full object-cover md:aspect-auto md:h-[440px]"
              />
            </div>
          </div>

          <section className="space-y-4 rounded-detail border border-detail-border bg-white p-5">
            <div className="flex flex-wrap items-center gap-2.5">
              <span className="inline-flex w-fit rounded-sm border border-primary bg-primary-soft [&>span]:bg-transparent [&>span]:px-3 [&>span]:py-1.5 [&>span]:text-sm [&>span]:font-semibold [&>span]:text-primary">
                <TypeBadge type={item.type} />
              </span>
              <span className="inline-flex w-fit rounded-sm border border-info bg-info-soft [&>span]:bg-transparent [&>span]:px-3 [&>span]:py-1.5 [&>span]:text-sm [&>span]:font-semibold [&>span]:text-info">
                <ConditionBadge condition={item.condition} />
              </span>
              {isTradedReadOnly ? (
                <StatusBadge status="TRADED" label="Đã giao dịch" />
              ) : isReservedReadOnly && !existingRequest ? (
                <StatusBadge status="RESERVED" label="Đã có người nhận" />
              ) : !activeListing ? (
                <StatusBadge status={item.status} />
              ) : null}
            </div>
            <h1 className="break-words text-[26px] font-bold leading-tight sm:text-[28px]">
              {item.title}
            </h1>
            {item.referencePrice != null ? (
              <p className="inline-flex w-fit rounded-sm border border-warning bg-warning-soft px-3 py-1.5 text-lg font-bold tabular-nums text-warning">
                {formatReferencePrice(item.referencePrice)}
              </p>
            ) : (
              <p className="text-sm font-medium text-text-muted">
                {formatReferencePrice(item.referencePrice)}
              </p>
            )}
            {own && item.hidden && item.hiddenReason ? (
              <Alert tone="warning">{item.hiddenReason}</Alert>
            ) : null}
            {own && item.status === 'REJECTED' && item.rejectionReason ? (
              <Alert tone="warning">Bài bị từ chối. Lý do: {item.rejectionReason}. Sửa bài để gửi duyệt lại.</Alert>
            ) : null}
            <div className="grid grid-cols-1 gap-x-6 gap-y-4 border-t border-detail-border pt-4 sm:grid-cols-2">
              <ItemFact icon="district" label="Khu vực">
                {item.district}
              </ItemFact>
              <ItemFact icon="category" label="Danh mục">
                <span className="break-words">{item.category}</span>
                {item.brand ? (
                  <span className="mt-0.5 block text-xs text-text-muted">
                    Thương hiệu {item.brand}
                  </span>
                ) : null}
              </ItemFact>
              <ItemFact icon="calendarCheck" label="Ngày đăng">
                {new Date(item.postedAt).toLocaleDateString('vi-VN')}
              </ItemFact>
              <ItemFact icon="expiring" label="Hiển thị đến">
                {item.expiresAt
                  ? new Date(item.expiresAt).toLocaleDateString('vi-VN')
                  : '—'}
              </ItemFact>
            </div>
          </section>

          {item.tradeFor ? (
            <section className="rounded-detail border border-detail-exchange-border bg-detail-exchange p-5">
              <h2 className="flex items-center gap-2 text-sm font-bold text-primary">
                <Icon name="transactions" className="size-5 shrink-0" weight="bold" />
                Chủ bài muốn đổi lấy
              </h2>
              <p className="mt-3 break-words text-base font-bold text-text-primary">
                {item.tradeFor}
              </p>
              <p className="mt-3 border-t border-detail-exchange-border pt-3 text-sm leading-6 text-text-secondary">
                Gợi ý: nếu bạn có món khác, hãy ghi vào đề nghị để chủ bài cân nhắc.
              </p>
            </section>
          ) : null}

          <section
            aria-labelledby="item-description"
            className="rounded-detail border border-detail-border bg-white p-5"
          >
            <h2
              id="item-description"
              className="flex items-center gap-2 text-lg font-bold text-text-primary"
            >
              <Icon name="manageItems" className="size-5 shrink-0 text-primary" weight="bold" />
              Mô tả
            </h2>
            <div className="mt-3 border-t border-detail-border pt-3">
              <SafeMarkdownDescription text={item.description} />
            </div>
            {item.defectNote ? (
              <p className="mt-3 border-t border-detail-border pt-3 text-sm leading-6 text-text-secondary">
                <strong className="text-text-primary">Lưu ý tình trạng:</strong> {item.defectNote}
              </p>
            ) : null}
            {operatingLabel ? (
              <p className="mt-2 text-sm leading-6 text-text-secondary">
                <strong className="text-text-primary">Trạng thái hoạt động:</strong> {operatingLabel}
              </p>
            ) : null}
            {item.defectImages?.length ? (
              <div className="mt-3 grid grid-cols-3 gap-3 border-t border-detail-border pt-3 sm:grid-cols-4">
                {item.defectImages.map((image, index) => (
                  <ItemImage key={`${image}-${index}`} src={image} alt={`Ảnh lỗi ${index + 1}`} className="aspect-square rounded-md object-cover" />
                ))}
              </div>
            ) : null}
          </section>

          <section
            aria-labelledby="item-owner"
            className="rounded-detail border border-detail-border bg-white p-5"
          >
            <h2 id="item-owner" className="text-lg font-bold text-text-primary">
              Thông tin chủ bài
            </h2>
            <div className="mt-3 flex flex-col gap-4 border-t border-detail-border pt-4 sm:flex-row sm:items-center">
              <div className="flex min-w-0 flex-1 items-center gap-3">
                <Avatar
                  size="lg"
                  user={{ name: item.donorName || 'Người đăng', avatarInitials: initialsOf(item.donorName || '') }}
                />
                <div className="min-w-0 flex-1 space-y-1">
                  <p className="break-words text-base font-bold text-text-primary">
                    {item.donorName || 'Người đăng'}
                  </p>
                  <ReputationBadge trustStars={item.donorTrustStars} rank={item.donorRank} />
                </div>
              </div>
              {ownerProfileId ? (
                <Button
                  variant="outline"
                  className="w-full !border-exchange !text-primary sm:w-auto"
                  onClick={() => navigate(userProfilePath(ownerProfileId))}
                >
                  Xem hồ sơ
                </Button>
              ) : null}
            </div>
          </section>
        </article>

        <aside
          aria-label={item.type === 'gift' ? 'Xin món này' : 'Đề nghị trao đổi'}
          className="space-y-4 rounded-xl bg-white p-5 ring-1 ring-border/80 lg:sticky lg:top-24"
        >
          {own ? (
            <>
              <h2 className="text-lg font-bold">Đây là bài của bạn</h2>
              <p className="text-sm leading-6 text-text-secondary">Xem người xin và chọn người nhận ở trang Hoạt động.</p>
              <Link to="/activities">
                <Button className="w-full">Mở Hoạt động</Button>
              </Link>
            </>
          ) : isTradedReadOnly ? (
            <>
              <h2 className="text-lg font-bold">Đã giao dịch</h2>
              <p className="text-sm leading-6 text-text-secondary">
                Món này đã trao đổi xong nên không nhận yêu cầu mới. Bạn chỉ xem được chi tiết bài đăng.
              </p>
              <Link to="/browse">
                <Button variant="outline" className="w-full">Tìm món khác</Button>
              </Link>
            </>
          ) : existingRequest ? (
            <>
              <h2 className="text-lg font-bold">Bạn đã gửi yêu cầu</h2>
              <p className="text-sm leading-6 text-text-secondary">
                Trạng thái: <strong className="text-text-primary">{requestStatusLabel[existingRequest.status]}</strong>
              </p>
              {requestOfferLabel(existingRequest) === 'Trao đổi' ? (
                <>
                  <Alert tone="warning">{DUPLICATE_SWAP_MESSAGE}</Alert>
                  <Link to={`/requests/${existingRequest.id.replace(/^api_req_/, '')}`}>
                    <Button className="w-full" variant="outline">
                      Xem yêu cầu
                    </Button>
                  </Link>
                </>
              ) : existingRequest.status === 'RESERVED' ? (
                <Link to={`/requests/${existingRequest.id.replace(/^api_req_/, '')}`}>
                  <Button className="w-full">Mở cuộc trò chuyện</Button>
                </Link>
              ) : null}
              {existingRequest.status === 'PENDING' ? (
                <Button
                  className="w-full"
                  variant="outline"
                  disabled={requestSubmitting}
                  onClick={() => void cancelApiRequest(existingRequest.id)}
                >
                  Hủy yêu cầu
                </Button>
              ) : null}
            </>
          ) : isReservedReadOnly ? (
            <>
              <h2 className="text-lg font-bold">Đã có người nhận</h2>
              <p className="text-sm leading-6 text-text-secondary">
                Chủ bài đã chọn người nhận cho món này nên bài không nhận yêu cầu mới. Bạn chỉ xem được
                chi tiết bài đăng.
              </p>
              <Link to="/browse">
                <Button variant="outline" className="w-full">Tìm món khác</Button>
              </Link>
            </>
          ) : !activeListing ? (
            <>
              <h2 className="text-lg font-bold">Bài không còn nhận yêu cầu</h2>
              <p className="text-sm leading-6 text-text-secondary">
                {isDonorLocked
                  ? 'Tài khoản người đăng hiện đang bị tạm khóa nên bài đăng không nhận yêu cầu mới.'
                  : 'Món này đã có người nhận, hết hạn hoặc đang chờ duyệt.'}
              </p>
              <Link to="/browse">
                <Button variant="outline" className="w-full">Tìm món khác</Button>
              </Link>
            </>
          ) : (
            <>
              <h2 className="text-lg font-bold">{item.type === 'gift' ? 'Xin món này' : 'Đề nghị trao đổi'}</h2>
              {item.type === 'gift' && user && eligibleOfferedItems.length ? (
                <fieldset className="min-w-0 w-full max-w-full space-y-2">
                  <legend className="label">Kèm món của bạn để đề nghị trao đổi</legend>
                  <p className="text-xs leading-5 text-text-secondary">
                    Không bắt buộc. Nếu chủ bài chọn bạn, món đồ sẽ được trao đổi thay vì cho tặng.
                  </p>
                  <OfferedItemOptions
                    items={eligibleOfferedItems}
                    selectedItemId={offeredItemId}
                    onSelect={chooseOfferedItem}
                  />
                  {offeredItemId ? (
                    <button
                      type="button"
                      onClick={() => chooseOfferedItem(offeredItemId)}
                      className="inline-flex min-h-11 items-center text-sm font-semibold text-link hover:text-secondary"
                    >
                      Không kèm món
                    </button>
                  ) : null}
                </fieldset>
              ) : null}
              {item.type === 'trade' && user ? (
                <fieldset className="min-w-0 w-full max-w-full space-y-2">
                  <legend className="label">Chọn món bạn mang đổi</legend>
                  <OfferedItemOptions
                    items={swapOffers.items}
                    similarPriceIds={swapOffers.similarIds}
                    selectedItemId={offeredItemId}
                    onSelect={chooseOfferedItem}
                  />
                  <button
                    type="button"
                    aria-pressed={receiveOnlyChosen}
                    onClick={chooseReceiveOnly}
                    className={`box-border flex min-h-11 w-full items-center rounded-md border p-2.5 text-left text-sm font-semibold transition ${receiveOnlyChosen ? 'border-primary bg-primary-faint' : 'border-border hover:border-primary/40'}`}
                  >
                    Chỉ xin nhận, không kèm món
                  </button>
                  <p className="text-xs leading-5 text-text-secondary">Chủ bài có thể đồng ý cho tặng thay vì trao đổi.</p>
                  {!eligibleOfferedItems.length ? (
                    <p className="rounded-md bg-warning-soft p-3 text-xs leading-5 text-warning">
                      Bạn chưa có món đã duyệt, còn hoạt động và chưa nằm trong giao dịch khác để trao đổi.
                    </p>
                  ) : null}
                  <Link
                    to="/post"
                    className="flex h-[42px] w-full items-center justify-center rounded-action border border-exchange bg-white text-sm font-semibold text-primary transition-colors hover:bg-primary-soft"
                  >
                    Đăng món mới để đổi
                  </Link>
                </fieldset>
              ) : null}
              <Button
                size="lg"
                className="!mt-3 w-full"
                disabled={requestSubmitting}
                onClick={() => void submitRequest()}
              >
                {requestSubmitting
                  ? 'Đang gửi...'
                  : !user
                    ? 'Đăng nhập để gửi yêu cầu'
                    : submitButtonLabel}
              </Button>
              <ul className="list-disc space-y-1 pl-5 text-xs leading-5 text-text-secondary">
                <li>Chat mở khi chủ bài chọn bạn. Bạn có thể hủy yêu cầu tới lúc đó.</li>
                <li>Số điện thoại chỉ hiện khi hai bên đã chốt lịch hẹn.</li>
              </ul>
            </>
          )}
          {requestSuccess ? <Alert tone="success">{requestSuccess}</Alert> : null}
          {requestError ? <Alert tone="warning">{requestError}</Alert> : null}
          {duplicateSwapRequest ? (
            <Link to={`/requests/${duplicateSwapRequest.id.replace(/^api_req_/, '')}`}>
              <Button className="w-full" variant="outline">
                Xem yêu cầu
              </Button>
            </Link>
          ) : null}
          {reportNotice ? <Alert tone="success">{reportNotice}</Alert> : null}
          {currentUserId && !own ? (
            <Button
              type="button"
              variant="warning"
              className="w-full"
              onClick={() => {
                setReportNotice('');
                setReportOpen(true);
              }}
            >
              Báo cáo bài đăng
            </Button>
          ) : null}
        </aside>
      </div>

      {reportOpen && currentUserId && !own ? (
        <ReportDialog
          target={{ targetType: 'ITEM', itemId: Number(item.id) }}
          contextLabel={`Báo cáo bài “${item.title}”`}
          onClose={() => setReportOpen(false)}
          onSuccess={() => {
            setReportOpen(false);
            setReportNotice('Báo cáo đã được gửi đến quản trị viên để xem xét.');
          }}
        />
      ) : null}
    </div>
  );
}

function ItemFact({
  icon,
  label,
  children,
}: {
  icon: IconName;
  label: string;
  children: ReactNode;
}) {
  return (
    <div className="flex min-w-0 items-start gap-2.5">
      <Icon name={icon} className="mt-0.5 size-4 shrink-0 text-primary" weight="bold" />
      <div className="min-w-0">
        <p className="text-xs font-semibold text-text-muted">{label}</p>
        <div className="mt-0.5 text-sm font-medium leading-5 text-text-primary">{children}</div>
      </div>
    </div>
  );
}

function SafeMarkdownDescription({ text }: { text: string }) {
  const lines = text.split(/\r?\n/);
  const blocks: ReactNode[] = [];
  let index = 0;

  while (index < lines.length) {
    if (!lines[index]?.trim()) {
      index += 1;
      continue;
    }

    const unorderedMatch = lines[index]?.match(/^\s*[-*+]\s+(.+)$/);
    if (unorderedMatch) {
      const items: string[] = [];
      const blockIndex = index;
      while (index < lines.length) {
        const match = lines[index]?.match(/^\s*[-*+]\s+(.+)$/);
        if (!match) break;
        items.push(match[1]);
        index += 1;
      }
      blocks.push(
        <ul key={`unordered-${blockIndex}`} className="list-disc space-y-1 pl-5">
          {items.map((item, itemIndex) => (
            <li key={`${blockIndex}-${itemIndex}`}>{renderInlineMarkdown(item)}</li>
          ))}
        </ul>,
      );
      continue;
    }

    const orderedMatch = lines[index]?.match(/^\s*\d+[.)]\s+(.+)$/);
    if (orderedMatch) {
      const items: string[] = [];
      const blockIndex = index;
      while (index < lines.length) {
        const match = lines[index]?.match(/^\s*\d+[.)]\s+(.+)$/);
        if (!match) break;
        items.push(match[1]);
        index += 1;
      }
      blocks.push(
        <ol key={`ordered-${blockIndex}`} className="list-decimal space-y-1 pl-5">
          {items.map((item, itemIndex) => (
            <li key={`${blockIndex}-${itemIndex}`}>{renderInlineMarkdown(item)}</li>
          ))}
        </ol>,
      );
      continue;
    }

    const paragraphLines: string[] = [];
    const blockIndex = index;
    while (
      index < lines.length &&
      lines[index]?.trim() &&
      !lines[index]?.match(/^\s*[-*+]\s+(.+)$/) &&
      !lines[index]?.match(/^\s*\d+[.)]\s+(.+)$/)
    ) {
      paragraphLines.push(lines[index]);
      index += 1;
    }
    blocks.push(
      <p key={`paragraph-${blockIndex}`} className="whitespace-pre-line">
        {renderInlineMarkdown(paragraphLines.join('\n'))}
      </p>,
    );
  }

  return <div className="space-y-2 text-[15px] leading-[1.7] text-text-primary">{blocks}</div>;
}

function renderInlineMarkdown(text: string) {
  return text.split(/(\*\*[^*]+?\*\*)/g).map((segment, index) => {
    const isBold = segment.startsWith('**') && segment.endsWith('**');
    const content = (isBold ? segment.slice(2, -2) : segment).replace(/\*\*/g, '');
    return isBold ? <strong key={index}>{content}</strong> : <span key={index}>{content}</span>;
  });
}

function initialsOf(name: string) {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(-2)
    .map((part) => part[0])
    .join('')
    .toUpperCase();
}
