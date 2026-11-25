import { useEffect, useRef, useState, type Dispatch } from 'react';
import { useRealtimeRefresh } from '../../utils/useRealtimeRefresh';
import { Link, useLocation } from 'react-router-dom';
import { useAppSelector } from '../../app/hooks';
import { selectCurrentUser } from '../../app/store';
import { Dialog } from '../../components/Dialog';
import { Pagination } from '../../components/Pagination';
import { Alert, Button, Field, PageHeader, StatusBadge } from '../../components/ui';
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
  summarizeCreditHistory,
  toCreditHistory,
  userHistoryTabs,
  type CreditHistorySummary,
} from '../../utils/creditHistory';
import type { CreditHistory } from '../../types/domain';
import { formatCredit, formatVnd } from '../../utils/formatting';
import { useWallet } from '../../app/useWallet';
import { walletApi, type HeldFeeItem, type WalletHistoryCounts } from '../../services/walletApi';
import {
  paymentApi,
  TOPUP_PENDING_EXISTS,
  type TopUpOrderDetailResponse,
  type PaymentOrderStatus,
  type PaymentProvider,
  type TopUpOrderResponse,
} from '../../services/paymentApi';
import type { ApiError } from '../../services/http';
import { backendMockProviderMessage } from '../../services/paymentFlow';
import { toast } from '../../components/toast';
import {
  clearPendingTopUp,
  loadPendingTopUp,
  paymentRedirectUrl,
  resolveTopUpReturnResult,
  returnOrderCode,
  savePendingTopUp,
  vnpayReturnStatus,
} from '../../services/topUpSession';

type Step = 'select' | 'pending';
type ReturnState =
  | 'idle'
  | 'pending'
  | 'cancelled'
  | 'failed'
  | 'expired'
  | 'pending-after-failed-return'
  | 'invalid'
  | 'checking'
  | 'success';

const PAYMENT_CANCELLED_MESSAGE = 'Bạn đã hủy yêu cầu thanh toán.';
const PAYMENT_PENDING_AFTER_FAILED_RETURN_MESSAGE =
  'Thanh toán vừa thất bại tại VNPay, nhưng đơn vẫn đang chờ hệ thống xác nhận hủy. Credit chưa được cộng vào ví.';
// Hỏi lại trạng thái đơn đang chờ mỗi 4 giây (và khi người dùng quay lại tab) để biết VNPay đã báo kết quả chưa.
const ORDER_STATUS_POLL_INTERVAL_MS = 4_000;

/** Độ lệch giữa giờ server và giờ máy (ms); dương khi server chạy trước. Không có serverTime thì coi như 0. */
function clockOffsetFrom(serverTime: string | null | undefined): number {
  const server = serverTime ? Date.parse(serverTime) : Number.NaN;
  return Number.isFinite(server) ? server - Date.now() : 0;
}

const paymentStatusLabels: Record<PaymentOrderStatus, string> = {
  PENDING: 'Đang chờ thanh toán',
  SUCCESS: 'Đã cộng Credit',
  FAILED: 'Thanh toán thất bại',
  EXPIRED: 'Đơn đã hết hạn',
  CANCELLED: 'Đã hủy',
};

function paymentProviderLabel(provider: PaymentProvider) {
  if (provider === 'VNPAY') return 'VNPAY';
  if (provider === 'MOCK') return 'Thanh toán thử nghiệm';
  return 'Không khả dụng';
}

// Mức chọn nhanh chỉ điền sẵn ô số tiền; backend vẫn kiểm tra mức tối thiểu và bước tiền.
const QUICK_TOPUP_AMOUNTS = [20_000, 50_000, 100_000, 200_000];

const topUpAmountInvalidMessage =
  'Số tiền nạp chưa hợp lệ. Theo giá trị mặc định, số tiền tối thiểu 10.000đ. Các mức này có thể thay đổi theo cấu hình hệ thống.';

function topUpCreateErrorMessage(error: unknown) {
  const apiError = error as Partial<ApiError>;
  if (apiError.code === 'WALLET_TOPUP_AMOUNT_INVALID') return topUpAmountInvalidMessage;
  if (typeof apiError.message === 'string' && apiError.message) return apiError.message;
  return 'Không tạo được đơn nạp Credit. Vui lòng thử lại.';
}

export function Credit() {
  const user = useAppSelector(selectCurrentUser)!;
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
  const [historyCounts, setHistoryCounts] = useState<WalletHistoryCounts | null>(null);
  const [historySummary, setHistorySummary] = useState<CreditHistorySummary | null>(null);
  const [itemTitles, setItemTitles] = useState<Map<string, string>>(() => new Map());
  const [heldFees, setHeldFees] = useState<HeldFeeItem[]>([]);
  const [heldFeesLoading, setHeldFeesLoading] = useState(true);
  const [heldFeesError, setHeldFeesError] = useState('');
  const [heldFeesReloadKey, setHeldFeesReloadKey] = useState(0);
  useRealtimeRefresh(['WALLET'], () => {
    setHistoryReloadKey((value) => value + 1);
    setHeldFeesReloadKey((value) => value + 1);
  });
  const [apiTopUpOrder, setApiTopUpOrder] = useState<TopUpOrderResponse | null>(null);
  const [topUpSubmitting, setTopUpSubmitting] = useState(false);
  const [topUpProcessing, setTopUpProcessing] = useState(false);
  const [topUpError, setTopUpError] = useState('');
  const [returnState, setReturnState] = useState<ReturnState>('idle');
  const [cancellingOrder, setCancellingOrder] = useState(false);
  // Độ lệch đồng hồ đo ở lần đọc đơn gần nhất, để đếm ngược theo giờ server chứ không theo giờ máy người dùng.
  const [clockOffsetMs, setClockOffsetMs] = useState(0);
  // Trình duyệt chặn mở tab VNPay: hiện nút để người dùng tự bấm mở.
  const [paymentTabBlocked, setPaymentTabBlocked] = useState(false);
  // Hộp thoại khi còn đơn đang chờ mà người dùng muốn tạo đơn mới: không âm thầm bỏ đơn cũ.
  const [pendingPrompt, setPendingPrompt] = useState<{
    order: TopUpOrderResponse;
    amountVnd: number | null;
  } | null>(null);
  // Tăng để mục "Lịch sử nạp" tải lại sau mỗi lần tạo, hủy hoặc đơn đổi trạng thái.
  const [topUpHistoryKey, setTopUpHistoryKey] = useState(0);
  // Mã đơn đang theo dõi (đơn PENDING trên thẻ): vòng hỏi trạng thái đọc lại đúng đơn này.
  const currentOrderCodeRef = useRef<string | null>(null);
  const cancelInFlight = useRef(false);
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
        setHistoryCounts(response.counts);
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

  // Tổng nạp, chi, hoàn trong khoảng ngày đang lọc: tải đủ mọi trang rồi cộng lại để hiển thị.
  useEffect(() => {
    if (dateRangeError) return;
    let active = true;
    setHistorySummary(null);
    walletApi
      .listAllTransactions({
        from: fromDate ? startOfDayIso(fromDate) : undefined,
        to: toDate ? endOfDayIso(toDate) : undefined,
      })
      .then((entries) => {
        if (active) setHistorySummary(summarizeCreditHistory(entries.map((entry) => toCreditHistory(entry, user.id))));
      })
      .catch(() => {
        if (active) setHistorySummary(null);
      });
    return () => {
      active = false;
    };
  }, [historyReloadKey, user.id, fromDate, toDate, dateRangeError]);

  // Đổi bộ lọc ngày thì quay về trang đầu.
  const changeDateRange = (nextFrom: string, nextTo: string) => {
    setFromDate(nextFrom);
    setToDate(nextTo);
    setHistoryPage(0);
  };

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const pendingSession = loadPendingTopUp();
    const returnCode = returnOrderCode(params);
    const code = returnCode ?? currentOrderCodeRef.current ?? pendingSession?.orderCode ?? null;
    const gatewayReturnStatus = vnpayReturnStatus(params);
    if (!code) {
      setTopUpError('');
      if (gatewayReturnStatus === 'INVALID_SIGNATURE') {
        setReturnState('invalid');
        clearPendingTopUp();
        return;
      }
      // Không có đơn trong URL hay phiên: hỏi server đơn đang chờ thanh toán của mình, để đơn tạo trước đó (tab khác,
      // lần vào trước) không bị "mất" khỏi trang.
      let restoreActive = true;
      paymentApi
        .listOrders({ status: 'PENDING', page: 0, size: 1 })
        .then((page) => {
          const pending = page.items[0];
          if (!restoreActive || !pending) return;
          setClockOffsetMs(clockOffsetFrom(pending.serverTime));
          setApiTopUpOrder({ ...pending, redirectUrl: pending.paymentUrl ?? '' });
          setReturnState('idle');
          setStep('pending');
        })
        .catch(() => {
          // Không tải được thì vẫn cho tạo đơn; Backend vẫn chặn đơn thứ hai bằng 409.
        });
      return () => {
        restoreActive = false;
      };
    }
    let active = true;
    setReturnState((current) => (current === 'idle' ? 'checking' : current));
    paymentApi
      .getOrder(code)
      .then((order) => {
        if (!active) return;
        const savedRedirectUrl =
          order.status === 'PENDING' && pendingSession?.orderCode === code
            ? (pendingSession.redirectUrl ?? '')
            : '';
        setClockOffsetMs(clockOffsetFrom(order.serverTime));
        setApiTopUpOrder({ ...order, redirectUrl: order.paymentUrl ?? savedRedirectUrl });
        setStep('pending');
        const result = resolveTopUpReturnResult(order.status, gatewayReturnStatus);
        setReturnState(result);
        if (result === 'success') {
          clearPendingTopUp();
          refreshWallet();
          setHistoryReloadKey((key) => key + 1);
        } else if (result === 'cancelled') {
          clearPendingTopUp();
          if (returnCode) toast.warning(PAYMENT_CANCELLED_MESSAGE);
        } else if (result === 'failed' || result === 'expired') {
          clearPendingTopUp();
        }
      })
      .catch((caught: ApiError) => {
        if (active) {
          setReturnState('idle');
          if (caught.status === 404) {
            clearPendingTopUp();
            setTopUpError('');
          } else {
            setTopUpError(caught.message || 'Không tải được đơn nạp.');
          }
        }
      });
    return () => {
      active = false;
    };
  }, [location.search, refreshWallet, orderCheckKey]);

  // Đơn còn PENDING (đang thanh toán ở tab VNPay, hoặc Return về trước IPN): hỏi lại trạng thái mỗi 4 giây và ngay khi
  // người dùng quay lại tab này. Hết PENDING thì dừng; chỉ lúc SUCCESS mới tải lại số dư ví (effect đọc đơn ở trên).
  // Mỗi lần đổi trạng thái effect dọn timer cũ nên không có hai vòng hỏi chạy song song.
  const waitingForPayment = step === 'pending' && apiTopUpOrder?.status === 'PENDING';
  useEffect(() => {
    if (!waitingForPayment) return;
    const checkNow = () => setOrderCheckKey((key) => key + 1);
    const timer = window.setInterval(checkNow, ORDER_STATUS_POLL_INTERVAL_MS);
    const onVisible = () => {
      if (document.visibilityState === 'visible') checkNow();
    };
    window.addEventListener('focus', checkNow);
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      window.clearInterval(timer);
      window.removeEventListener('focus', checkNow);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [waitingForPayment]);

  const currentOrderCode = apiTopUpOrder?.orderCode ?? null;
  const currentOrderStatus = apiTopUpOrder?.status ?? null;
  useEffect(() => {
    currentOrderCodeRef.current = currentOrderStatus === 'PENDING' ? currentOrderCode : null;
    setTopUpHistoryKey((key) => key + 1);
  }, [currentOrderCode, currentOrderStatus]);

  // Đồng hồ về 0: đơn hết hạn thanh toán, rời khỏi mục "đang chờ" và sang lịch sử; hỏi lại server để lấy trạng thái
  // cuối (server chuyển EXPIRED sau dung sai chờ IPN). FE không tự cộng hay trừ gì.
  const handleDeadlineReached = () => {
    clearPendingTopUp();
    currentOrderCodeRef.current = null;
    setApiTopUpOrder(null);
    setPaymentTabBlocked(false);
    setReturnState('idle');
    setStep('select');
    setTopUpHistoryKey((key) => key + 1);
    toast.warning('Đơn nạp đã hết hạn thanh toán. Bạn có thể tạo đơn mới.');
  };

  // "Tạo đơn nạp mới" trên thẻ: còn đơn đang chờ thì hỏi trước, không âm thầm bỏ đơn cũ.
  const requestNewTopUp = () => {
    if (apiTopUpOrder && isStillPayable(apiTopUpOrder, clockOffsetMs)) {
      setPendingPrompt({ order: apiTopUpOrder, amountVnd: null });
      return;
    }
    setApiTopUpOrder(null);
    setPaymentTabBlocked(false);
    setReturnState('idle');
    clearPendingTopUp();
    setTopUpError('');
    setStep('select');
  };

  const continueOldOrder = () => {
    if (!pendingPrompt) return;
    const url = paymentRedirectUrl(pendingPrompt.order.redirectUrl);
    setApiTopUpOrder(pendingPrompt.order);
    setStep('pending');
    setPendingPrompt(null);
    if (url) {
      const tab = window.open(url, '_blank', 'noopener,noreferrer');
      setPaymentTabBlocked(!tab);
    }
  };

  const replaceOldOrder = async () => {
    if (!pendingPrompt) return;
    const { order, amountVnd } = pendingPrompt;
    setPendingPrompt(null);
    if (amountVnd !== null) {
      // Có số tiền: Backend hủy đơn cũ và tạo đơn mới trong cùng một transaction.
      await startTopUp(amountVnd, true);
      return;
    }
    // Bấm từ thẻ (chưa nhập số tiền): hủy đơn cũ rồi đưa người dùng về form để nhập số tiền mới.
    const cancelled = await cancelTopUpOrder(order.orderCode, { silent: true });
    if (cancelled) {
      setApiTopUpOrder(null);
      setReturnState('idle');
      setStep('select');
      toast.success('Đã hủy đơn cũ. Hãy nhập số tiền cho đơn nạp mới.');
    }
  };

  // "Xác nhận hủy": Backend hủy ngay và trả trạng thái mới của đơn, nên cập nhật giao diện theo response luôn,
  // không cần "Kiểm tra lại". Không tải lại ví: đơn chưa thanh toán không đổi số dư.
  const cancelTopUpOrder = async (
    orderCode: string,
    { silent = false }: { silent?: boolean } = {},
  ): Promise<boolean> => {
    if (cancelInFlight.current) return false;
    cancelInFlight.current = true;
    setCancellingOrder(true);
    try {
      const order = await paymentApi.cancelOrder(orderCode);
      setClockOffsetMs(clockOffsetFrom(order.serverTime));
      setApiTopUpOrder({ ...order, redirectUrl: '' });
      setTopUpHistoryKey((key) => key + 1);
      if (order.status === 'CANCELLED' || order.status === 'FAILED') {
        clearPendingTopUp();
        setReturnState('cancelled');
        if (!silent) toast.success('Đã hủy đơn nạp Credit.');
        return true;
      } else {
        // Đơn đã kịp SUCCESS hoặc EXPIRED trước khi hủy: hiện đúng trạng thái Backend trả.
        const result = resolveTopUpReturnResult(order.status, null);
        setReturnState(result);
        if (result === 'success') {
          clearPendingTopUp();
          refreshWallet();
          setHistoryReloadKey((key) => key + 1);
        } else if (result === 'expired') {
          clearPendingTopUp();
        }
      }
      return false;
    } catch (caught) {
      const apiError = caught as Partial<ApiError>;
      toast.error(apiError.message || 'Không hủy được đơn nạp. Vui lòng thử lại.');
      return false;
    } finally {
      cancelInFlight.current = false;
      setCancellingOrder(false);
    }
  };

  const showPendingConflict = async (amountVnd: number) => {
    try {
      const page = await paymentApi.listOrders({ status: 'PENDING', page: 0, size: 1 });
      const pending = page.items[0];
      if (!pending) {
        setTopUpError('Đơn đang chờ vừa kết thúc. Vui lòng bấm tạo đơn lại.');
        return;
      }
      const order: TopUpOrderResponse = { ...pending, redirectUrl: pending.paymentUrl ?? '' };
      setClockOffsetMs(clockOffsetFrom(pending.serverTime));
      setApiTopUpOrder(order);
      setStep('pending');
      setPendingPrompt({ order, amountVnd });
    } catch {
      setTopUpError('Bạn còn một đơn nạp đang chờ thanh toán. Vui lòng tải lại trang để xem đơn đó.');
    }
  };

  const selectedItemTitle = selectedEntry ? itemTitleOf(selectedEntry, itemTitles) : undefined;

  const continuePayment = async () => {
    const amountVnd = Number(apiAmountVnd);
    if (!Number.isSafeInteger(amountVnd) || amountVnd <= 0) {
      setTopUpError('Vui lòng nhập số tiền nạp hợp lệ.');
      return;
    }
    // Còn đơn đang chờ thanh toán: hỏi tiếp tục đơn cũ hay hủy đơn cũ & tạo đơn mới.
    if (apiTopUpOrder && isStillPayable(apiTopUpOrder, clockOffsetMs)) {
      setPendingPrompt({ order: apiTopUpOrder, amountVnd });
      return;
    }
    await startTopUp(amountVnd, false);
  };

  const startTopUp = async (amountVnd: number, replacePending: boolean) => {
    if (createTopUpInFlight.current) return;
    createTopUpInFlight.current = true;
    setTopUpSubmitting(true);
    setTopUpProcessing(false);
    setTopUpError('');
    setPaymentTabBlocked(false);
    // Mở tab trống NGAY trong lúc bấm (trình duyệt chỉ cho mở tab mới khi đang xử lý cú bấm), có URL VNPay rồi mới
    // điền vào. Tab ShareLoop ở lại để hiện đồng hồ và hỏi trạng thái đơn.
    const paymentTab = openBlankPaymentTab();
    try {
      const order = await paymentApi.createTopUpOrder(
        replacePending ? { amountVnd, replacePending: true } : { amountVnd },
      );
      setTopUpHistoryKey((key) => key + 1);
      savePendingTopUp(order);
      setClockOffsetMs(clockOffsetFrom(order.serverTime));
      setApiTopUpOrder(order);
      setReturnState('idle');
      setStep('pending');
      if (order.provider === 'MOCK') {
        paymentTab?.close();
        setTopUpError(backendMockProviderMessage);
        return;
      }
      const redirectUrl = paymentRedirectUrl(order.redirectUrl);
      if (!redirectUrl) {
        paymentTab?.close();
        setTopUpError(
          'Không thể mở cổng thanh toán vì địa chỉ chuyển hướng không hợp lệ. Vui lòng thử lại sau.',
        );
        return;
      }
      if (paymentTab && !paymentTab.closed) {
        paymentTab.location.href = redirectUrl;
      } else {
        setPaymentTabBlocked(true);
      }
    } catch (caught) {
      paymentTab?.close();
      if ((caught as Partial<ApiError>).code === TOPUP_PENDING_EXISTS) {
        // Backend báo còn đơn đang chờ (vd tạo ở tab khác): lấy đơn đó về rồi hỏi người dùng.
        await showPendingConflict(amountVnd);
      } else {
        setTopUpError(topUpCreateErrorMessage(caught));
      }
    } finally {
      createTopUpInFlight.current = false;
      setTopUpSubmitting(false);
      setTopUpProcessing(false);
    }
  };

  return (
    <div className="page-shell">
      <PageHeader
        title="Ví Credit"
        description="Credit dùng để đăng bài Trao đổi, gia hạn, đẩy bài và tìm bằng trợ lý. Xin đồ và trao đổi luôn miễn phí."
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

      {returnState === 'pending' && returnOrderCode(new URLSearchParams(location.search)) ? (
        <div className="mb-6 space-y-3">
          <Alert>Đơn đang chờ xác nhận từ hệ thống. Credit chưa được cộng cho giao dịch này.</Alert>
          <Button
            variant="outline"
            onClick={() => {
              setOrderCheckKey((key) => key + 1);
            }}
          >
            Kiểm tra lại
          </Button>
        </div>
      ) : null}
      {returnState === 'pending-after-failed-return' ? (
        <div className="mb-6 space-y-3">
          <Alert tone="warning">{PAYMENT_PENDING_AFTER_FAILED_RETURN_MESSAGE}</Alert>
          <Button
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
      {returnState === 'expired' ? (
        <div className="mb-6">
          <Alert tone="warning">Đơn thanh toán đã hết hạn. Credit chưa được cộng vào ví.</Alert>
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
        <div className="mb-6 rounded-lg border border-success/20 bg-success-soft px-4 py-3 text-success">
          <div>
            <p className="text-sm font-bold">Nạp thành công +{completedCredits} Credit</p>
            <p className="mt-0.5 text-xs leading-5">Số dư ví đã được cập nhật từ hệ thống.</p>
          </div>
        </div>
      ) : null}

      <div className="grid gap-5 lg:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]">
        <section aria-label="Số dư" className="flex flex-col gap-5 rounded-xl bg-primary p-6 text-white">
          <div>
            <p className="text-sm text-primary-fixed">Khả dụng</p>
            <p className="mt-1 text-4xl font-extrabold tabular-nums">
              {wallet ? wallet.available : '--'} <span className="text-lg font-semibold">Credit</span>
            </p>
          </div>
          <div className="grid grid-cols-2 gap-3 border-t border-white/20 pt-4">
            <BalanceMetric label="Đang giữ" value={wallet?.heldCredit ?? null} note="Phí của bài đang chờ duyệt" />
            <BalanceMetric label="Tổng" value={wallet?.creditBalance ?? null} note="Khả dụng cộng đang giữ" />
          </div>
        </section>

        <section id="topup" aria-label="Nạp Credit" className="scroll-mt-24">
          {topUpError ? (
            <div className="mb-3">
              <Alert tone="error">{topUpError}</Alert>
            </div>
          ) : null}
          {step === 'pending' && apiTopUpOrder ? (
            <ApiTopUpOrder
              order={apiTopUpOrder}
              returnState={returnState}
              cancelling={cancellingOrder}
              clockOffsetMs={clockOffsetMs}
              paymentTabBlocked={paymentTabBlocked}
              onDeadlineReached={handleDeadlineReached}
              onCancel={() => void cancelTopUpOrder(apiTopUpOrder.orderCode)}
              onNewTopup={requestNewTopUp}
            />
          ) : null}
          {/* Đơn đang chờ hiện ở thẻ phía trên, form vẫn dùng được; tạo đơn khi còn đơn chờ sẽ hỏi trước. */}
          {step === 'select' || apiTopUpOrder?.status === 'PENDING' ? (
            <div className={step === 'pending' ? 'mt-5' : ''}>
              <ApiTopupSelection
                amountVnd={apiAmountVnd}
                onAmountChange={setApiAmountVnd}
                onContinue={() => void continuePayment()}
                submitting={topUpSubmitting}
                processing={topUpProcessing}
              />
            </div>
          ) : null}
        </section>
      </div>

      {pendingPrompt ? (
        <PendingTopUpDialog
          order={pendingPrompt.order}
          busy={topUpSubmitting || cancellingOrder}
          onContinue={continueOldOrder}
          onReplace={() => void replaceOldOrder()}
          onClose={() => setPendingPrompt(null)}
        />
      ) : null}

      <TopUpHistorySection reloadKey={topUpHistoryKey} />

      <HeldFeesSection
        heldFees={heldFees}
        loading={heldFeesLoading}
        error={heldFeesError}
        onRetry={() => setHeldFeesReloadKey((key) => key + 1)}
      />

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
        tabCounts={
          historyCounts
            ? { all: historyCounts.all, topup: historyCounts.topUp, spend: historyCounts.spend }
            : undefined
        }
        activeTab={historyTab}
        onTabChange={setHistoryTab}
        onSelect={setSelectedEntry}
        unavailable={false}
        summary={<CreditSummary summary={historySummary} held={wallet?.heldCredit ?? null} />}
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

/** Bốn ô tổng của khoảng ngày đang lọc. "Đang giữ" là số hiện tại của ví, không theo khoảng ngày. */
function CreditSummary({ summary, held }: { summary: CreditHistorySummary | null; held: number | null }) {
  const show = (value: number, sign: '+' | '-' | '') =>
    summary === null ? '--' : `${value > 0 ? sign : ''}${formatCredit(value)}`;
  const cells: Array<[string, string, string]> = [
    ['Đã nạp', show(summary?.toppedUp ?? 0, '+'), 'text-success'],
    ['Đã chi', show(summary?.spent ?? 0, '-'), 'text-error'],
    ['Được hoàn', show(summary?.refunded ?? 0, '+'), 'text-text-primary'],
    ['Đang giữ', held === null ? '--' : formatCredit(held), 'text-warning'],
  ];
  return (
    <div className="mt-4">
      <dl className="grid grid-cols-2 gap-2.5 sm:grid-cols-4">
        {cells.map(([label, value, tone]) => (
          <div key={label} className="rounded-md bg-surface-low px-4 py-3">
            <dt className="text-xs font-medium text-text-muted">{label}</dt>
            <dd className={`mt-1 text-lg font-bold tabular-nums ${tone}`}>{value}</dd>
          </div>
        ))}
      </dl>
      {summary && summary.adjusted !== 0 ? (
        <p className="mt-2 text-xs text-text-muted">
          Admin điều chỉnh trong khoảng này: {summary.adjusted > 0 ? '+' : ''}
          {formatCredit(summary.adjusted)} Credit.
        </p>
      ) : null}
      <p className="mt-2 text-xs text-text-muted">Tổng tính theo khoảng ngày đang chọn. Đang giữ là số hiện tại của ví.</p>
    </div>
  );
}

function BalanceMetric({ label, value, note }: { label: string; value: number | null; note: string }) {
  return (
    <div>
      <p className="text-xs text-primary-fixed">{label}</p>
      <p className="mt-0.5 text-xl font-bold tabular-nums">{value === null ? '--' : `${value} Credit`}</p>
      <p className="mt-0.5 text-xs text-primary-fixed">{note}</p>
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
  // Hộp cảnh báo không hoàn tiền: chỉ tạo đơn nạp sau khi người dùng xác nhận.
  const [warningOpen, setWarningOpen] = useState(false);
  const parsedAmount = Number(amountVnd);
  const validAmount = Number.isSafeInteger(parsedAmount) && parsedAmount > 0;
  const confirmTopUp = () => {
    setWarningOpen(false);
    onContinue();
  };
  return (
    <div className="flex h-full flex-col gap-4 rounded-xl bg-white p-5 ring-1 ring-border/80 sm:p-6">
      <div>
        <h2 className="section-title">Nạp Credit</h2>
        <p className="mt-1 text-sm text-text-secondary">
          Chọn nhanh hoặc nhập số tiền. Số Credit nhận được hiện ngay khi tạo đơn.
        </p>
      </div>
      <div className="flex flex-wrap gap-2" role="group" aria-label="Chọn nhanh số tiền">
        {QUICK_TOPUP_AMOUNTS.map((amount) => (
          <button
            key={amount}
            type="button"
            aria-pressed={parsedAmount === amount}
            onClick={() => onAmountChange(String(amount))}
            className={`min-h-11 rounded-md border px-4 text-sm font-semibold transition ${parsedAmount === amount ? 'border-primary bg-primary-faint text-primary' : 'border-border hover:border-primary/50'}`}
          >
            {formatVnd(amount)}
          </button>
        ))}
      </div>
      <div className="flex flex-wrap items-end gap-3">
        <div className="min-w-[200px] flex-1">
          <Field
            label="Số tiền nạp (VND)"
            type="number"
            min={1}
            step={1}
            value={amountVnd}
            onChange={(event) => onAmountChange(event.target.value)}
            placeholder="Nhập số tiền"
          />
        </div>
        <Button size="lg" onClick={() => setWarningOpen(true)} disabled={!validAmount || submitting}>
          {processing ? 'Đang xử lý thanh toán...' : submitting ? 'Đang tạo đơn...' : 'Tạo đơn nạp'}
        </Button>
      </div>
      <p className="text-xs leading-5 text-text-muted">
        Credit chỉ được cộng sau khi thanh toán được xác nhận. Mức tối thiểu theo quy định hiện hành.
      </p>
      {warningOpen ? (
        <Dialog title="Lưu ý trước khi nạp Credit" onClose={() => setWarningOpen(false)}>
          <p className="text-sm leading-6 text-text-secondary">
            Hệ thống <strong className="text-text-primary">không hoàn tiền</strong> sau khi nạp
            thành công. Bạn nên nạp một mức vừa đủ dùng, không nạp dư.
          </p>
          <p className="mt-2 text-sm font-semibold text-text-primary">
            Số tiền nạp: {formatVnd(parsedAmount)}
          </p>
          <div className="mt-5 flex flex-wrap justify-end gap-2">
            <Button variant="outline" onClick={() => setWarningOpen(false)}>
              Xem lại số tiền
            </Button>
            <Button onClick={confirmTopUp}>Tôi hiểu, tiếp tục nạp</Button>
          </div>
        </Dialog>
      ) : null}
    </div>
  );
}

export function ApiTopUpOrder({
  order,
  returnState,
  onNewTopup,
  onCancel,
  cancelling = false,
  clockOffsetMs = 0,
  paymentTabBlocked = false,
  onDeadlineReached,
}: {
  order: TopUpOrderResponse;
  returnState?: ReturnState;
  onNewTopup: () => void;
  /** Hủy đơn đang chờ thanh toán; không truyền thì không hiện nút hủy. */
  onCancel?: () => void;
  cancelling?: boolean;
  /** Độ lệch giờ server - giờ máy (ms), lấy từ serverTime của lần đọc đơn gần nhất. */
  clockOffsetMs?: number;
  /** Trình duyệt chặn mở tab VNPay: nút đổi thành "Mở trang thanh toán VNPay" để người dùng tự bấm. */
  paymentTabBlocked?: boolean;
  /** Gọi một lần khi đồng hồ về 0, để hỏi lại server kết quả cuối của đơn. */
  onDeadlineReached?: () => void;
}) {
  const [confirmingCancel, setConfirmingCancel] = useState(false);
  const safeRedirectUrl = paymentRedirectUrl(order.redirectUrl);
  const paid = order.status === 'SUCCESS';
  // CANCELLED: tự hủy, hủy để tạo đơn mới, hủy tại VNPay. FAILED: cổng báo thanh toán lỗi. Lý do do Backend trả.
  const cancelled = order.status === 'CANCELLED';
  const failed = order.status === 'FAILED';
  const expired = order.status === 'EXPIRED';
  const pendingAfterFailedReturn =
    order.status === 'PENDING' && returnState === 'pending-after-failed-return';
  // Chỉ đếm ngược khi người dùng còn đang ở bước thanh toán: đơn PENDING và lần thanh toán vừa rồi chưa thất bại.
  // Trạng thái khác truyền null để hook dọn interval.
  const remainingMs = usePaymentCountdown(
    order.status === 'PENDING' && !pendingAfterFailedReturn ? order.expiresAt : null,
    clockOffsetMs,
  );
  // Hết giờ chỉ đổi cách hiển thị và hỏi lại server; server (đọc đơn hoặc PaymentExpiryJob) mới đổi trạng thái thật,
  // FE không tự cộng hay trừ gì.
  const timedOut = remainingMs !== null && remainingMs <= 0;
  const deadlineReachedRef = useRef(onDeadlineReached);
  deadlineReachedRef.current = onDeadlineReached;
  useEffect(() => {
    if (timedOut) deadlineReachedRef.current?.();
  }, [timedOut]);
  const waitingForPayment = order.status === 'PENDING' && !timedOut;
  const statusLabel = cancelled
    ? returnState === 'cancelled'
      ? 'Đã hủy thanh toán'
      : 'Đã hủy'
    : failed
      ? paymentStatusLabels.FAILED
      : pendingAfterFailedReturn
      ? 'Đang đồng bộ trạng thái'
      : timedOut
        ? paymentStatusLabels.EXPIRED
        : paymentStatusLabels[order.status];
  const heading = paid
    ? 'Nạp Credit thành công'
    : cancelled
      ? 'Đơn nạp đã hủy'
      : failed
        ? 'Thanh toán không thành công'
        : pendingAfterFailedReturn
        ? 'Đơn nạp đang chờ xác nhận'
        : expired
          ? 'Đơn nạp Credit đã hết hạn'
          : 'Đơn đang chờ thanh toán';
  const description = paid
    ? `+${order.credits} Credit đã được cộng vào ví.`
    : cancelled || failed
      ? `${order.failureReason ? `${order.failureReason} ` : ''}Credit không được cộng và số dư ví không thay đổi.`
      : pendingAfterFailedReturn
        ? 'Lần thanh toán vừa thất bại, nhưng trạng thái đơn vẫn đang chờ hệ thống xác nhận hủy.'
        : expired
          ? 'Đơn đã quá hạn thanh toán. Credit không được cộng cho đơn này.'
          : 'Credit chưa được cộng vào ví. Trạng thái sẽ thay đổi sau khi khoản thanh toán được xác nhận.';
  return (
    <div className="rounded-xl bg-white px-5 py-8 ring-1 ring-border/80 sm:px-8">
      <div className="mx-auto max-w-2xl text-center">
        <span
          className={`inline-flex rounded-full px-3 py-1 text-xs font-bold ${paid ? 'bg-success-soft text-success' : cancelled ? 'bg-error-soft text-error' : 'bg-warning-soft text-warning'}`}
        >
          {statusLabel}
        </span>
        {remainingMs !== null && !timedOut ? (
          <p
            className="mt-3 text-sm font-semibold text-text-secondary"
            role="timer"
            aria-live="off"
          >
            Còn <span className="tabular-nums text-text-primary">{formatRemaining(remainingMs)}</span>{' '}
            để thanh toán
            {order.expiresAt ? ` (hạn ${new Date(order.expiresAt).toLocaleTimeString('vi-VN')})` : ''}
          </p>
        ) : timedOut ? (
          <p className="mt-3 text-sm font-semibold text-text-secondary">
            Đơn đã quá hạn thanh toán. Credit sẽ không được cộng cho đơn này.
          </p>
        ) : null}
        <h2 className="mt-4 text-2xl font-bold">{heading}</h2>
        {description ? <p className="mt-2 text-sm leading-6 text-text-muted">{description}</p> : null}
        <dl className="mt-6 divide-y divide-border rounded-lg bg-surface-low px-4 text-left">
          <OrderCodeDetailRow orderCode={order.orderCode} />
          <DetailRow label="Số tiền" value={formatVnd(order.amountVnd)} />
          <DetailRow label="Credit dự kiến" value={`${order.credits} Credit`} />
          <DetailRow label="Cổng thanh toán" value={paymentProviderLabel(order.provider)} />
          <DetailRow label="Trạng thái" value={statusLabel} />
        </dl>
        {order.provider === 'MOCK' && !paid ? (
          <p className="mt-4 rounded-md bg-warning-soft p-3 text-sm text-warning">
            {backendMockProviderMessage}
          </p>
        ) : null}
        {confirmingCancel && order.status === 'PENDING' ? (
          <div className="mt-6 rounded-lg bg-warning-soft p-4 text-left text-sm text-text-primary">
            <p className="font-semibold">Hủy đơn nạp này?</p>
            <p className="mt-1 text-text-secondary">
              Đơn sẽ không thanh toán tiếp được. Credit chưa được cộng nên số dư ví không thay đổi.
            </p>
            <div className="mt-3 flex flex-wrap gap-2">
              <Button variant="danger" disabled={cancelling} onClick={onCancel}>
                {cancelling ? 'Đang hủy...' : 'Xác nhận hủy'}
              </Button>
              <Button variant="ghost" disabled={cancelling} onClick={() => setConfirmingCancel(false)}>
                Không hủy
              </Button>
            </div>
          </div>
        ) : null}
        <div className="mt-6 flex flex-wrap justify-center gap-2">
          <Button variant="outline" onClick={onNewTopup}>
            Tạo đơn nạp mới
          </Button>
          {onCancel && order.status === 'PENDING' && !confirmingCancel ? (
            <Button variant="ghost" disabled={cancelling} onClick={() => setConfirmingCancel(true)}>
              Hủy đơn nạp
            </Button>
          ) : null}
          {order.provider === 'VNPAY' &&
          waitingForPayment &&
          !pendingAfterFailedReturn &&
          safeRedirectUrl ? (
            // Mở VNPay ở tab mới như lúc tạo đơn; tab này ở lại đếm ngược và hỏi trạng thái.
            <a
              href={safeRedirectUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex min-h-10 items-center justify-center rounded-md bg-primary px-4 py-2 text-sm font-bold text-white"
            >
              {paymentTabBlocked ? 'Mở trang thanh toán VNPay' : 'Tiếp tục thanh toán'}
            </a>
          ) : null}
        </div>
      </div>
    </div>
  );
}

function OrderCodeDetailRow({ orderCode }: { orderCode: string }) {
  const [copied, setCopied] = useState(false);
  const compactCode =
    orderCode.length > 10
      ? `#${orderCode.slice(0, 4).toUpperCase()}...${orderCode.slice(-4).toUpperCase()}`
      : `#${orderCode.toUpperCase()}`;

  const copyOrderCode = async () => {
    try {
      await navigator.clipboard.writeText(orderCode);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  };

  return (
    <div className="flex flex-wrap items-center justify-between gap-3 py-3">
      <dt className="text-sm text-text-muted">Mã đơn</dt>
      <dd className="flex min-w-0 items-center gap-2 text-right text-sm font-medium text-text-primary">
        <code className="font-mono text-xs">{compactCode}</code>
        <button
          type="button"
          className="inline-flex min-h-8 items-center rounded-md border border-border bg-white px-2.5 text-xs font-semibold text-primary transition hover:bg-primary-soft focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/30"
          aria-label="Sao chép mã đơn đầy đủ"
          onClick={() => void copyOrderCode()}
        >
          {copied ? 'Đã sao chép' : 'Sao chép'}
        </button>
      </dd>
    </div>
  );
}

/** Đơn còn PENDING và chưa qua hạn thanh toán (theo giờ server). */
function isStillPayable(order: TopUpOrderResponse, clockOffsetMs: number): boolean {
  if (order.status !== 'PENDING') return false;
  const deadline = order.expiresAt ? Date.parse(order.expiresAt) : Number.NaN;
  return !Number.isFinite(deadline) || deadline - clockOffsetMs > Date.now();
}

/** Còn đơn đang chờ mà người dùng muốn tạo đơn mới: chọn tiếp tục đơn cũ hoặc hủy đơn cũ & tạo đơn mới. */
function PendingTopUpDialog({
  order,
  busy,
  onContinue,
  onReplace,
  onClose,
}: {
  order: TopUpOrderResponse;
  busy: boolean;
  onContinue: () => void;
  onReplace: () => void;
  onClose: () => void;
}) {
  const canContinue = Boolean(paymentRedirectUrl(order.redirectUrl));
  return (
    <Dialog title="Bạn còn đơn nạp đang chờ" busy={busy} onClose={onClose}>
      <p className="text-sm leading-6 text-text-secondary">
        Bạn còn 1 đơn <strong className="text-text-primary">{formatVnd(order.amountVnd)}</strong> (
        {order.credits} Credit) đang chờ thanh toán. Mỗi lần chỉ giữ một đơn đang chờ.
      </p>
      <div className="mt-5 flex flex-wrap justify-end gap-2">
        {canContinue ? (
          <Button variant="outline" disabled={busy} onClick={onContinue}>
            Tiếp tục thanh toán đơn cũ
          </Button>
        ) : null}
        <Button variant="danger" disabled={busy} onClick={onReplace}>
          Hủy đơn cũ & tạo đơn mới
        </Button>
      </div>
    </Dialog>
  );
}

const TOPUP_HISTORY_PAGE_SIZE = 5;

/** Nhãn và màu badge của một đơn trong lịch sử nạp. Đơn PENDING đã qua hạn (đang chờ server chốt) hiện là Hết hạn. */
function topUpHistoryBadge(order: TopUpOrderDetailResponse): { label: string; className: string } {
  if (order.status === 'SUCCESS') return { label: 'Thành công', className: 'bg-success-soft text-success' };
  if (order.status === 'PENDING') {
    const now = order.serverTime ? Date.parse(order.serverTime) : Date.now();
    const expired = order.expiresAt ? Date.parse(order.expiresAt) <= now : false;
    return expired
      ? { label: 'Hết hạn', className: 'bg-surface-low text-text-muted' }
      : { label: 'Đang chờ', className: 'bg-warning-soft text-warning' };
  }
  if (order.status === 'CANCELLED') return { label: 'Đã hủy', className: 'bg-surface-container text-text-secondary' };
  if (order.status === 'FAILED') return { label: 'Thất bại', className: 'bg-error-soft text-error' };
  return { label: 'Hết hạn', className: 'bg-surface-low text-text-muted' };
}

/** Lịch sử nạp: mọi đơn nạp của mình, mới nhất trước, có phân trang. Dữ liệu luôn đọc từ server. */
export function TopUpHistorySection({ reloadKey }: { reloadKey: number }) {
  const [page, setPage] = useState(0);
  const [orders, setOrders] = useState<TopUpOrderDetailResponse[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [retryKey, setRetryKey] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    paymentApi
      .listOrders({ page, size: TOPUP_HISTORY_PAGE_SIZE })
      .then((result) => {
        if (!active) return;
        setOrders(result.items);
        setTotal(result.totalElements);
      })
      .catch(() => {
        if (active) setError('Không tải được lịch sử nạp.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [page, reloadKey, retryKey]);

  return (
    <section aria-label="Lịch sử nạp" className="mt-5 rounded-xl bg-white p-5 ring-1 ring-border/80 sm:p-6">
      <h2 className="section-title">Lịch sử nạp</h2>
      <p className="mt-1 text-xs text-text-muted">Mọi đơn nạp Credit của bạn, mới nhất trước.</p>
      {loading && !orders.length ? (
        <div className="mt-3" role="status">
          <Alert>Đang tải lịch sử nạp...</Alert>
        </div>
      ) : null}
      {!loading && error ? (
        <div className="mt-3 space-y-3">
          <Alert tone="error">{error}</Alert>
          <Button variant="outline" onClick={() => setRetryKey((key) => key + 1)}>
            Thử lại
          </Button>
        </div>
      ) : null}
      {!loading && !error && !orders.length ? (
        <p className="mt-3 text-sm text-text-muted">Bạn chưa có đơn nạp nào.</p>
      ) : null}
      {orders.length ? (
        <ul className="mt-3 divide-y divide-border overflow-hidden rounded-md ring-1 ring-border/80">
          {orders.map((order) => {
            const badge = topUpHistoryBadge(order);
            return (
              <li
                key={order.orderCode}
                className="flex flex-col gap-2 px-4 py-3 text-sm sm:flex-row sm:items-center sm:justify-between"
              >
                <div className="min-w-0">
                  <p className="font-semibold tabular-nums text-text-primary">
                    {formatVnd(order.amountVnd)} · {order.credits} Credit
                  </p>
                  <p className="mt-0.5 text-xs text-text-muted">
                    {new Date(order.createdAt).toLocaleString('vi-VN')} · #
                    {order.orderCode.slice(0, 4).toUpperCase()}...{order.orderCode.slice(-4).toUpperCase()}
                  </p>
                  {order.failureReason ? (
                    <p className="mt-0.5 text-xs text-text-muted">{order.failureReason}</p>
                  ) : null}
                </div>
                <span
                  className={`inline-flex w-fit shrink-0 rounded-full px-3 py-1 text-xs font-bold ${badge.className}`}
                >
                  {badge.label}
                </span>
              </li>
            );
          })}
        </ul>
      ) : null}
      {total > TOPUP_HISTORY_PAGE_SIZE ? (
        <div className="mt-3">
          <Pagination
            page={page}
            size={TOPUP_HISTORY_PAGE_SIZE}
            totalElements={total}
            disabled={loading}
            onPageChange={setPage}
          />
        </div>
      ) : null}
    </section>
  );
}

/**
 * Mở một tab trống để sau đó điền URL VNPay. Bỏ liên kết ngược (opener) để trang thanh toán không điều khiển được tab
 * ShareLoop. Trình duyệt chặn popup thì trả null.
 */
function openBlankPaymentTab(): Window | null {
  try {
    const tab = window.open('', '_blank');
    if (tab) tab.opener = null;
    return tab;
  } catch {
    return null;
  }
}

/**
 * Số mili giây còn lại tới hạn thanh toán do Backend trả, cập nhật mỗi giây; null khi không có hạn để đếm. Chỉ để hiển
 * thị: không đổi trạng thái đơn.
 */
function usePaymentCountdown(
  expiresAt: string | null | undefined,
  clockOffsetMs = 0,
): number | null {
  // Còn lại = expiresAt - (giờ máy + độ lệch với server), tức hạn quy về giờ máy = expiresAt - độ lệch.
  const deadline = expiresAt ? Date.parse(expiresAt) - clockOffsetMs : Number.NaN;
  const hasDeadline = Number.isFinite(deadline);
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!hasDeadline || deadline <= Date.now()) return;
    const timer = window.setInterval(() => {
      const current = Date.now();
      setNow(current);
      if (current >= deadline) window.clearInterval(timer);
    }, 1000);
    return () => window.clearInterval(timer);
  }, [deadline, hasDeadline]);
  return hasDeadline ? deadline - now : null;
}

function formatRemaining(ms: number) {
  const totalSeconds = Math.max(0, Math.ceil(ms / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
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
    <section className="mt-5 rounded-xl bg-white p-5 ring-1 ring-border/80 sm:p-6">
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
          <Button variant="outline" onClick={onRetry}>
            Thử lại
          </Button>
        </div>
      ) : null}
      {!loading && !error && !heldFees.length ? (
        <p className="mt-3 text-sm text-text-muted">Không có bài nào đang giữ phí.</p>
      ) : null}
      {!loading && !error && heldFees.length ? (
        <ul className="mt-3 divide-y divide-border overflow-hidden rounded-md ring-1 ring-border/80">
          {heldFees.map((heldFee) => (
            <li
              key={heldFee.itemId}
              className="flex flex-col gap-3 px-4 py-4 text-sm sm:flex-row sm:items-center sm:justify-between"
            >
              <div className="min-w-0">
                <Link
                  className="block truncate font-semibold text-link hover:underline"
                  to={`/items/${heldFee.itemId}`}
                >
                  {heldFee.title}
                </Link>
                <div className="mt-2 flex flex-wrap items-center gap-2">
                  <span className="inline-flex rounded-sm bg-primary-soft px-2 py-1 text-2xs font-bold text-primary">
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




