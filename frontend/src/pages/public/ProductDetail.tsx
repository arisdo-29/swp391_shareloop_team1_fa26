import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useAppSelector } from '../../app/hooks';
import { selectCurrentUser, selectData } from '../../app/store';
import {
  Alert,
  Skeleton,
  Button,
  ConditionBadge,
  ItemImage,
  StatusBadge,
  TextArea,
  TypeBadge,
} from '../../components/ui';
import { DialogFrame } from '../../components/Dialog';
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

export function ProductDetail() {
  const { id } = useParams();
  const data = useAppSelector(selectData);
  const user = useAppSelector(selectCurrentUser);
  const navigate = useNavigate();
  const [requestError, setRequestError] = useState('');
  const [requestSuccess, setRequestSuccess] = useState('');
  const [requestOpen, setRequestOpen] = useState(false);
  const [loginPromptOpen, setLoginPromptOpen] = useState(false);
  const [message, setMessage] = useState('');
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
  const activeListing =
    (item.status === 'approved' || item.status === 'APPROVED') &&
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

  const openRequest = () => {
    setRequestError('');
    setRequestSuccess('');
    if (!user) {
      setLoginPromptOpen(true);
      return;
    }
    if (!currentUserId) {
      setRequestError('Vui lòng đăng nhập bằng tài khoản thật để gửi yêu cầu.');
      return;
    }
    if (own || !activeListing) return;
    if (user.reputationStars <= 0) {
      setRequestError('Uy tín của bạn đang ở mức 0 nên hiện không thể gửi yêu cầu.');
      return;
    }
    if (existingRequest) {
      setRequestError(`Bạn đã gửi yêu cầu cho món đồ này. Trạng thái hiện tại: ${requestStatusLabel[existingRequest.status]}.`);
      return;
    }
    setRequestOpen(true);
  };

  const submitRequest = async () => {
    if (!user) return;
    if (item.type === 'trade' && !offeredItemId) {
      setRequestError('Vui lòng chọn món bạn muốn dùng để trao đổi.');
      return;
    }
    if (requestSubmitting) return;
    if (!currentUserId) {
      setRequestError('Vui lòng đăng nhập bằng tài khoản thật để gửi yêu cầu.');
      return;
    }
    setRequestSubmitting(true);
    setRequestError('');
    try {
      const request = await requestApi.create(
        Number(item.id),
        currentUserId,
        item.type === 'trade' ? Number(offeredItemId) : undefined,
      );
      setApiSentRequests((current) => [toFeItemRequest(request, user.id), ...current]);
      setRequestOpen(false);
      setMessage('');
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

  return (
    <div className="page-shell">
      <Link
        to="/browse"
        className="mb-5 inline-flex items-center gap-1 text-sm font-semibold text-text-muted hover:text-primary"
      >
        {'<-'} Quay lại tìm đồ
      </Link>
      <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_360px]">
        <main className="min-w-0">
          <div className="overflow-hidden rounded-xl bg-surface-container">
            <ItemImage
              src={item.images[selectedImageIndex] ?? item.images[0]}
              alt={item.title}
              className="aspect-[4/3] w-full object-cover"
            />
          </div>
          {item.images.length > 1 ? (
            <div className="mt-3 flex gap-2 overflow-x-auto pb-1">
              {item.images.map((image, index) => (
                <button
                  key={`${image}-${index}`}
                  type="button"
                  aria-label={`Xem ảnh ${index + 1}`}
                  aria-pressed={selectedImageIndex === index}
                  onClick={() => setSelectedImageIndex(index)}
                  className={`shrink-0 overflow-hidden rounded-md focus-visible:outline-primary ${selectedImageIndex === index ? 'ring-2 ring-primary' : 'ring-1 ring-border'}`}
                >
                  <ItemImage
                    src={image}
                    alt={`${item.title} - ảnh ${index + 1}`}
                    className="h-16 w-20 object-cover sm:h-20 sm:w-24"
                  />
                </button>
              ))}
            </div>
          ) : null}
          <div className="mt-6 flex flex-wrap gap-2">
            <TypeBadge type={item.type} />
            <ConditionBadge condition={item.condition} />
            <StatusBadge status={item.status} />
          </div>
          <h1 className="mt-4 text-3xl font-bold leading-tight sm:text-4xl">{item.title}</h1>
          <div className="mt-3 flex flex-wrap gap-x-5 gap-y-2 text-sm text-text-muted">
            <span>{item.district}</span>
            <span>{item.category}</span>
            <span>Đăng {new Date(item.postedAt).toLocaleDateString('vi-VN')}</span>
          </div>
          <section className="mt-8 border-t border-border pt-6">
            <h2 className="section-title">Về món đồ này</h2>
            <p className="mt-3 max-w-3xl whitespace-pre-line text-[15px] leading-7 text-text-secondary">
              {item.description}
            </p>
            {item.brand ? (
              <p className="mt-3 text-sm text-text-secondary">
                <strong>Thương hiệu:</strong> {item.brand}
              </p>
            ) : null}
            {item.defectNote ? (
              <p className="mt-3 text-sm text-text-secondary">
                <strong>Lưu ý tình trạng:</strong> {item.defectNote}
              </p>
            ) : null}
            {item.operatingStatus ? (
              <p className="mt-3 text-sm text-text-secondary">
                <strong>Trạng thái hoạt động:</strong>{' '}
                {{ NORMAL: 'Bình thường', PARTIAL: 'Một phần', NOT_WORKING: 'Không hoạt động' }[item.operatingStatus]}
              </p>
            ) : null}
            {item.defectImages?.length ? (
              <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3">
                {item.defectImages.map((image, index) => (
                  <ItemImage key={`${image}-${index}`} src={image} alt={`Ảnh lỗi ${index + 1}`} className="aspect-square rounded-md object-cover" />
                ))}
              </div>
            ) : null}
            {item.tradeFor ? (
              <div className="mt-5 rounded-lg bg-secondary-soft p-4 text-sm leading-6 text-secondary">
                <strong>Mong muốn trao đổi:</strong> {item.tradeFor}
              </div>
            ) : null}
          </section>
        </main>
        <aside className="space-y-4 lg:sticky lg:top-24 lg:h-fit">
          <div className="rounded-lg bg-white p-5 ring-1 ring-border/80">
            <p className="text-xs font-semibold text-text-muted">
              {item.type === 'gift' ? 'Yêu cầu nhận món' : 'Gửi đề nghị trao đổi'}
            </p>
            <h2 className="mt-1 text-lg font-bold">{item.district}</h2>
            <p className="mt-2 text-sm leading-6 text-text-muted">
              Gửi yêu cầu miễn phí. Transaction và chat chỉ mở sau khi chủ bài chọn bạn.
            </p>
            <Button
              className="mt-5 w-full"
              size="lg"
              disabled={own || !activeListing}
              onClick={openRequest}
            >
              {own
                ? 'Đây là món của bạn'
                : item.type === 'gift'
                  ? 'Yêu cầu nhận'
                  : 'Đề nghị trao đổi'}
            </Button>
            {!user ? (
              <p className="mt-3 text-center text-xs text-text-muted">
                Bạn cần đăng nhập trước khi gửi yêu cầu.
              </p>
            ) : null}
            {requestSuccess ? (
              <div className="mt-3 rounded-md bg-success-soft p-3 text-xs leading-5 text-success">
                <p>{requestSuccess}</p>
              </div>
            ) : null}
            {requestError ? (
              <div className="mt-3 rounded-md bg-warning-soft p-3 text-xs leading-5 text-warning">
                <p>{requestError}</p>
              </div>
            ) : null}
            {isApiId && existingRequest?.status === 'PENDING' ? (
              <Button
                className="mt-3 w-full"
                variant="outline"
                disabled={requestSubmitting}
                onClick={() => void cancelApiRequest(existingRequest.id)}
              >
                Hủy yêu cầu
              </Button>
            ) : null}
            {currentUserId && !own ? (
              <Button
                className="mt-3 w-full"
                variant="ghost"
                onClick={() => {
                  setReportNotice('');
                  setReportOpen(true);
                }}
              >
                Báo cáo vi phạm
              </Button>
            ) : null}
            {reportNotice ? (
              <div className="mt-3 rounded-md bg-success-soft p-3 text-xs leading-5 text-success">
                {reportNotice}
              </div>
            ) : null}
          </div>
          <section className="rounded-xl bg-white p-5 ring-1 ring-border/80">
            <h2 className="font-bold">Thông tin người đăng</h2>
            <p className="mt-3 font-semibold">{item.donorName || 'Chưa có tên hiển thị'}</p>
            <ReputationBadge
              className="mt-3"
              trustStars={item.donorTrustStars}
              rank={item.donorRank}
              asDescribedRate={item.donorAsDescribedRate}
            />
          </section>
        </aside>
      </div>

      {requestOpen ? (
        <DialogFrame>
          <h2 className="text-xl font-bold">
            {item.type === 'gift' ? 'Gửi yêu cầu nhận' : 'Đề nghị trao đổi'}
          </h2>
          <p className="mt-2 text-sm leading-6 text-text-muted">
            {item.type === 'gift'
              ? 'Bạn muốn gửi yêu cầu nhận món đồ này?'
              : 'Chọn một món của bạn để đề nghị trao đổi.'}
          </p>
          {item.type === 'trade' ? (
            <div className="mt-5">
              <span className="label">Món bạn muốn dùng để trao đổi</span>
              <div className="space-y-2">
                {eligibleOfferedItems.map((entry) => (
                  <label
                    key={entry.id}
                    className={`flex cursor-pointer items-center gap-3 rounded-md border p-3 transition ${offeredItemId === entry.id ? 'border-primary bg-primary-faint' : 'border-border hover:border-primary/40'}`}
                  >
                    <input
                      type="radio"
                      name="offeredItem"
                      value={entry.id}
                      checked={offeredItemId === entry.id}
                      onChange={() => setOfferedItemId(entry.id)}
                    />
                    <ItemImage src={entry.images[0]} alt={entry.title} className="size-12 rounded-md object-cover" />
                    <span className="min-w-0 flex-1">
                      <strong className="block truncate text-sm">{entry.title}</strong>
                      <span className="text-xs text-text-muted">{entry.district}</span>
                    </span>
                  </label>
                ))}
                {!eligibleOfferedItems.length ? (
                  <p className="rounded-md bg-warning-soft p-3 text-xs leading-5 text-warning">
                    Bạn chưa có món đã duyệt, còn hoạt động và chưa nằm trong giao dịch khác để trao đổi.
                  </p>
                ) : null}
              </div>
            </div>
          ) : null}
          <div className="mt-5">
            <TextArea
              label="Lời nhắn cho người đăng"
              value={message}
              onChange={(event) => setMessage(event.target.value)}
            />
          </div>
          <div className="mt-6 flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setRequestOpen(false)}>
              Hủy
            </Button>
            <Button onClick={submitRequest} disabled={requestSubmitting || (item.type === 'trade' && !eligibleOfferedItems.length)}>
              {item.type === 'gift' ? 'Gửi yêu cầu' : 'Gửi đề nghị trao đổi'}
            </Button>
          </div>
        </DialogFrame>
      ) : null}

      {loginPromptOpen ? (
        <DialogFrame size="sm">
          <h2 className="text-xl font-bold">Bạn cần đăng nhập để sử dụng chức năng này.</h2>
          <div className="mt-6 flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setLoginPromptOpen(false)}>
              Để sau
            </Button>
            <Button onClick={() => navigate('/login')}>Đăng nhập</Button>
          </div>
        </DialogFrame>
      ) : null}

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
