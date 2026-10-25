import { useEffect, useRef, useState, type Dispatch } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAppSelector } from '../../app/hooks';
import { selectCurrentUser } from '../../app/store';
import { Alert, Button, Field, Icon, PageHeader, StatusBadge } from '../../components/ui';
import {
  CreditHistorySection,
  CreditTransactionDetail,
  DetailRow,
  HistoryDateFilter,
  HistoryPagination,
} from '../../components/CreditHistoryView';
import { itemApi } from '../../services/itemApi';
import {
  HISTORY_PAGE_SIZE,
  endOfDayIso,
  itemTitleOf,
  startOfDayIso,
  toCreditHistory,
  userHistoryTabs,
} from '../../utils/creditHistory';
import type { CreditHistory } from '../../types/domain';
import { formatCredit, formatVnd } from '../../utils/formatting';
import { useWallet } from '../../app/useWallet';
import { walletApi, type HeldFeeItem } from '../../services/walletApi';
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
type ReturnState = 'idle' | 'pending' | 'failed' | 'invalid' | 'checking' | 'success';

const paymentStatusLabels: Record<PaymentOrderStatus, string> = {
  PENDING: 'Đang chờ thanh toán',
  SUCCESS: 'Đã cộng Credit',
  FAILED: 'Thanh toán thất bại',
  EXPIRED: 'Đơn đã hết hạn',
};

const paymentProviderLabels: Record<PaymentProvider, string> = {
  VNPAY: 'VNPAY',
  MOCK: 'Thanh toán thử nghiệm',
  MANUAL: 'QR minh họa, quản trị viên duyệt',
};

const topUpAmountInvalidMessage =
  'Số tiền nạp chưa hợp lệ. Theo giá trị mặc định, số tiền cần chia hết cho 1.000đ và tối thiểu 10.000đ. Các mức này có thể thay đổi theo cấu hình hệ thống.';

function topUpCreateErrorMessage(error: unknown) {
  const apiError = error as Partial<ApiError>;
  if (apiError.code === 'WALLET_TOPUP_AMOUNT_INVALID') return topUpAmountInvalidMessage;
  if (typeof apiError.message === 'string' && apiError.message) return apiError.message;
  return 'Không tạo được đơn nạp Credit. Vui lòng thử lại.';
}

export function Credit() {
  const user = useAppSelector(selectCurrentUser)!;
  const navigate = useNavigate();
  const location = useLocation();
  const [apiAmountVnd, setApiAmountVnd] = useState('');
  const [step, setStep] = useState<Step>('select');
  const [historyTab, setHistoryTab] = useState('all');
  const [selectedEntry, setSelectedEntry] = useState<CreditHistory | null>(null);
  const {
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
  const [itemTitles, setItemTitles] = useState<Map<string, string>>(() => new Map());
  const [heldFees, setHeldFees] = useState<HeldFeeItem[]>([]);
  const [heldFeesLoading, setHeldFeesLoading] = useState(true);
  const [heldFeesError, setHeldFeesError] = useState('');
  const [heldFeesReloadKey, setHeldFeesReloadKey] = useState(0);
  const [apiTopUpOrder, setApiTopUpOrder] = useState<TopUpOrderResponse | null>(null);
  const [topUpSubmitting, setTopUpSubmitting] = useState(false);
  const [topUpProcessing, setTopUpProcessing] = useState(false);
  const [topUpError, setTopUpError] = useState('');
  const [returnState, setReturnState] = useState<ReturnState>('idle');
  const [orderCheckKey, setOrderCheckKey] = useState(0);
  const createTopUpInFlight = useRef(false);

  const completedCredits = returnState === 'success' ? (apiTopUpOrder?.credits ?? null) : null;

  // Tên món chỉ để hiển thị cho dễ nhìn nên gọi một lần; lỗi thì bỏ qua, lịch sử vẫn xem được không có tên.
  useEffect(() => {
    let active = true;
    itemApi
      .mine()
      .then((items) => {
        if (active) setItemTitles(new Map(items.map((item) => [String(item.id), item.title])));
      })
      .catch(() => {
        if (active) setItemTitles(new Map());
      });
    return () => {
      active = false;
    };
  }, []);

  // Từ ngày sau Đến ngày thì báo lỗi và không gọi API.
  const dateRangeError =
    fromDate && toDate && fromDate > toDate ? '"Từ ngày" không được sau "Đến ngày".' : '';

  // Danh sách bài đang giữ phí tải lại mỗi khi số dư ví được tải lại (wallet đổi object).
  useEffect(() => {
    let active = true;
    setHeldFeesLoading(true);
    setHeldFeesError('');
    walletApi
      .listHeldFees()
      .then((response) => {
        if (!active) return;
        setHeldFees(response);
      })
      .catch((caught) => {
        if (!active) return;
        setHeldFees([]);
        setHeldFeesError((caught as ApiError).message || 'Không tải được danh sách bài đang giữ phí.');
      })
      .finally(() => {
        if (active) setHeldFeesLoading(false);
      });
    return () => {
      active = false;
    };
  }, [heldFeesReloadKey, wallet]);

  useEffect(() => {
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
  }, [historyReloadKey, user.id, fromDate, toDate, historyPage, dateRangeError]);

  // Đổi bộ lọc ngày thì quay về trang đầu.
  const changeDateRange = (nextFrom: string, nextTo: string) => {
    setFromDate(nextFrom);
    setToDate(nextTo);
    setHistoryPage(0);
  };

  useEffect(() => {
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
  }, [location.search, navigate, refreshWallet, orderCheckKey]);

  const selectedItemTitle = selectedEntry ? itemTitleOf(selectedEntry, itemTitles) : undefined;

  const continuePayment = async () => {
    if (createTopUpInFlight.current) return;
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

      {returnState === 'pending' ? (
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
      {returnState === 'failed' ? (
        <div className="mb-6">
          <Alert tone="error">Thanh toán không thành công. Credit chưa được cộng vào ví.</Alert>
        </div>
      ) : null}
      {returnState === 'invalid' ? (
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
              {wallet ? formatCredit(wallet.creditBalance) : '--'}
            </div>
            <p className="mt-3 text-sm text-white/70">
              Tỷ lệ quy đổi được xác định khi tạo đơn.
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
            value={wallet?.available ?? null}
          />
          <BalanceMetric
            label="Đang giữ"
            value={wallet?.heldCredit ?? null}
            bordered
          />
        </div>
      </section>

      <HeldFeesSection
        heldFees={heldFees}
        loading={heldFeesLoading}
        error={heldFeesError}
        onRetry={() => setHeldFeesReloadKey((key) => key + 1)}
      />

      <section id="topup" className="mt-8 scroll-mt-24">
        {loadPendingTopUp()?.orderCode ? (
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
          <ApiTopupSelection
            amountVnd={apiAmountVnd}
            onAmountChange={setApiAmountVnd}
            onContinue={() => void continuePayment()}
            submitting={topUpSubmitting}
            processing={topUpProcessing}
          />
        ) : null}
        {step === 'pending' && apiTopUpOrder ? (
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
        itemTitles={itemTitles}
        allHistory={apiHistory}
        tabs={userHistoryTabs}
        activeTab={historyTab}
        onTabChange={setHistoryTab}
        onSelect={setSelectedEntry}
        unavailable={false}
        dateFilter={
          <HistoryDateFilter
            fromDate={fromDate}
            toDate={toDate}
            error={dateRangeError}
            onChange={changeDateRange}
          />
        }
        pagination={
          <HistoryPagination page={historyPage} total={historyTotal} onPageChange={setHistoryPage} />
        }
      />
      {selectedEntry ? (
        <CreditTransactionDetail
          entry={selectedEntry}
          onClose={() => setSelectedEntry(null)}
          relatedItemTitle={selectedItemTitle}
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
            {backendMockProviderMessage}
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

const heldFeeOfferTypeLabels: Record<HeldFeeItem['offerType'], string> = {
  GIVE: 'Cho tặng',
  SWAP: 'Trao đổi',
};

const heldFeeTypeLabels: Record<HeldFeeItem['feeType'], string> = {
  POST: 'Phí đăng',
  EDIT: 'Phí sửa',
};

export function HeldFeesSection({
  heldFees,
  loading,
  error,
  onRetry,
}: {
  heldFees: HeldFeeItem[];
  loading: boolean;
  error: string;
  onRetry: () => void;
}) {
  return (
    <section className="mt-8">
      <h2 className="section-title">Bài đang giữ phí</h2>
      <p className="mt-1 text-xs text-text-muted">
        Được trừ khi bài được duyệt, nhả lại nếu bài bị từ chối hoặc bị gỡ.
      </p>
      {loading ? (
        <div className="mt-3" role="status">
          <Alert>Đang tải danh sách Credit đang giữ...</Alert>
        </div>
      ) : null}
      {!loading && error ? (
        <div className="mt-3 space-y-3">
          <Alert tone="error">{error}</Alert>
          <Button size="sm" variant="outline" onClick={onRetry}>
            Thử lại
          </Button>
        </div>
      ) : null}
      {!loading && !error && !heldFees.length ? (
        <p className="mt-3 text-sm text-text-muted">Không có bài nào đang giữ phí.</p>
      ) : null}
      {!loading && !error && heldFees.length ? (
        <ul className="mt-3 divide-y divide-border overflow-hidden rounded-xl bg-white ring-1 ring-border/80">
          {heldFees.map((heldFee) => (
            <li
              key={heldFee.itemId}
              className="flex flex-col gap-3 px-4 py-4 text-sm sm:flex-row sm:items-center sm:justify-between"
            >
              <div className="min-w-0">
                <Link
                  className="block truncate font-semibold text-primary hover:underline"
                  to={`/items/${heldFee.itemId}`}
                >
                  {heldFee.title}
                </Link>
                <div className="mt-2 flex flex-wrap items-center gap-2">
                  <span className="inline-flex rounded-sm bg-primary-soft px-2 py-1 text-[11px] font-bold text-primary">
                    {heldFeeOfferTypeLabels[heldFee.offerType]}
                  </span>
                  <StatusBadge status={heldFee.status} />
                  <span className="text-xs text-text-muted">
                    {heldFeeTypeLabels[heldFee.feeType]}
                  </span>
                </div>
              </div>
              <div className="shrink-0 sm:text-right">
                <p className="text-xs text-text-muted">Credit đang giữ</p>
                <p className="mt-1 font-bold tabular-nums">{formatCredit(heldFee.pendingFee)}</p>
              </div>
            </li>
          ))}
        </ul>
      ) : null}
    </section>
  );
}




