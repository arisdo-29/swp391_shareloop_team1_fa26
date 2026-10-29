import { useEffect, useMemo, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { useAppSelector } from '../../app/hooks';
import { selectCurrentUser, selectData } from '../../app/store';
import {
  Alert,
  Avatar,
  Skeleton,
  Button,
  ConditionBadge,
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
import { reputationBlockedMessage } from '../../utils/apiErrorMessages';
import { loginPath } from '../../utils/authRedirect';
import { productDetailBackLink } from '../../utils/productDetailNavigation';

export function ProductDetail() {
  const { id } = useParams();
  const data = useAppSelector(selectData);
  const user = useAppSelector(selectCurrentUser);
  const navigate = useNavigate();
  const location = useLocation();
  const [requestError, setRequestError] = useState('');
  const [requestSuccess, setRequestSuccess] = useState('');
  const [offeredItemId, setOfferedItemId] = useState('');
  const [apiItem, setApiItem] = useState<Item | null>(null);
  const [apiOfferedItems, setApiOfferedItems] = useState<Item[]>([]);
  const [apiSentRequests, setApiSentRequests] = useState<ItemRequest[]>([]);
  const [loadingItem, setLoadingItem] = useState(false);
  const [itemError, setItemError] = useState('');
  const [itemReloadKey, setItemReloadKey] = useState(0);
  const [requestSubmitting, setRequestSubmitting] = useState(false);
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
            className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_360px]"
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
  const activeListing =
    (item.status === 'approved' || item.status === 'APPROVED') &&
    !isDonorLocked &&
    (!item.expiresAt || new Date(item.expiresAt).getTime() >= Date.now());
  const existingRequest = user
    ? apiSentRequests.find(
        (request) =>
          request.itemId === item.id &&
          request.requesterId === user.id &&
          (request.status === 'PENDING' || request.status === 'RESERVED'),
      )
    : undefined;
  const eligibleOfferedItems = user
    ? apiOfferedItems.filter(
        (entry) =>
          entry.ownerId === user.id &&
          entry.id !== item.id &&
          (entry.status === 'approved' || entry.status === 'APPROVED') &&
          (!entry.expiresAt || new Date(entry.expiresAt).getTime() >= Date.now()) &&
          !activeTransactionItemIds.has(entry.id),
      )
    : [];

  // Kiểm tra giống backend trước khi gửi để báo lỗi sớm; backend vẫn là nơi quyết định cuối cùng.
  const submitRequest = async () => {
    setRequestError('');
    setRequestSuccess('');
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
    if (existingRequest) {
      setRequestError(`Bạn đã gửi yêu cầu cho món đồ này. Trạng thái hiện tại: ${requestStatusLabel[existingRequest.status]}.`);
      return;
    }
    if (item.type === 'trade' && !offeredItemId) {
      setRequestError('Vui lòng chọn món bạn muốn dùng để trao đổi.');
      return;
    }
    setRequestSubmitting(true);
    try {
      const request = await requestApi.create(
        Number(item.id),
        currentUserId,
        item.type === 'trade' ? Number(offeredItemId) : undefined,
      );
      setApiSentRequests((current) => [toFeItemRequest(request, user.id), ...current]);
      setOfferedItemId('');
      setRequestSuccess(item.type === 'gift' ? 'Đã gửi yêu cầu nhận.' : 'Đã gửi đề nghị trao đổi.');
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
      <Link to={backLink.target} className="inline-flex min-h-11 items-center text-sm font-semibold text-primary hover:text-primary-hover">
        {backLink.label}
      </Link>
      <div className="mt-2 grid items-start gap-8 lg:grid-cols-[minmax(0,1fr)_380px]">
        <article className="min-w-0 space-y-6">
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

          <div className="space-y-2.5">
            <div className="flex flex-wrap gap-2">
              <TypeBadge type={item.type} />
              <ConditionBadge condition={item.condition} />
              {!activeListing ? <StatusBadge status={item.status} /> : null}
            </div>
            <h1 className="text-2xl font-bold leading-tight sm:text-3xl">{item.title}</h1>
            <p className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-text-muted">
              <span>{item.district}</span>
              <span>{item.category}</span>
              {item.brand ? <span>Thương hiệu {item.brand}</span> : null}
              <span>
                Đăng {new Date(item.postedAt).toLocaleDateString('vi-VN')}
                {item.expiresAt ? `, hiển thị đến ${new Date(item.expiresAt).toLocaleDateString('vi-VN')}` : ''}
              </span>
            </p>
          </div>

          {item.tradeFor ? (
            <div className="rounded-lg border border-secondary-fixed bg-secondary-soft px-4 py-4">
              <p className="text-xs font-bold uppercase tracking-wide text-secondary-hover">Chủ bài muốn đổi lấy</p>
              <p className="mt-1 text-base font-bold">{item.tradeFor}</p>
              <p className="mt-1 text-sm text-text-secondary">Gợi ý: nếu bạn có món khác, hãy ghi vào đề nghị để chủ bài cân nhắc.</p>
            </div>
          ) : null}

          <section aria-labelledby="item-description" className="space-y-3">
            <h2 id="item-description" className="section-title">Mô tả</h2>
            <p className="max-w-3xl whitespace-pre-line text-[15px] leading-7">{item.description}</p>
            {item.defectNote ? (
              <p className="text-sm text-text-secondary">
                <strong className="text-text-primary">Lưu ý tình trạng:</strong> {item.defectNote}
              </p>
            ) : null}
            {operatingLabel ? (
              <p className="text-sm text-text-secondary">
                <strong className="text-text-primary">Trạng thái hoạt động:</strong> {operatingLabel}
              </p>
            ) : null}
            {item.defectImages?.length ? (
              <div className="grid grid-cols-3 gap-3 sm:grid-cols-4">
                {item.defectImages.map((image, index) => (
                  <ItemImage key={`${image}-${index}`} src={image} alt={`Ảnh lỗi ${index + 1}`} className="aspect-square rounded-md object-cover" />
                ))}
              </div>
            ) : null}
          </section>

          <section aria-labelledby="item-owner" className="flex flex-wrap items-center gap-4 rounded-lg bg-white px-4 py-4 ring-1 ring-border/80">
            <Avatar
              size="lg"
              user={{ name: item.donorName || 'Người đăng', avatarInitials: initialsOf(item.donorName || '') }}
            />
            <div className="min-w-0 flex-1 space-y-1">
              <h2 id="item-owner" className="text-base font-bold">{item.donorName || 'Người đăng'}</h2>
              <ReputationBadge trustStars={item.donorTrustStars} rank={item.donorRank} asDescribedRate={item.donorAsDescribedRate} />
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
          ) : existingRequest ? (
            <>
              <h2 className="text-lg font-bold">Bạn đã gửi yêu cầu</h2>
              <p className="text-sm leading-6 text-text-secondary">
                Trạng thái: <strong className="text-text-primary">{requestStatusLabel[existingRequest.status]}</strong>
              </p>
              {existingRequest.status === 'RESERVED' ? (
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
              {item.type === 'trade' && user ? (
                <fieldset className="space-y-2">
                  <legend className="label">Chọn món bạn mang đổi</legend>
                  {eligibleOfferedItems.map((entry) => (
                    <button
                      key={entry.id}
                      type="button"
                      aria-pressed={offeredItemId === entry.id}
                      onClick={() => setOfferedItemId(entry.id)}
                      className={`flex w-full items-center gap-3 rounded-md border p-2.5 text-left transition ${offeredItemId === entry.id ? 'border-primary bg-primary-faint' : 'border-border hover:border-primary/40'}`}
                    >
                      <ItemImage src={entry.images[0]} alt={entry.title} className="h-11 w-14 shrink-0 rounded-md object-cover" />
                      <span className="min-w-0 flex-1">
                        <strong className="block truncate text-sm">{entry.title}</strong>
                        <span className="text-xs text-text-muted">{entry.district}</span>
                      </span>
                    </button>
                  ))}
                  {!eligibleOfferedItems.length ? (
                    <p className="rounded-md bg-warning-soft p-3 text-xs leading-5 text-warning">
                      Bạn chưa có món đã duyệt, còn hoạt động và chưa nằm trong giao dịch khác để trao đổi.
                    </p>
                  ) : null}
                  <Link to="/post" className="inline-flex min-h-11 items-center text-sm font-semibold text-primary hover:text-primary-hover">
                    Đăng món mới để đổi
                  </Link>
                </fieldset>
              ) : null}
              <Button
                size="lg"
                className="w-full"
                disabled={requestSubmitting || (Boolean(user) && item.type === 'trade' && !eligibleOfferedItems.length)}
                onClick={() => void submitRequest()}
              >
                {requestSubmitting
                  ? 'Đang gửi...'
                  : !user
                    ? 'Đăng nhập để gửi yêu cầu'
                    : item.type === 'gift'
                      ? 'Gửi yêu cầu, miễn phí'
                      : 'Gửi đề nghị, miễn phí'}
              </Button>
              <ul className="list-disc space-y-1 pl-5 text-xs leading-5 text-text-secondary">
                <li>Chat mở khi chủ bài chọn bạn. Bạn có thể hủy yêu cầu tới lúc đó.</li>
                <li>Số điện thoại chỉ hiện khi hai bên đã chốt lịch hẹn.</li>
              </ul>
            </>
          )}
          {requestSuccess ? <Alert tone="success">{requestSuccess}</Alert> : null}
          {requestError ? <Alert tone="warning">{requestError}</Alert> : null}
          {reportNotice ? <Alert tone="success">{reportNotice}</Alert> : null}
          {currentUserId && !own ? (
            <button
              type="button"
              className="min-h-11 text-sm font-semibold text-text-secondary hover:text-error"
              onClick={() => {
                setReportNotice('');
                setReportOpen(true);
              }}
            >
              Báo cáo bài đăng
            </button>
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

function initialsOf(name: string) {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(-2)
    .map((part) => part[0])
    .join('')
    .toUpperCase();
}
