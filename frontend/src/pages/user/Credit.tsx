import { useMemo, useState, type Dispatch } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '../../app/hooks';
import { actions, selectCurrentUser, selectData } from '../../app/store';
import { Button, Field, Icon, PageHeader } from '../../components/ui';
import type { CreditHistory, CreditHistoryType, Item, Topup } from '../../types/domain';
import { formatCredit, formatVnd } from '../../utils/formatting';
import { CREDIT_TO_VND } from '../../utils/credit';

const packages = [1, 2, 5, 10, 20];
type Step = 'select' | 'qr' | 'pending';
type HistoryFilter = 'all' | 'topup' | 'spend';

const filterTypes: Record<Exclude<HistoryFilter, 'all'>, CreditHistoryType[]> = {
  topup: ['TOPUP'],
  spend: ['SPEND', 'AI_SPEND', 'RECEIVE_FEE'],
};

const legacyHistoryPattern = /swap|give|giu phi|giữ phí|giu giao dich|giữ giao dịch|tam giu|tạm giữ/i;

function isPostFee(entry: CreditHistory) {
  return entry.type === 'SPEND' && /^phi dang bai|^phí đăng bài/i.test(entry.description);
}

function isVisibleCreditHistory(entry: CreditHistory) {
  if (['TRANSACTION_FEE', 'HOLD', 'RELEASE_HOLD'].includes(entry.type)) return false;
  if (legacyHistoryPattern.test(`${entry.description} ${entry.note}`)) return false;
  return entry.type === 'TOPUP' || entry.type === 'AI_SPEND' || entry.type === 'RECEIVE_FEE' || entry.type === 'ADMIN_ADJUSTMENT' || isPostFee(entry);
}

function postFeeItemId(entry: CreditHistory) {
  return entry.ref?.match(/^post:(.+):fee$/)?.[1] ?? entry.transactionId.match(/^post:(.+):fee$/)?.[1];
}

function balanceBefore(entry: CreditHistory) {
  return entry.balance - entry.amount;
}

export function Credit() {
  const user = useAppSelector(selectCurrentUser)!;
  const data = useAppSelector(selectData);
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const [creditAmount, setCreditAmount] = useState(5);
  const [customAmount, setCustomAmount] = useState('');
  const [step, setStep] = useState<Step>('select');
  const [transactionCode, setTransactionCode] = useState('');
  const [activeTopupId, setActiveTopupId] = useState('');
  const [historyFilter, setHistoryFilter] = useState<HistoryFilter>('all');
  const [selectedEntry, setSelectedEntry] = useState<CreditHistory | Topup | null>(null);

  const topups = data.topups.filter((topup) => topup.userId === user.id);
  const activeTopup = topups.find((topup) => topup.id === activeTopupId);
  const latestCompleted = topups.find((topup) => topup.status === 'completed');
  const paymentValue = creditAmount * CREDIT_TO_VND;

  const allHistory = useMemo(
    () =>
      data.creditHistory
        .filter((entry) => entry.userId === user.id)
        .filter(isVisibleCreditHistory)
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt)),
    [data.creditHistory, user.id],
  );

  const history = useMemo(() => {
    if (historyFilter === 'all') return allHistory;
    return allHistory.filter((entry) => filterTypes[historyFilter].includes(entry.type));
  }, [allHistory, historyFilter]);

  const selectedRelatedTransaction = selectedEntry && 'relatedTransactionId' in selectedEntry
    ? data.transactions.find((tx) => tx.id === selectedEntry.relatedTransactionId)
    : undefined;
  const selectedRelatedItem = selectedRelatedTransaction
    ? data.items.find((item) => item.id === selectedRelatedTransaction.itemId)
    : selectedEntry && !('method' in selectedEntry)
      ? data.items.find((item) => item.id === postFeeItemId(selectedEntry))
      : undefined;

  const continuePayment = () => {
    const code = `SLTOPUP-${crypto.randomUUID()}`;
    setTransactionCode(code);
    setStep('qr');
  };

  const confirmTransfer = () => {
    const id = `top_${crypto.randomUUID()}`;
    dispatch(
      actions.topupCredit({
        id,
        code: transactionCode,
        userId: user.id,
        vnd: paymentValue,
      }),
    );
    setActiveTopupId(id);
    setStep('pending');
  };

  return (
    <div className="page-shell">
      <PageHeader
        title="Credit của bạn"
        description="Quản lý số dư, nạp Credit và theo dõi các giao dịch của bạn."
      />

      {latestCompleted ? (
        <div className="mb-6 flex items-start gap-3 rounded-lg border border-emerald-100 bg-emerald-50 px-4 py-3 text-success">
          <span className="mt-0.5 grid size-7 shrink-0 place-items-center rounded-full bg-white">
            <Icon name="check" className="size-4" weight="bold" />
          </span>
          <div>
            <p className="text-sm font-bold">Nạp Credit thành công</p>
            <p className="mt-0.5 text-xs leading-5">
              +{latestCompleted.amount} Credit đã được cộng vào tài khoản.
            </p>
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
              {formatCredit(user.totalCredit)}
            </div>
            <p className="mt-3 text-sm text-white/70">1 Credit = 1.000đ</p>
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
          <BalanceMetric label="Khả dụng" value={user.availableCredit} />
          <BalanceMetric label="Đang giữ" value={user.holdCredit} bordered />
        </div>
      </section>

      <section id="topup" className="mt-8 scroll-mt-24">
        {step === 'select' ? (
          <TopupSelection
            creditAmount={creditAmount}
            customAmount={customAmount}
            onAmountChange={(amount) => {
              setCreditAmount(amount);
              setCustomAmount('');
            }}
            onCustomChange={(value) => {
              setCustomAmount(value);
              const parsed = Number(value);
              setCreditAmount(Number.isInteger(parsed) && parsed > 0 ? parsed : 0);
            }}
            onContinue={continuePayment}
          />
        ) : null}
        {step === 'qr' ? (
          <QrPayment
            creditAmount={creditAmount}
            transactionCode={transactionCode}
            onBack={() => setStep('select')}
            onConfirm={confirmTransfer}
          />
        ) : null}
        {step === 'pending' ? (
          <PendingTopup
            code={activeTopup?.code ?? transactionCode}
            amount={activeTopup?.amount ?? creditAmount}
            onNewTopup={() => {
              setActiveTopupId('');
              setStep('select');
            }}
          />
        ) : null}
      </section>

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
  value: number;
  bordered?: boolean;
}) {
  return (
    <div
      className={`px-6 py-4 sm:px-8 lg:px-10 ${bordered ? 'border-t border-white/15 sm:border-l sm:border-t-0' : ''}`}
    >
      <p className="text-xs font-medium text-white/60">{label}</p>
      <p className="mt-1 text-xl font-bold tabular-nums">{value} Credit</p>
    </div>
  );
}

function TopupSelection({
  creditAmount,
  customAmount,
  onAmountChange,
  onCustomChange,
  onContinue,
}: {
  creditAmount: number;
  customAmount: string;
  onAmountChange: Dispatch<number>;
  onCustomChange: Dispatch<string>;
  onContinue: () => void;
}) {
  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_340px]">
      <div className="rounded-xl bg-white p-5 ring-1 ring-border/80 sm:p-7">
        <h2 className="section-title">Nạp Credit</h2>
        <p className="mt-2 text-sm text-text-muted">Chọn số Credit bạn muốn nạp vào tài khoản.</p>
        <div className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-3">
          {packages.map((amount) => {
            const selected = creditAmount === amount && !customAmount;
            return (
              <button
                key={amount}
                onClick={() => onAmountChange(amount)}
                className={`relative rounded-lg border px-4 py-4 text-left transition duration-200 active:translate-y-px ${selected ? 'border-primary bg-primary-faint ring-2 ring-primary/10' : 'border-border bg-white hover:border-primary/40'}`}
              >
                {selected ? (
                  <span className="absolute right-3 top-3 grid size-5 place-items-center rounded-full bg-primary text-white">
                    <Icon name="check" className="size-3" weight="bold" />
                  </span>
                ) : null}
                <strong className="block text-base text-text-primary">{amount} Credit</strong>
                <span className="mt-1 block text-xs text-text-muted">
                  {formatVnd(amount * CREDIT_TO_VND)}
                </span>
              </button>
            );
          })}
        </div>
        <div className="mt-5 max-w-xs">
          <Field
            label="Số Credit khác"
            type="number"
            min={1}
            step={1}
            value={customAmount}
            onChange={(event) => onCustomChange(event.target.value)}
            placeholder="Nhập số Credit"
            hint="Tối thiểu 1 Credit."
          />
        </div>
      </div>
      <PaymentSummary creditAmount={creditAmount} onContinue={onContinue} />
    </div>
  );
}

function PaymentSummary({ creditAmount, onContinue }: { creditAmount: number; onContinue: () => void }) {
  const value = creditAmount * CREDIT_TO_VND;
  return (
    <aside className="h-fit rounded-xl bg-white p-5 ring-1 ring-border/80 sm:p-6 lg:sticky lg:top-24">
      <h2 className="text-base font-bold">Tóm tắt thanh toán</h2>
      <dl className="mt-5 space-y-3 text-sm">
        <SummaryRow label="Số Credit" value={`${creditAmount} Credit`} />
        <SummaryRow label="Giá trị" value={formatVnd(value)} />
        <SummaryRow label="Phí thanh toán" value="0đ" />
      </dl>
      <div className="mt-5 flex items-center justify-between border-t border-border pt-4">
        <span className="text-sm font-semibold">Tổng thanh toán</span>
        <strong className="text-lg tabular-nums text-primary">{formatVnd(value)}</strong>
      </div>
      <Button className="mt-5 w-full" size="lg" onClick={onContinue} disabled={creditAmount < 1}>
        Tiếp tục thanh toán
      </Button>
      <p className="mt-3 text-center text-[11px] leading-5 text-text-muted">
        Credit chỉ được cộng sau khi giao dịch được xác nhận.
      </p>
    </aside>
  );
}

function SummaryRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-4">
      <dt className="text-text-muted">{label}</dt>
      <dd className="font-semibold tabular-nums text-text-primary">{value}</dd>
    </div>
  );
}

function QrPayment({
  creditAmount,
  transactionCode,
  onBack,
  onConfirm,
}: {
  creditAmount: number;
  transactionCode: string;
  onBack: () => void;
  onConfirm: () => void;
}) {
  return (
    <div className="rounded-xl bg-white p-5 ring-1 ring-border/80 sm:p-7">
      <div className="mx-auto max-w-3xl">
        <div className="text-center">
          <span className="inline-flex rounded-sm bg-amber-50 px-2.5 py-1 text-xs font-semibold text-warning">
            Đang chờ thanh toán
          </span>
          <h2 className="mt-4 text-2xl font-bold">Quét mã QR để thanh toán</h2>
          <p className="mt-2 text-sm text-text-muted">
            Dùng ứng dụng ngân hàng của bạn để quét mã bên dưới.
          </p>
        </div>
        <div className="mt-7 grid gap-8 md:grid-cols-[240px_1fr] md:items-center">
          <div className="mx-auto w-full max-w-[240px] rounded-lg bg-white p-4 shadow-md ring-1 ring-border">
            <MockQr />
          </div>
          <div>
            <dl className="space-y-3 rounded-lg bg-background p-4 text-sm">
              <SummaryRow label="Số tiền" value={formatVnd(creditAmount * CREDIT_TO_VND)} />
              <SummaryRow label="Credit nhận" value={`${creditAmount} Credit`} />
              <SummaryRow label="Mã giao dịch" value={transactionCode} />
            </dl>
            <ol className="mt-5 space-y-2 text-sm leading-6 text-text-secondary">
              <li>1. Mở ứng dụng ngân hàng.</li>
              <li>2. Quét mã QR.</li>
              <li>3. Kiểm tra đúng số tiền và nội dung chuyển khoản.</li>
              <li>4. Sau khi chuyển khoản, chọn “Tôi đã chuyển khoản”.</li>
            </ol>
          </div>
        </div>
        <div className="mt-8 flex flex-col-reverse gap-2 border-t border-border pt-5 sm:flex-row sm:justify-end">
          <Button variant="outline" onClick={onBack}>
            Quay lại
          </Button>
          <Button onClick={onConfirm}>Tôi đã chuyển khoản</Button>
        </div>
      </div>
    </div>
  );
}

function MockQr() {
  return (
    <div aria-label="Mã QR thanh toán mẫu" className="grid aspect-square grid-cols-11 gap-[2px] bg-white">
      {Array.from({ length: 121 }, (_, index) => {
        const row = Math.floor(index / 11);
        const col = index % 11;
        const finder = (row < 4 && col < 4) || (row < 4 && col > 6) || (row > 6 && col < 4);
        const filled = finder ? row % 3 !== 1 || col % 3 !== 1 : (row * 7 + col * 11 + index) % 5 < 2;
        return <span key={index} className={filled ? 'bg-text-primary' : 'bg-white'} />;
      })}
    </div>
  );
}

function PendingTopup({ code, amount, onNewTopup }: { code: string; amount: number; onNewTopup: () => void }) {
  return (
    <div className="rounded-xl bg-white px-5 py-10 text-center ring-1 ring-border/80 sm:px-8">
      <span className="mx-auto grid size-14 place-items-center rounded-lg bg-amber-50 text-warning">
        <Icon name="history" className="size-7" />
      </span>
      <h2 className="mt-5 text-2xl font-bold">Yêu cầu nạp Credit đang được xác nhận.</h2>
      <p className="mx-auto mt-3 max-w-lg text-sm leading-6 text-text-muted">
        Yêu cầu {code} cho {amount} Credit đã được ghi nhận. Credit sẽ được cộng đúng một lần sau khi quản trị viên xác nhận.
      </p>
      <Button className="mt-6" variant="outline" onClick={onNewTopup}>
        Tạo yêu cầu nạp khác
      </Button>
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
}: {
  items: Item[];
  allHistory: CreditHistory[];
  history: CreditHistory[];
  pendingTopups: Topup[];
  activeFilter: HistoryFilter;
  onFilterChange: Dispatch<HistoryFilter>;
  onSelect: (entry: CreditHistory | Topup) => void;
}) {
  const countFor = (filter: HistoryFilter) => {
    const pendingCount = filter === 'all' || filter === 'topup' ? pendingTopups.length : 0;
    if (filter === 'all') return allHistory.length + pendingCount;
    return allHistory.filter((entry) => filterTypes[filter].includes(entry.type)).length + pendingCount;
  };
  const tabs: Array<[HistoryFilter, string]> = [
    ['all', 'Tất cả'],
    ['topup', 'Nạp Credit'],
    ['spend', 'Chi tiêu'],
  ];

  return (
    <section className="mt-10">
      <h2 className="section-title">Lịch sử Credit</h2>
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
    </section>
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
    <button type="button" onClick={onClick} className="grid w-full cursor-pointer grid-cols-[1fr_auto_auto] gap-4 border-b border-border/70 px-4 py-4 text-left transition hover:bg-surface-low last:border-0 sm:px-5">
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
    const itemTitle = relatedItemTitle ?? entry.description.replace(/^phi dang bai\s*-\s*/i, '').replace(/^phí đăng bài\s*-\s*/i, '');
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
  const typeLabel = isTopup ? 'Nạp Credit' : isPostFee(entry) ? 'Phí đăng bài' : historyTitle(entry, relatedItemTitle);
  const status = isTopup
    ? entry.status === 'pending' ? 'Đang xác nhận' : 'Hoàn tất'
    : historyStatus(entry);

  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-slate-950/45 p-4" onMouseDown={onClose}>
      <div role="dialog" aria-modal="true" aria-labelledby="credit-detail-title" className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-xl bg-white p-6 shadow-2xl sm:p-7" onMouseDown={(event) => event.stopPropagation()}>
        <div className="flex items-start justify-between gap-4">
          <h2 id="credit-detail-title" className="text-xl font-bold text-text-primary">Chi tiết giao dịch Credit</h2>
          <button type="button" aria-label="Đóng" onClick={onClose} className="rounded-md p-1 text-text-muted hover:bg-surface-low hover:text-text-primary">
            <Icon name="close" className="size-5" />
          </button>
        </div>
        <dl className="mt-6 divide-y divide-border rounded-lg bg-surface-low px-4">
          <DetailRow label="Loại giao dịch" value={typeLabel} />
          {isTopup ? (
            <>
              <DetailRow label="Số Credit" value={`+${entry.amount} Credit`} valueClass="text-success" />
              <DetailRow label="Số tiền thanh toán" value={formatVnd(entry.vnd)} />
            </>
          ) : (
            <>
              {isPostFee(entry) && relatedItemTitle ? <DetailRow label="Món đồ" value={relatedItemTitle} /> : null}
              <DetailRow label="Số Credit" value={`${entry.amount > 0 ? '+' : ''}${entry.amount} Credit`} valueClass={historyTone(entry) === 'negative' ? 'text-error' : 'text-success'} />
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
              : historyEntry?.note || historyEntry?.description || historyTitle(historyEntry!, relatedItemTitle)}
          </p>
          {relatedItemTitle ? <p className="mt-1 text-text-secondary">Món đồ: {relatedItemTitle}</p> : null}
          {relatedTransactionType ? (
            <p className="mt-1 text-text-secondary">
              Hình thức: {relatedTransactionType === 'trade' ? 'Trao đổi' : 'Cho - Nhận'}
            </p>
          ) : null}
          {relatedTransactionId ? <p className="mt-1 text-text-secondary">Giao dịch liên quan: {relatedTransactionId}</p> : null}
          {onViewTransaction ? <Button className="mt-4" size="sm" onClick={onViewTransaction}>Xem giao dịch</Button> : null}
        </div>
      </div>
    </div>
  );
}

function DetailRow({ label, value, valueClass = 'text-text-primary' }: { label: string; value: string; valueClass?: string }) {
  return <div className="flex items-center justify-between gap-4 py-3 text-sm"><dt className="text-text-muted">{label}</dt><dd className={`text-right font-semibold ${valueClass}`}>{value}</dd></div>;
}

function formatDateTime(value: string) {
  return new Date(value).toLocaleString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}
