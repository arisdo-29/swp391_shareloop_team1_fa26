import { useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '../../app/hooks';
import { actions, selectCurrentUser, selectData } from '../../app/store';
import {
  Alert,
  Button,
  ConditionBadge,
  Icon,
  OwnerCard,
  StatusBadge,
  TextArea,
  TypeBadge,
} from '../../components/ui';

const requestStatusLabel = {
  PENDING: 'Chờ phản hồi',
  ACCEPTED: 'Đã được chọn',
  NOT_SELECTED: 'Không được chọn',
  CANCELLED: 'Đã hủy',
};

export function ProductDetail() {
  const { id } = useParams();
  const data = useAppSelector(selectData);
  const user = useAppSelector(selectCurrentUser);
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const [requestError, setRequestError] = useState('');
  const [requestSuccess, setRequestSuccess] = useState('');
  const [requestOpen, setRequestOpen] = useState(false);
  const [loginPromptOpen, setLoginPromptOpen] = useState(false);
  const [message, setMessage] = useState('');
  const [offeredItemId, setOfferedItemId] = useState('');
  const item = data.items.find((x) => x.id === id);

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

  if (!item)
    return (
      <div className="page-shell">
        <Alert tone="error">Món đồ này không tồn tại hoặc đã được gỡ.</Alert>
      </div>
    );

  const owner = data.users.find((x) => x.id === item.ownerId)!;
  const own = user?.id === item.ownerId;
  const activeListing =
    (item.status === 'approved' || item.status === 'APPROVED') &&
    new Date(item.expiresAt).getTime() >= Date.now();
  const existingRequest = user
    ? data.itemRequests.find(
        (request) =>
          request.itemId === item.id &&
          request.requesterId === user.id &&
          (request.status === 'PENDING' || request.status === 'ACCEPTED'),
      )
    : undefined;
  const eligibleOfferedItems = user
    ? data.items.filter(
        (entry) =>
          entry.ownerId === user.id &&
          entry.id !== item.id &&
          (entry.status === 'approved' || entry.status === 'APPROVED') &&
          new Date(entry.expiresAt).getTime() >= Date.now() &&
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

  const submitRequest = () => {
    if (!user) return;
    if (item.type === 'trade' && !offeredItemId) {
      setRequestError('Vui lòng chọn món bạn muốn dùng để trao đổi.');
      return;
    }
    dispatch(
      actions.createItemRequest({
        itemId: item.id,
        requesterId: user.id,
        offeredItemId: item.type === 'trade' ? offeredItemId : undefined,
        message,
      }),
    );
    setRequestOpen(false);
    setMessage('');
    setOfferedItemId('');
    setRequestSuccess(item.type === 'gift' ? 'Đã gửi yêu cầu nhận.' : 'Đã gửi đề nghị trao đổi.');
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
            <img src={item.images[0]} alt={item.title} className="aspect-[4/3] w-full object-cover" />
          </div>
          <div className="mt-6 flex flex-wrap gap-2">
            <TypeBadge type={item.type} />
            <ConditionBadge condition={item.condition} />
            <StatusBadge status={item.status} />
          </div>
          <h1 className="mt-4 text-3xl font-bold leading-tight sm:text-4xl">{item.title}</h1>
          <div className="mt-3 flex flex-wrap gap-x-5 gap-y-2 text-sm text-text-muted">
            <span className="inline-flex items-center gap-1.5">
              <Icon name="location" className="size-4" />
              {item.district}
            </span>
            <span>{item.category}</span>
            <span>Đăng {new Date(item.postedAt).toLocaleDateString('vi-VN')}</span>
          </div>
          <section className="mt-8 border-t border-border pt-6">
            <h2 className="section-title">Về món đồ này</h2>
            <p className="mt-3 max-w-3xl whitespace-pre-line text-[15px] leading-7 text-text-secondary">
              {item.description}
            </p>
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
              <div className="mt-3 rounded-md bg-emerald-50 p-3 text-xs leading-5 text-success">
                <p>{requestSuccess}</p>
              </div>
            ) : null}
            {requestError ? (
              <div className="mt-3 rounded-md bg-amber-50 p-3 text-xs leading-5 text-warning">
                <p>{requestError}</p>
              </div>
            ) : null}
          </div>
          <OwnerCard owner={owner} />
        </aside>
      </div>

      {requestOpen ? (
        <div className="fixed inset-0 z-40 grid place-items-end bg-black/30 sm:place-items-center sm:p-4">
          <div
            role="dialog"
            aria-modal="true"
            className="w-full rounded-t-xl bg-white p-5 shadow-lg sm:max-w-lg sm:rounded-xl sm:p-6"
          >
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
                      <img src={entry.images[0]} alt="" className="size-12 rounded-md object-cover" />
                      <span className="min-w-0 flex-1">
                        <strong className="block truncate text-sm">{entry.title}</strong>
                        <span className="text-xs text-text-muted">{entry.district}</span>
                      </span>
                    </label>
                  ))}
                  {!eligibleOfferedItems.length ? (
                    <p className="rounded-md bg-amber-50 p-3 text-xs leading-5 text-warning">
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
              <Button onClick={submitRequest} disabled={item.type === 'trade' && !eligibleOfferedItems.length}>
                {item.type === 'gift' ? 'Gửi yêu cầu' : 'Gửi đề nghị trao đổi'}
              </Button>
            </div>
          </div>
        </div>
      ) : null}

      {loginPromptOpen ? (
        <div className="fixed inset-0 z-40 grid place-items-end bg-black/30 sm:place-items-center sm:p-4">
          <div
            role="dialog"
            aria-modal="true"
            className="w-full rounded-t-xl bg-white p-5 shadow-lg sm:max-w-md sm:rounded-xl sm:p-6"
          >
            <h2 className="text-xl font-bold">Bạn cần đăng nhập để sử dụng chức năng này.</h2>
            <div className="mt-6 flex justify-end gap-2">
              <Button variant="ghost" onClick={() => setLoginPromptOpen(false)}>
                Để sau
              </Button>
              <Button onClick={() => navigate('/login')}>Đăng nhập</Button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}
