import { useEffect, useMemo, useRef, useState, type Dispatch, type ReactNode } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAppSelector } from '../../app/hooks';
import { selectCurrentUser, selectData } from '../../app/store';
import { Alert, Button, Field, Icon, PageHeader } from '../../components/ui';
import type { CreditHistory, CreditHistoryType, Item, Topup } from '../../types/domain';
import { formatCredit, formatVnd } from '../../utils/formatting';
import { useWallet } from '../../app/useWallet';
import {
  walletApi,
  type HeldFeeItem,
  type WalletTransactionResponse,
} from '../../services/walletApi';
import {
  paymentApi,
  type PaymentOrderStatus,
  type PaymentProvider,
  type TopUpOrderResponse,
} from '../../services/paymentApi';
import type { ApiError } from '../../services/http';
import { backendMockProviderMessage, startPayment } from '../../services/paymentFlow';
import {
  clearPendingTopUp,
  loadPendingTopUp,
  paymentRedirectUrl,
  returnOrderCode,
  savePendingTopUp,
} from '../../services/topUpSession';

type Step = 'select' | 'pending';
type HistoryFilter = 'all' | 'topup' | 'spend';
type ReturnState = 'idle' | 'pending' | 'failed' | 'invalid' | 'checking' | 'success';

const filterTypes: Record<Exclude<HistoryFilter, 'all'>, CreditHistoryType[]> = {
  topup: ['TOPUP'],
  spend: ['SPEND', 'AI_SPEND', 'RECEIVE_FEE'],
};

const HISTORY_PAGE_SIZE = 20;
const DAY_MS = 24 * 60 * 60 * 1000;
// Asia/Ho_Chi_Minh là UTC+7 quanh năm (không có giờ mùa hè) nên cộng cố định 7 giờ.
const VN_OFFSET_MS = 7 * 60 * 60 * 1000;

type QuickRange = '7days' | 'month' | 'quarter' | 'all';

// Ngày hôm nay theo giờ Việt Nam, dạng yyyy-MM-dd (dùng được cho ô <input type="date">).
function todayInVietnam() {
  return new Date(Date.now() + VN_OFFSET_MS).toISOString().slice(0, 10);
}

function dateToInput(timestamp: number) {
  return new Date(timestamp).toISOString().slice(0, 10);
}

// Trả về [từ ngày, đến ngày] cho nút lọc nhanh; '' nghĩa là không giới hạn.
function quickRangeDates(range: QuickRange): [string, string] {
  const today = todayInVietnam();
  if (range === 'all') return ['', ''];
  if (range === '7days') {
    return [dateToInput(Date.parse(today) - 6 * DAY_MS), today];
  }
  if (range === 'month') return [today.slice(0, 8) + '01', today];
  const month = Number(today.slice(5, 7));
  const quarterStartMonth = Math.floor((month - 1) / 3) * 3 + 1;
  return [`${today.slice(0, 5)}${String(quarterStartMonth).padStart(2, '0')}-01`, today];
}

// 00:00 giờ Việt Nam của ngày đã chọn, đổi sang ISO UTC. API tính "from" là gồm.
function startOfDayIso(date: string) {
  return new Date(`${date}T00:00:00+07:00`).toISOString();
}

// API tính "to" là không gồm, nên lấy 00:00 của ngày kế tiếp để bao trọn cuối ngày đã chọn.
function endOfDayIso(date: string) {
  return new Date(new Date(`${date}T00:00:00+07:00`).getTime() + DAY_MS).toISOString();
}

const legacyHistoryPattern =
  /swap|give|giu phi|giữ phí|giu giao dich|giữ giao dịch|tam giu|tạm giữ/i;

function isPostFee(entry: CreditHistory) {
  return entry.type === 'SPEND' && /^phi dang bai|^phí đăng bài/i.test(entry.description);
}

function isVisibleCreditHistory(entry: CreditHistory) {
  if (['TRANSACTION_FEE', 'HOLD', 'RELEASE_HOLD'].includes(entry.type)) return false;
  if (legacyHistoryPattern.test(`${entry.description} ${entry.note}`)) return false;
  return (
    entry.type === 'TOPUP' ||
    entry.type === 'AI_SPEND' ||
    entry.type === 'RECEIVE_FEE' ||
    entry.type === 'ADMIN_ADJUSTMENT' ||
    isPostFee(entry)
  );
}

function postFeeItemId(entry: CreditHistory) {
  return (
    entry.ref?.match(/^post:(.+):fee$/)?.[1] ?? entry.transactionId.match(/^post:(.+):fee$/)?.[1]
  );
}

function balanceBefore(entry: CreditHistory) {
  return entry.balance - entry.amount;
}

const walletTransactionLabels: Record<WalletTransactionResponse['type'], string> = {
  TOP_UP: 'Nạp Credit',
  POST_FEE: 'Phí đăng bài',
  EDIT_FEE: 'Phí chỉnh sửa bài',
  RENEW_FEE: 'Phí gia hạn bài',
  BOOST_FEE: 'Phí đẩy tin',
  AI_SEARCH_FEE: 'Phí dịch vụ AI',
  ADMIN_ADJUST: 'Điều chỉnh bởi quản trị viên',
};

const paymentStatusLabels: Record<PaymentOrderStatus, string> = {
  PENDING: 'Đang chờ thanh toán',
  SUCCESS: 'Đã cộng Credit',
  FAILED: 'Thanh toán thất bại',
  EXPIRED: 'Đơn đã hết hạn',
};

const paymentProviderLabels: Record<PaymentProvider, string> = {
  VNPAY: 'VNPAY',
  MOCK: 'Thanh toán thử nghiệm',
  MANUAL: 'QR minh họa, admin duyệt',
};

const topUpAmountInvalidMessage =
  'Số tiền nạp chưa hợp lệ. Theo giá trị mặc định, số tiền cần chia hết cho 1.000đ và tối thiểu 10.000đ; các mức này có thể thay đổi theo cấu hình hệ thống.';

function toCreditHistory(entry: WalletTransactionResponse, userId: string): CreditHistory {
  const type: CreditHistoryType =
    entry.type === 'TOP_UP'
      ? 'TOPUP'
      : entry.type === 'AI_SEARCH_FEE'
        ? 'AI_SPEND'
        : entry.type === 'ADMIN_ADJUST'
          ? 'ADMIN_ADJUSTMENT'
          : 'SPEND';
  return {
    id: `api_wallet_${entry.id}`,
    userId,
    transactionId: entry.paymentOrderId
      ? `payment-order-${entry.paymentOrderId}`
      : `ledger-${entry.id}`,
    type,
    amount: entry.amount,
    balance: entry.balanceAfter,
    description: walletTransactionLabels[entry.type],
    status: 'completed',
    ref: entry.type === 'POST_FEE' && entry.itemId ? `post:${entry.itemId}:fee` : undefined,
    note: entry.note ?? '',
    createdAt: entry.createdAt,
  };
}

function topUpCreateErrorMessage(error: unknown) {
  const apiError = error as Partial<ApiError>;
  if (apiError.code === 'WALLET_TOPUP_AMOUNT_INVALID') return topUpAmountInvalidMessage;
  if (typeof apiError.message === 'string' && apiError.message) return apiError.message;
  return 'Không tạo được đơn nạp Credit. Vui lòng thử lại.';
}

export function Credit() {
  const user = useAppSelector(selectCurrentUser)!;
  const data = useAppSelector(selectData);
  const navigate = useNavigate();
  const location = useLocation();
  const [apiAmountVnd, setApiAmountVnd] = useState('');
  const [step, setStep] = useState<Step>('select');
  const [historyFilter, setHistoryFilter] = useState<HistoryFilter>('all');
  const [selectedEntry, setSelectedEntry] = useState<CreditHistory | Topup | null>(null);
  const {
    isApiAccount,
    wallet,
    loading: walletLoading,
    error: walletError,
    refresh: refreshWallet,
  } = useWallet();
  const [apiHistory, setApiHistory] = useState<CreditHistory[]>([]);
  const [apiHistoryLoading, setApiHistoryLoading] = useState(false);
  const [apiHistoryError, setApiHistoryError] = useState('');
  const [historyReloadKey, setHistoryReloadKey] = useState(0);
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [historyPage, setHistoryPage] = useState(0);
  const [historyTotal, setHistoryTotal] = useState(0);
  const [heldFees, setHeldFees] = useState<HeldFeeItem[]>([]);
  const [heldFeesError, setHeldFeesError] = useState('');
  const [apiTopUpOrder, setApiTopUpOrder] = useState<TopUpOrderResponse | null>(null);
  const [topUpSubmitting, setTopUpSubmitting] = useState(false);
  const [topUpProcessing, setTopUpProcessing] = useState(false);
  const [topUpError, setTopUpError] = useState('');
  const [returnState, setReturnState] = useState<ReturnState>('idle');
  const [orderCheckKey, setOrderCheckKey] = useState(0);
  const createTopUpInFlight = useRef(false);

  const topups = isApiAccount ? [] : data.topups.filter((topup) => topup.userId === user.id);
  const completedCredits =
    isApiAccount && returnState === 'success' ? (apiTopUpOrder?.credits ?? null) : null;

  // Từ ngày sau Đến ngày thì báo lỗi và không gọi API.
  const dateRangeError =
    fromDate && toDate && fromDate > toDate ? '"Từ ngày" không được sau "Đến ngày".' : '';

  // Danh sách bài đang giữ phí tải lại mỗi khi số dư ví được tải lại (wallet đổi object).
  useEffect(() => {
    if (!isApiAccount) {
      setHeldFees([]);
      return;
    }
    let active = true;
    walletApi
      .listHeldFees()
      .then((response) => {
        if (!active) return;
        setHeldFees(response);
        setHeldFeesError('');
      })
      .catch((caught) => {
        if (!active) return;
        setHeldFees([]);
        setHeldFeesError((caught as ApiError).message || 'Không tải được danh sách bài đang giữ phí.');
      });
    return () => {
      active = false;
    };
  }, [isApiAccount, wallet]);

  useEffect(() => {
    if (!isApiAccount) {
      setApiHistory([]);
      setApiHistoryError('');
      return;
    }
    if (dateRangeError) return;
    let active = true;
    setApiHistoryLoading(true);
    setApiHistoryError('');
    walletApi
      .listTransactions({
        from: fromDate ? startOfDayIso(fromDate) : undefined,
        to: toDate ? endOfDayIso(toDate) : undefined,
        page: historyPage,
        size: HISTORY_PAGE_SIZE,
      })
      .then((response) => {
        if (!active) return;
        setApiHistory(response.items.map((entry) => toCreditHistory(entry, user.id)));
        setHistoryTotal(response.totalElements);
      })
      .catch((caught) => {
        if (!active) return;
        const apiError = caught as ApiError;
        setApiHistory([]);
        setApiHistoryError(apiError.message || 'Không tải được lịch sử Credit.');
      })
      .finally(() => {
        if (active) setApiHistoryLoading(false);
      });
    return () => {
      active = false;
    };
  }, [historyReloadKey, isApiAccount, user.id, fromDate, toDate, historyPage, dateRangeError]);

  // Đổi bộ lọc ngày thì quay về trang đầu.
  const changeDateRange = (nextFrom: string, nextTo: string) => {
    setFromDate(nextFrom);
    setToDate(nextTo);
    setHistoryPage(0);
  };

  useEffect(() => {
    if (!isApiAccount) return;
    const code = returnOrderCode(new URLSearchParams(location.search));
    if (!code) {
      if (new URLSearchParams(location.search).get('status') === 'INVALID_SIGNATURE') {
        setReturnState('invalid');
        clearPendingTopUp();
      }
      return;
    }
    let active = true;
    setReturnState('checking');
    paymentApi
      .getOrder(code)
      .then((order) => {
        if (!active) return;
        if (order.provider === 'MANUAL' && order.status !== 'SUCCESS') {
          navigate('/wallet/topup/manual-checkout?orderCode=' + encodeURIComponent(code), {
            replace: true,
          });
          return;
        }
        setApiTopUpOrder({ ...order, redirectUrl: '' });
        setStep('pending');
        setReturnState(
          order.status === 'SUCCESS'
            ? 'success'
            : order.status === 'FAILED'
              ? 'failed'
              : order.status === 'EXPIRED'
                ? 'idle'
                : 'pending',
        );
        if (order.status === 'SUCCESS') {
          clearPendingTopUp();
          refreshWallet();
          setHistoryReloadKey((key) => key + 1);
        }
      })
      .catch((caught: ApiError) => {
        if (active) {
          setReturnState('idle');
          setTopUpError(caught.message || 'Không tải được đơn nạp.');
        }
      });
    return () => {
      active = false;
    };
  }, [isApiAccount, location.search, navigate, refreshWallet, orderCheckKey]);

  const allHistory = useMemo(
    () =>
      isApiAccount
        ? apiHistory
        : data.creditHistory
            .filter((entry) => entry.userId === user.id)
            .filter(isVisibleCreditHistory)
            .sort((a, b) => b.createdAt.localeCompare(a.createdAt)),
    [apiHistory, data.creditHistory, isApiAccount, user.id],
  );

  const history = useMemo(() => {
    if (historyFilter === 'all') return allHistory;
    return allHistory.filter((entry) => filterTypes[historyFilter].includes(entry.type));
  }, [allHistory, historyFilter]);

  const selectedRelatedTransaction =
    selectedEntry && 'relatedTransactionId' in selectedEntry
      ? data.transactions.find((tx) => tx.id === selectedEntry.relatedTransactionId)
      : undefined;
  const selectedRelatedItem = selectedRelatedTransaction
    ? data.items.find((item) => item.id === selectedRelatedTransaction.itemId)
    : selectedEntry && !('method' in selectedEntry)
      ? data.items.find((item) => item.id === postFeeItemId(selectedEntry))
      : undefined;

  const continuePayment = async () => {
    if (!isApiAccount || createTopUpInFlight.current) return;
    const amountVnd = Number(apiAmountVnd);
    if (!Number.isSafeInteger(amountVnd) || amountVnd <= 0) {
      setTopUpError('Vui lòng nhập số tiền nạp hợp lệ.');
      return;
    }
    createTopUpInFlight.current = true;
    setTopUpSubmitting(true);
    setTopUpProcessing(false);
    setTopUpError('');
    try {
      const order = await paymentApi.createTopUpOrder({ amountVnd });
      savePendingTopUp(order);
      setApiTopUpOrder(order);
      setStep('pending');
      if (order.provider === 'MOCK') {
        setTopUpError(backendMockProviderMessage);
        return;
      }
      setTopUpProcessing(true);
      await startPayment(order);
    } catch (caught) {
      setTopUpError(topUpCreateErrorMessage(caught));
    } finally {
      createTopUpInFlight.current = false;
      setTopUpSubmitting(false);
      setTopUpProcessing(false);
    }
  };

  return (
    <div className="page-shell">
      <PageHeader
        title="Credit của bạn"
        description="Quản lý số dư, nạp Credit và theo dõi các giao dịch của bạn."
      />

      {walletLoading ? (
        <div className="mb-6">
          <Alert>Đang tải số dư Credit...</Alert>
        </div>
      ) : null}
      {walletError ? (
        <div className="mb-6 space-y-3">
          <Alert tone="error">{walletError}</Alert>
          <Button variant="outline" onClick={refreshWallet}>
            Thử tải lại số dư
          </Button>
        </div>
      ) : null}

      {isApiAccount && returnState === 'pending' ? (
        <div className="mb-6 space-y-3">
          <Alert>Đơn đang chờ xác nhận từ hệ thống. Credit chưa được cộng cho giao dịch này.</Alert>
          <Button
            size="sm"
            variant="outline"
            onClick={() => {
              setOrderCheckKey((key) => key + 1);
            }}
          >
            Kiểm tra lại
          </Button>
        </div>
      ) : null}
      {isApiAccount && returnState === 'failed' ? (
        <div className="mb-6">
          <Alert tone="error">Thanh toán không thành công. Credit chưa được cộng vào ví.</Alert>
        </div>
      ) : null}
      {isApiAccount && returnState === 'invalid' ? (
        <div className="mb-6">
          <Alert tone="error">
            Kết quả trả về từ cổng thanh toán không hợp lệ. Credit chưa được cộng vào ví.
          </Alert>
        </div>
      ) : null}
      {completedCredits !== null ? (
        <div className="mb-6 flex items-start gap-3 rounded-lg border border-emerald-100 bg-emerald-50 px-4 py-3 text-success">
          <span className="mt-0.5 grid size-7 shrink-0 place-items-center rounded-full bg-white">
            <Icon name="check" className="size-4" weight="bold" />
          </span>
          <div>
            <p className="text-sm font-bold">Nạp thành công +{completedCredits} Credit</p>
            <p className="mt-0.5 text-xs leading-5">Số dư ví đã được cập nhật từ hệ thống.</p>
          </div>
        </div>
      ) : null}

      <section className="overflow-hidden rounded-xl bg-primary text-white shadow-lg">
        <div className="grid gap-8 px-6 py-7 sm:px-8 md:grid-cols-[1fr_auto] md:items-end lg:px-10 lg:py-9">
          <div>
            <div className="flex items-center gap-2 text-xs font-semibold text-white/70">
              <Icon name="wallet" className="size-4" />
              Số dư Credit
            </div>
            <div className="mt-3 text-4xl font-bold tabular-nums sm:text-5xl">
              {isApiAccount
                ? wallet
                  ? formatCredit(wallet.creditBalance)
                  : '--'
                : formatCredit(user.totalCredit)}
            </div>
            <p className="mt-3 text-sm text-white/70">
              {isApiAccount ? 'Tỷ lệ quy đổi được xác định khi tạo đơn.' : '1 Credit = 1.000đ'}
            </p>
          </div>
          <button
            className="inline-flex min-h-11 items-center justify-center rounded-md bg-white px-4 py-2 text-sm font-bold text-primary shadow-sm transition duration-200 hover:bg-primary-soft active:translate-y-px"
            onClick={() => {
              setStep('select');
              document.getElementById('topup')?.scrollIntoView({ behavior: 'smooth' });
            }}
          >
            Nạp Credit
          </button>
        </div>
        <div className="grid border-t border-white/15 bg-white/5 sm:grid-cols-2">
          <BalanceMetric
            label="Khả dụng"
            value={isApiAccount ? (wallet?.available ?? null) : user.availableCredit}
          />
          <BalanceMetric
            label="Đang giữ"
            value={isApiAccount ? (wallet?.heldCredit ?? null) : user.holdCredit}
            bordered
          />
        </div>
      </section>

      {isApiAccount ? <HeldFeesSection heldFees={heldFees} error={heldFeesError} /> : null}

      <section id="topup" className="mt-8 scroll-mt-24">
        {isApiAccount && loadPendingTopUp()?.orderCode ? (
          <Link
            className="mb-4 inline-block text-primary underline"
            to={`/credit?orderCode=${encodeURIComponent(loadPendingTopUp()!.orderCode)}`}
          >
            Mở lại đơn nạp gần nhất
          </Link>
        ) : null}
        {topUpError ? (
          <div className="mb-4">
            <Alert tone="error">{topUpError}</Alert>
          </div>
        ) : null}
        {step === 'select' ? (
          isApiAccount ? (
            <ApiTopupSelection
              amountVnd={apiAmountVnd}
              onAmountChange={setApiAmountVnd}
              onContinue={() => void continuePayment()}
              submitting={topUpSubmitting}
              processing={topUpProcessing}
            />
          ) : (
            <Alert tone="warning">
              Tài khoản demo không thể tạo giao dịch nạp Credit. Vui lòng đăng nhập bằng tài khoản
              Backend để thanh toán qua VNPAY.
            </Alert>
          )
        ) : null}
        {isApiAccount && step === 'pending' && apiTopUpOrder ? (
          <ApiTopUpOrder
            order={apiTopUpOrder}
            onNewTopup={() => {
              setApiTopUpOrder(null);
              setReturnState('idle');
              clearPendingTopUp();
              setTopUpError('');
              setStep('select');
            }}
          />
        ) : null}
      </section>

      {apiHistoryLoading ? (
        <div className="mt-8">
          <Alert>Đang tải lịch sử Credit...</Alert>
        </div>
      ) : null}
      {apiHistoryError ? (
        <div className="mt-8 space-y-3">
          <Alert tone="error">{apiHistoryError}</Alert>
          <Button variant="outline" onClick={() => setHistoryReloadKey((key) => key + 1)}>
            Thử tải lại
          </Button>
        </div>
      ) : null}

      <CreditHistorySection
        items={data.items}
        allHistory={allHistory}
        history={history}
        pendingTopups={
          historyFilter === 'all' || historyFilter === 'topup'
            ? topups.filter((t) => t.status === 'pending')
            : []
        }
        activeFilter={historyFilter}
        onFilterChange={setHistoryFilter}
        onSelect={setSelectedEntry}
        unavailable={false}
        dateFilter={
          isApiAccount ? (
            <HistoryDateFilter
              fromDate={fromDate}
              toDate={toDate}
              error={dateRangeError}
              onChange={changeDateRange}
            />
          ) : null
        }
        pagination={
          isApiAccount ? (
            <HistoryPagination
              page={historyPage}
              total={historyTotal}
              onPageChange={setHistoryPage}
            />
          ) : null
        }
      />
      {selectedEntry ? (
        <CreditTransactionDetail
          entry={selectedEntry}
          onClose={() => setSelectedEntry(null)}
          relatedTransactionId={selectedRelatedTransaction?.id}
          relatedItemTitle={selectedRelatedItem?.title}
          relatedTransactionType={selectedRelatedTransaction?.type}
          onViewTransaction={selectedRelatedTransaction ? () => navigate('/messages') : undefined}
        />
      ) : null}
    </div>
  );
}

function BalanceMetric({
  label,
  value,
  bordered = false,
}: {
  label: string;
  value: number | null;
  bordered?: boolean;
}) {
  return (
    <div
      className={`px-6 py-4 sm:px-8 lg:px-10 ${bordered ? 'border-t border-white/15 sm:border-l sm:border-t-0' : ''}`}
    >
      <p className="text-xs font-medium text-white/60">{label}</p>
      <p className="mt-1 text-xl font-bold tabular-nums">
        {value === null ? '--' : `${value} Credit`}
      </p>
    </div>
  );
}

function ApiTopupSelection({
  amountVnd,
  onAmountChange,
  onContinue,
  submitting,
  processing,
}: {
  amountVnd: string;
  onAmountChange: Dispatch<string>;
  onContinue: () => void;
  submitting: boolean;
  processing: boolean;
}) {
  const parsedAmount = Number(amountVnd);
  const validAmount = Number.isSafeInteger(parsedAmount) && parsedAmount > 0;
  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_340px]">
      <div className="rounded-xl bg-white p-5 ring-1 ring-border/80 sm:p-7">
        <h2 className="section-title">Nạp Credit</h2>
        <p className="mt-2 text-sm text-text-muted">
          Nhập số tiền muốn nạp. Số Credit quy đổi sẽ được hiển thị sau khi tạo đơn.
        </p>
        <div className="mt-6 max-w-sm">
          <Field
            label="Số tiền nạp (VND)"
            type="number"
            min={1}
            step={1}
            value={amountVnd}
            onChange={(event) => onAmountChange(event.target.value)}
            placeholder="Nhập số tiền"
            hint="Nhập số nguyên dương. Mức tối thiểu và bước tiền nạp được áp dụng theo quy định hiện hành."
          />
        </div>
      </div>
      <aside className="h-fit rounded-xl bg-white p-5 ring-1 ring-border/80 sm:p-6">
        <h2 className="text-base font-bold">Tóm tắt thanh toán</h2>
        <p className="mt-3 text-sm text-text-muted">
          Credit chỉ được cộng sau khi khoản thanh toán được xác nhận.
        </p>
        <Button
          className="mt-5 w-full"
          size="lg"
          onClick={onContinue}
          disabled={!validAmount || submitting}
        >
          {processing
            ? 'Đang xử lý thanh toán...'
            : submitting
              ? 'Đang tạo đơn...'
              : 'Tạo đơn nạp Credit'}
        </Button>
      </aside>
    </div>
  );
}

function ApiTopUpOrder({
  order,
  onNewTopup,
}: {
  order: TopUpOrderResponse;
  onNewTopup: () => void;
}) {
  const safeRedirectUrl = paymentRedirectUrl(order.redirectUrl);
  const paid = order.status === 'SUCCESS';
  return (
    <div className="rounded-xl bg-white px-5 py-8 ring-1 ring-border/80 sm:px-8">
      <div className="mx-auto max-w-2xl text-center">
        <span
          className={`mx-auto grid size-14 place-items-center rounded-lg ${paid ? 'bg-emerald-50 text-success' : 'bg-amber-50 text-warning'}`}
        >
          <Icon name={paid ? 'check' : 'history'} className="size-7" />
        </span>
        <h2 className="mt-5 text-2xl font-bold">
          {paid ? 'Nạp Credit thành công' : 'Đơn nạp Credit đã được tạo'}
        </h2>
        <p className="mt-2 text-sm leading-6 text-text-muted">
          {paid
            ? `+${order.credits} Credit đã được cộng vào ví.`
            : 'Credit chưa được cộng vào ví. Trạng thái sẽ thay đổi sau khi khoản thanh toán được xác nhận.'}
        </p>
        <dl className="mt-6 divide-y divide-border rounded-lg bg-surface-low px-4 text-left">
          <DetailRow label="Mã đơn" value={order.orderCode} />
          <DetailRow label="Số tiền" value={formatVnd(order.amountVnd)} />
          <DetailRow label="Credit dự kiến" value={`${order.credits} Credit`} />
          <DetailRow label="Cổng thanh toán" value={paymentProviderLabels[order.provider]} />
          <DetailRow label="Trạng thái" value={paymentStatusLabels[order.status]} />
        </dl>
        {order.provider === 'MOCK' && !paid ? (
          <p className="mt-4 rounded-md bg-amber-50 p-3 text-sm text-warning">
            Backend đang cấu hình cổng thanh toán thử nghiệm. Cần chuyển cấu hình Backend sang VNPAY
            để tiếp tục nạp Credit.
          </p>
        ) : null}
        <div className="mt-6 flex flex-wrap justify-center gap-2">
          <Button variant="outline" onClick={onNewTopup}>
            Tạo đơn nạp khác
          </Button>
          {order.provider === 'VNPAY' && !paid && safeRedirectUrl ? (
            <a
              href={safeRedirectUrl}
              className="inline-flex min-h-10 items-center justify-center rounded-md bg-primary px-4 py-2 text-sm font-bold text-white"
            >
              Quay lại cổng thanh toán
            </a>
          ) : null}
        </div>
      </div>
    </div>
  );
}

function CreditHistorySection({
  items,
  allHistory,
  history,
  pendingTopups,
  activeFilter,
  onFilterChange,
  onSelect,
  unavailable = false,
  dateFilter = null,
  pagination = null,
}: {
  items: Item[];
  allHistory: CreditHistory[];
  history: CreditHistory[];
  pendingTopups: Topup[];
  activeFilter: HistoryFilter;
  onFilterChange: Dispatch<HistoryFilter>;
  onSelect: (entry: CreditHistory | Topup) => void;
  unavailable?: boolean;
  dateFilter?: ReactNode;
  pagination?: ReactNode;
}) {
  const countFor = (filter: HistoryFilter) => {
    const pendingCount = filter === 'all' || filter === 'topup' ? pendingTopups.length : 0;
    if (filter === 'all') return allHistory.length + pendingCount;
    return (
      allHistory.filter((entry) => filterTypes[filter].includes(entry.type)).length + pendingCount
    );
  };
  const tabs: Array<[HistoryFilter, string]> = [
    ['all', 'Tất cả'],
    ['topup', 'Nạp Credit'],
    ['spend', 'Chi tiêu'],
  ];

  return (
    <section className="mt-10">
      <h2 className="section-title">Lịch sử Credit</h2>
      {dateFilter}
      {unavailable ? (
        <div className="mt-4 rounded-xl bg-white px-5 py-12 text-center text-sm text-text-muted ring-1 ring-border/80">
          Chưa thể tải lịch sử Credit.
        </div>
      ) : (
        <>
          <div className="mt-4 overflow-x-auto border-b border-border">
            <div className="flex min-w-max gap-1">
              {tabs.map(([id, label]) => (
                <button
                  key={id}
                  onClick={() => onFilterChange(id)}
                  className={`border-b-2 px-3 py-2.5 text-sm font-semibold ${activeFilter === id ? 'border-primary text-primary' : 'border-transparent text-text-muted hover:text-text-primary'}`}
                >
                  {label} ({countFor(id)})
                </button>
              ))}
            </div>
          </div>
          <div className="overflow-hidden rounded-b-xl bg-white ring-1 ring-border/80">
            {pendingTopups.map((topup) => (
              <HistoryRow
                key={topup.id}
                icon="wallet"
                title="Nạp Credit"
                transactionId={topup.code}
                date={topup.createdAt}
                amount={topup.amount}
                status="Đang xác nhận"
                tone="pending"
                onClick={() => onSelect(topup)}
              />
            ))}
            {history.map((entry) => {
              const relatedItem = items.find((item) => item.id === postFeeItemId(entry));
              return (
                <HistoryRow
                  key={entry.id}
                  icon={historyIcon(entry)}
                  title={historyTitle(entry, relatedItem?.title)}
                  transactionId={entry.transactionId}
                  date={entry.createdAt}
                  amount={entry.amount}
                  status={historyStatus(entry)}
                  tone={historyTone(entry)}
                  onClick={() => onSelect(entry)}
                />
              );
            })}
            {!pendingTopups.length && !history.length ? (
              <div className="px-5 py-12 text-center text-sm text-text-muted">
                Chưa có giao dịch phù hợp.
              </div>
            ) : null}
          </div>
          {pagination}
        </>
      )}
    </section>
  );
}

function HeldFeesSection({ heldFees, error }: { heldFees: HeldFeeItem[]; error: string }) {
  return (
    <section className="mt-8">
      <h2 className="section-title">Bài đang giữ phí</h2>
      <p className="mt-1 text-xs text-text-muted">
        Được trừ khi bài được duyệt, nhả lại nếu bài bị từ chối hoặc bị gỡ.
      </p>
      {error ? (
        <div className="mt-3">
          <Alert tone="error">{error}</Alert>
        </div>
      ) : null}
      {!error && !heldFees.length ? (
        <p className="mt-3 text-sm text-text-muted">Không có bài nào đang giữ phí.</p>
      ) : null}
      {heldFees.length ? (
        <ul className="mt-3 divide-y divide-border overflow-hidden rounded-xl bg-white ring-1 ring-border/80">
          {heldFees.map((heldFee) => (
            <li
              key={heldFee.itemId}
              className="flex items-center justify-between gap-3 px-4 py-3 text-sm"
            >
              <div className="min-w-0">
                <Link
                  className="block truncate font-semibold text-primary hover:underline"
                  to={`/items/${heldFee.itemId}`}
                >
                  {heldFee.title}
                </Link>
                <span className="text-xs text-text-muted">
                  {heldFee.feeType === 'POST' ? 'Phí đăng' : 'Phí sửa'}
                </span>
              </div>
              <span className="shrink-0 font-bold tabular-nums">
                {formatCredit(heldFee.pendingFee)}
              </span>
            </li>
          ))}
        </ul>
      ) : null}
    </section>
  );
}

function HistoryDateFilter({
  fromDate,
  toDate,
  error,
  onChange,
}: {
  fromDate: string;
  toDate: string;
  error: string;
  onChange: (fromDate: string, toDate: string) => void;
}) {
  const quickButtons: Array<[QuickRange, string]> = [
    ['7days', '7 ngày'],
    ['month', 'Tháng này'],
    ['quarter', 'Quý này'],
    ['all', 'Tất cả'],
  ];
  const dateInputClass = 'rounded-md border border-border bg-white px-2 py-1.5 text-sm';

  return (
    <div className="mt-4 space-y-3">
      <div className="flex flex-wrap gap-2">
        {quickButtons.map(([range, label]) => (
          <Button
            key={range}
            size="sm"
            variant="outline"
            onClick={() => onChange(...quickRangeDates(range))}
          >
            {label}
          </Button>
        ))}
      </div>
      <div className="flex flex-wrap items-center gap-3 text-sm">
        <label className="flex items-center gap-2">
          Từ ngày
          <input
            type="date"
            className={dateInputClass}
            value={fromDate}
            onChange={(event) => onChange(event.target.value, toDate)}
          />
        </label>
        <label className="flex items-center gap-2">
          Đến ngày
          <input
            type="date"
            className={dateInputClass}
            value={toDate}
            onChange={(event) => onChange(fromDate, event.target.value)}
          />
        </label>
      </div>
      {error ? <Alert tone="error">{error}</Alert> : null}
    </div>
  );
}

function HistoryPagination({
  page,
  total,
  onPageChange,
}: {
  page: number;
  total: number;
  onPageChange: (page: number) => void;
}) {
  const totalPages = Math.ceil(total / HISTORY_PAGE_SIZE);
  if (totalPages <= 1) return null;

  return (
    <div className="mt-4 flex items-center justify-between text-sm">
      <Button
        size="sm"
        variant="outline"
        disabled={page === 0}
        onClick={() => onPageChange(page - 1)}
      >
        Trang trước
      </Button>
      <span className="text-text-muted">
        Trang {page + 1} / {totalPages}
      </span>
      <Button
        size="sm"
        variant="outline"
        disabled={page + 1 >= totalPages}
        onClick={() => onPageChange(page + 1)}
      >
        Trang sau
      </Button>
    </div>
  );
}

function HistoryRow({
  icon,
  title,
  transactionId,
  date,
  amount,
  status,
  tone,
  onClick,
}: {
  icon: 'wallet' | 'payments' | 'history' | 'renew';
  title: string;
  transactionId: string;
  date: string;
  amount: number;
  status: string;
  tone: 'positive' | 'negative' | 'hold' | 'pending';
  onClick: () => void;
}) {
  const amountClass =
    tone === 'positive'
      ? 'text-success'
      : tone === 'negative'
        ? 'text-error'
        : tone === 'hold'
          ? 'text-warning'
          : 'text-text-secondary';
  const prefix = amount > 0 ? '+' : '';

  return (
    <button
      type="button"
      onClick={onClick}
      className="grid w-full cursor-pointer grid-cols-[1fr_auto_auto] gap-4 border-b border-border/70 px-4 py-4 text-left transition hover:bg-surface-low last:border-0 sm:px-5"
    >
      <div className="flex min-w-0 gap-3">
        <span className="mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-md bg-surface-low text-primary">
          <Icon name={icon} className="size-5" />
        </span>
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold text-text-primary">{title}</p>
          <p className="mt-1 truncate text-xs text-text-muted">
            {transactionId} · {new Date(date).toLocaleString('vi-VN')}
          </p>
        </div>
      </div>
      <div className="self-center text-right">
        <strong className={`block whitespace-nowrap text-sm tabular-nums ${amountClass}`}>
          {prefix}
          {amount} Credit
        </strong>
        <span className="mt-1 block text-xs text-text-muted">{status}</span>
      </div>
      <Icon name="chevronDown" className="-rotate-90 self-center text-text-muted" />
    </button>
  );
}

function historyTitle(entry: CreditHistory, relatedItemTitle?: string) {
  if (isPostFee(entry)) {
    const itemTitle =
      relatedItemTitle ??
      entry.description.replace(/^phi dang bai\s*-\s*/i, '').replace(/^phí đăng bài\s*-\s*/i, '');
    return `Phí đăng bài${itemTitle ? ` - ${itemTitle}` : ''}`;
  }
  if (entry.type === 'RECEIVE_FEE') return entry.description;
  const labels: Record<CreditHistoryType, string> = {
    TOPUP: 'Nạp Credit',
    TRANSACTION_FEE: 'Lịch sử cũ',
    SPEND: 'Chi tiêu',
    RECEIVE_FEE: 'Phí nhận đồ',
    AI_SPEND: 'Phí dịch vụ AI',
    HOLD: 'Lịch sử cũ',
    RELEASE_HOLD: 'Hoàn Credit',
    REFUND: 'Hoàn Credit',
    ADMIN_ADJUSTMENT: 'Cập nhật hệ thống',
  };
  return labels[entry.type];
}

function historyStatus(entry: CreditHistory) {
  if (entry.status === 'pending') return 'Đang xác nhận';
  if (entry.status === 'refunded') return 'Đã hoàn';
  return 'Hoàn tất';
}

function historyTone(entry: CreditHistory): 'positive' | 'negative' | 'hold' | 'pending' {
  if (entry.amount > 0) return 'positive';
  return 'negative';
}

function historyIcon(entry: CreditHistory): 'wallet' | 'payments' | 'history' | 'renew' {
  if (entry.type === 'TOPUP') return 'wallet';
  if (entry.type === 'REFUND' || entry.type === 'RELEASE_HOLD') return 'renew';
  return 'payments';
}

function CreditTransactionDetail({
  entry,
  relatedTransactionId,
  relatedItemTitle,
  relatedTransactionType,
  onClose,
  onViewTransaction,
}: {
  entry: CreditHistory | Topup;
  relatedTransactionId?: string;
  relatedItemTitle?: string;
  relatedTransactionType?: 'gift' | 'trade';
  onClose: () => void;
  onViewTransaction?: () => void;
}) {
  const isTopup = 'method' in entry;
  const historyEntry = isTopup ? undefined : entry;
  const typeLabel = isTopup
    ? 'Nạp Credit'
    : isPostFee(entry)
      ? 'Phí đăng bài'
      : historyTitle(entry, relatedItemTitle);
  const status = isTopup
    ? entry.status === 'pending'
      ? 'Đang xác nhận'
      : 'Hoàn tất'
    : historyStatus(entry);

  return (
    <div
      className="fixed inset-0 z-50 grid place-items-center bg-slate-950/45 p-4"
      onMouseDown={onClose}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="credit-detail-title"
        className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-xl bg-white p-6 shadow-2xl sm:p-7"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-4">
          <h2 id="credit-detail-title" className="text-xl font-bold text-text-primary">
            Chi tiết giao dịch Credit
          </h2>
          <button
            type="button"
            aria-label="Đóng"
            onClick={onClose}
            className="rounded-md p-1 text-text-muted hover:bg-surface-low hover:text-text-primary"
          >
            <Icon name="close" className="size-5" />
          </button>
        </div>
        <dl className="mt-6 divide-y divide-border rounded-lg bg-surface-low px-4">
          <DetailRow label="Loại giao dịch" value={typeLabel} />
          {isTopup ? (
            <>
              <DetailRow
                label="Số Credit"
                value={`+${entry.amount} Credit`}
                valueClass="text-success"
              />
              <DetailRow label="Số tiền thanh toán" value={formatVnd(entry.vnd)} />
            </>
          ) : (
            <>
              {isPostFee(entry) && relatedItemTitle ? (
                <DetailRow label="Món đồ" value={relatedItemTitle} />
              ) : null}
              <DetailRow
                label="Số Credit"
                value={`${entry.amount > 0 ? '+' : ''}${entry.amount} Credit`}
                valueClass={historyTone(entry) === 'negative' ? 'text-error' : 'text-success'}
              />
              <DetailRow label="Số dư trước" value={`${balanceBefore(entry)} Credit`} />
              <DetailRow label="Số dư sau" value={`${entry.balance} Credit`} />
            </>
          )}
          <DetailRow label="Thời gian" value={formatDateTime(entry.createdAt)} />
          <DetailRow label="Mã giao dịch" value={isTopup ? entry.code : entry.transactionId} />
          <DetailRow label="Trạng thái" value={status} />
        </dl>
        <div className="mt-5 rounded-lg border border-border p-4 text-sm">
          <p className="font-semibold text-text-primary">Nội dung liên quan</p>
          <p className="mt-2 text-text-secondary">
            {isTopup
              ? `Nạp ${entry.amount} Credit qua QR`
              : historyEntry?.note ||
                historyEntry?.description ||
                historyTitle(historyEntry!, relatedItemTitle)}
          </p>
          {relatedItemTitle ? (
            <p className="mt-1 text-text-secondary">Món đồ: {relatedItemTitle}</p>
          ) : null}
          {relatedTransactionType ? (
            <p className="mt-1 text-text-secondary">
              Hình thức: {relatedTransactionType === 'trade' ? 'Trao đổi' : 'Cho - Nhận'}
            </p>
          ) : null}
          {relatedTransactionId ? (
            <p className="mt-1 text-text-secondary">Giao dịch liên quan: {relatedTransactionId}</p>
          ) : null}
          {onViewTransaction ? (
            <Button className="mt-4" size="sm" onClick={onViewTransaction}>
              Xem giao dịch
            </Button>
          ) : null}
        </div>
      </div>
    </div>
  );
}

function DetailRow({
  label,
  value,
  valueClass = 'text-text-primary',
}: {
  label: string;
  value: string;
  valueClass?: string;
}) {
  return (
    <div className="flex items-center justify-between gap-4 py-3 text-sm">
      <dt className="text-text-muted">{label}</dt>
      <dd className={`text-right font-semibold ${valueClass}`}>{value}</dd>
    </div>
  );
}

function formatDateTime(value: string) {
  return new Date(value).toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}
