import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useAppSelector } from '../../app/hooks';
import { selectCurrentUser } from '../../app/store';
import {
  conversationImage,
  conversationTitle,
  requestParticipantName,
} from '../../components/messages/conversation';
import {
  Alert,
  Button,
  EmptyState,
  Icon,
  ItemImage,
  SearchField,
  SkeletonRows,
  StatusBadge,
  TypeBadge,
} from '../../components/ui';
import { requestApi, toBackendUserId, type RequestResponse } from '../../services/requestApi';
import { isClosedRequestStatus } from '../../utils/conversationFilter';
import { canViewChat, requestStatusLabel } from '../../utils/request';
import { useRealtimeRefresh } from '../../utils/useRealtimeRefresh';

const transactionFilters = [
  { value: 'all', label: 'Tất cả' },
  { value: 'active', label: 'Đang diễn ra' },
  { value: 'completed', label: 'Hoàn tất' },
  { value: 'cancelled', label: 'Đã hủy' },
] as const;

function normalizeSearch(text: string) {
  return text
    .toLocaleLowerCase('vi-VN')
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .replace(/đ/g, 'd')
    .trim();
}

export function Transactions() {
  const user = useAppSelector(selectCurrentUser);
  const currentUserId = toBackendUserId(user?.id);

  return (
    <div className="page-shell space-y-5">
      <h1 className="page-title">Giao dịch</h1>
      {currentUserId ? (
        <TransactionList key={currentUserId} currentUserId={currentUserId} />
      ) : (
        <EmptyState title="Chưa đăng nhập" text="Vui lòng đăng nhập lại để xem giao dịch." />
      )}
    </div>
  );
}

function TransactionList({ currentUserId }: { currentUserId: number }) {
  const [searchParams, setSearchParams] = useSearchParams();
  const statusParam = searchParams.get('status');
  const filter = transactionFilters.find(({ value }) => value === statusParam)?.value ?? 'all';
  const query = searchParams.get('q') ?? '';
  const [transactions, setTransactions] = useState<RequestResponse[] | null>(null);
  const [failed, setFailed] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);
  useRealtimeRefresh(['REQUEST'], () => setReloadKey((key) => key + 1));

  useEffect(() => {
    let active = true;
    Promise.all([requestApi.mine(currentUserId), requestApi.received(currentUserId)])
      .then(([sent, received]) => {
        if (!active) return;
        const byId = new Map(sent.map((request) => [request.id, request]));
        for (const request of received) {
          byId.set(request.id, { ...request, donorId: request.donorId ?? currentUserId });
        }
        const sorted = Array.from(byId.values())
          .filter((request) => canViewChat(request.status))
          .sort(
            (left, right) =>
              Number(isClosedRequestStatus(left.status)) -
                Number(isClosedRequestStatus(right.status)) ||
              right.createdAt.localeCompare(left.createdAt),
          );
        setTransactions(sorted);
        setFailed(false);
      })
      .catch(() => {
        if (active) setFailed(true);
      });
    return () => {
      active = false;
    };
  }, [currentUserId, reloadKey]);

  if (failed) {
    return (
      <div className="space-y-3">
        <Alert tone="error">Không tải được danh sách giao dịch.</Alert>
        <Button
          type="button"
          variant="outline"
          onClick={() => {
            setFailed(false);
            setReloadKey((key) => key + 1);
          }}
        >
          Thử tải lại
        </Button>
      </div>
    );
  }

  if (!transactions) return <SkeletonRows label="Đang tải giao dịch" rows={4} />;

  if (!transactions.length) {
    return (
      <EmptyState
        title="Chưa có giao dịch"
        text="Các giao dịch cho tặng và trao đổi của bạn sẽ xuất hiện tại đây."
      />
    );
  }

  const search = normalizeSearch(query);
  const visibleTransactions = transactions.flatMap((request) => {
    const group =
      request.status === 'COMPLETED'
        ? 'completed'
        : request.status === 'CANCELLED'
          ? 'cancelled'
          : 'active';
    if (filter !== 'all' && group !== filter) return [];
    const conversation = { request };
    const title = conversationTitle(conversation);
    const partner = requestParticipantName(conversation, currentUserId);
    if (!normalizeSearch(`${title} ${partner}`).includes(search)) return [];
    return [{ conversation, title, partner }];
  });
  const detailsSearch = searchParams.toString();
  const updateFilter = (key: 'status' | 'q', value: string) => {
    const params = new URLSearchParams(searchParams);
    if (value) params.set(key, value);
    else params.delete(key);
    setSearchParams(params, { replace: true });
  };

  return (
    <>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div role="group" aria-label="Lọc theo trạng thái" className="flex flex-wrap gap-1.5">
          {transactionFilters.map(({ value, label }) => (
            <Button
              key={value}
              type="button"
              variant={value === filter ? 'primary' : 'outline'}
              size="sm"
              aria-pressed={value === filter}
              onClick={() => updateFilter('status', value === 'all' ? '' : value)}
            >
              {label}
            </Button>
          ))}
        </div>
        <div className="w-full min-w-0 md:w-80 lg:w-96">
          <SearchField
            showIcon
            type="search"
            aria-label="Tìm theo tên món hoặc người giao dịch"
            placeholder="Tìm món hoặc người giao dịch"
            value={query}
            onChange={(event) => updateFilter('q', event.target.value)}
          />
        </div>
      </div>
      <p className="sr-only" role="status">
        {visibleTransactions.length} giao dịch phù hợp
      </p>
      {!visibleTransactions.length ? (
        <EmptyState
          title="Không có giao dịch phù hợp"
          text="Thử đổi bộ lọc hoặc tìm bằng từ khóa khác."
        />
      ) : (
        <ul className="space-y-2">
          {visibleTransactions.map(({ conversation, title, partner }) => {
            const { request } = conversation;
            return (
              <li key={request.id}>
                <Link
                  to={{ pathname: `/transactions/${request.id}`, search: detailsSearch }}
                  className="flex min-w-0 items-center gap-3 rounded-lg border border-border bg-surface p-3 transition hover:border-primary/50 hover:bg-primary-faint focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                >
                  <ItemImage
                    src={conversationImage(conversation)}
                    alt=""
                    loading="lazy"
                    className="size-14 shrink-0 rounded-md object-cover"
                  />
                  <div className="min-w-0 flex-1">
                    <h2 className="break-words text-sm font-bold text-primary">{title}</h2>
                    <p className="break-words text-xs text-text-secondary">{partner}</p>
                    <div className="mt-1 flex flex-wrap items-center gap-1.5">
                      <TypeBadge type={request.type === 'SWAP' ? 'trade' : 'gift'} />
                      <StatusBadge
                        status={request.status}
                        label={requestStatusLabel[request.status]}
                      />
                    </div>
                  </div>
                  <Icon name="caretRight" className="size-4 shrink-0 text-primary" />
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </>
  );
}
