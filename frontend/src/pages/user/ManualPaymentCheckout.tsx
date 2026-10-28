import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useWallet } from '../../app/useWallet';
import { Alert, Button } from '../../components/ui';
import { paymentApi, type TopUpOrderDetailResponse } from '../../services/paymentApi';
import { clearPendingTopUp, loadPendingTopUp } from '../../services/topUpSession';
import type { ApiError } from '../../services/http';
import { formatVnd } from '../../utils/formatting';

export function ManualPaymentCheckout() {
  const location = useLocation();
  const navigate = useNavigate();
  const code =
    new URLSearchParams(location.search).get('orderCode') ?? loadPendingTopUp()?.orderCode;
  const { refresh } = useWallet();
  const [order, setOrder] = useState<TopUpOrderDetailResponse | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const inFlight = useRef(false);

  useEffect(() => {
    let active = true;
    setOrder(null);
    setError('');
    if (!code) {
      setError('Không tìm thấy mã đơn. Vui lòng tạo đơn nạp Credit.');
      return;
    }
    paymentApi
      .getOrder(code)
      .then((result) => {
        if (!active) return;
        if (result.provider !== 'MANUAL') {
          setError('Đơn không dùng QR minh họa.');
          return;
        }
        setOrder(result);
        if (result.status === 'SUCCESS') {
          refresh();
          clearPendingTopUp();
          navigate(`/credit?orderCode=${encodeURIComponent(result.orderCode)}`, { replace: true });
        }
      })
      .catch((caught: ApiError) => {
        if (active) setError(caught.message || 'Không tải được đơn nạp.');
      });
    return () => {
      active = false;
    };
  }, [code, refresh, navigate]);

  const update = async (markPaid: boolean) => {
    if (!code || inFlight.current) return;
    inFlight.current = true;
    setBusy(true);
    setError('');
    try {
      const result = await (markPaid ? paymentApi.markPaid(code) : paymentApi.getOrder(code));
      if (result.provider !== 'MANUAL') throw new Error('Đơn không dùng QR minh họa.');
      setOrder(result);
      if (result.status === 'SUCCESS') {
        refresh();
        clearPendingTopUp();
        navigate(`/credit?orderCode=${encodeURIComponent(result.orderCode)}`, { replace: true });
      }
    } catch (caught) {
      setError((caught as ApiError).message || 'Không cập nhật được đơn nạp. Vui lòng thử lại.');
      // Đọc lại EXPIRED nếu mark-paid vừa bị từ chối vì hết hạn.
      try {
        const latest = await paymentApi.getOrder(code);
        if (latest.provider === 'MANUAL') {
          setOrder(latest);
          if (latest.status === 'SUCCESS') {
            refresh();
            clearPendingTopUp();
            navigate(`/credit?orderCode=${encodeURIComponent(latest.orderCode)}`, { replace: true });
          }
        }
      } catch {
        /* Keep the original error. */
      }
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  };

  const status =
    order?.status === 'SUCCESS'
      ? 'Đã cộng Credit'
      : order?.status === 'FAILED'
        ? 'Yêu cầu bị từ chối'
        : order?.status === 'EXPIRED'
          ? 'Đơn đã hết hạn'
          : order?.submittedAt
            ? 'Chờ quản trị viên duyệt'
            : 'Chờ thanh toán';

  const waitingForPayment = order?.status === 'PENDING' && !order.submittedAt;

  return (
    <div className="page-shell max-w-[1040px] space-y-5">
      <Link
        className="inline-flex min-h-11 items-center text-sm font-semibold text-primary hover:text-primary-hover"
        to="/credit"
        onClick={() => {
          if (order && order.status !== 'PENDING') clearPendingTopUp();
        }}
      >
        Về Ví Credit
      </Link>
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="page-title">{order ? `Chuyển khoản để nạp ${order.credits} Credit` : 'Nạp Credit bằng QR minh họa'}</h1>
          {order ? <p className="page-intro">Đơn {order.orderCode}</p> : null}
        </div>
        {order ? (
          <span className="rounded-md bg-warning-soft px-3.5 py-2 text-sm font-semibold text-warning">{status}</span>
        ) : null}
      </div>
      <Alert tone="warning">QR minh họa, không chuyển tiền thật.</Alert>
      {error ? <Alert tone="error">{error}</Alert> : null}
      {!order && !error ? <p role="status" className="text-sm text-text-secondary">Đang tải đơn nạp...</p> : null}
      {error && !order && code ? (
        <Button variant="outline" disabled={busy} onClick={() => void update(false)}>
          Thử tải lại
        </Button>
      ) : null}
      {order ? (
        <div className="grid items-start gap-5 md:grid-cols-[300px_minmax(0,1fr)]">
          <section aria-label="Mã QR" className="flex flex-col items-center gap-3 rounded-xl bg-white p-6 text-center ring-1 ring-border/80">
            {waitingForPayment ? (
              <>
                <img className="size-56" src="/images/manual-topup-qr.svg" alt="Mã QR minh họa, không dùng để chuyển tiền thật" />
                <p className="text-sm text-text-secondary">Mở app ngân hàng và quét mã. Số tiền đã điền sẵn.</p>
              </>
            ) : (
              <p className="py-10 text-sm text-text-secondary">
                {order.status === 'SUCCESS' ? 'Đơn đã được cộng Credit.' : 'Mã QR không còn dùng cho đơn này.'}
              </p>
            )}
          </section>
          <section aria-label="Thông tin đơn" className="space-y-4 rounded-xl bg-white p-6 ring-1 ring-border/80">
            <dl className="divide-y divide-border/70 text-sm">
              <CheckoutRow label="Mã đơn" value={order.orderCode} />
              <CheckoutRow label="Số tiền minh họa" value={formatVnd(order.amountVnd)} strong />
              <CheckoutRow label="Credit của đơn" value={`${order.credits} Credit`} />
              {!order.submittedAt ? (
                <CheckoutRow label="Hạn gửi yêu cầu" value={new Date(order.expiresAt).toLocaleString('vi-VN')} />
              ) : null}
            </dl>
            {order.status === 'PENDING' && order.submittedAt ? (
              <Alert>Đã gửi yêu cầu. Credit sẽ được cộng sau khi quản trị viên duyệt.</Alert>
            ) : null}
            {order.status === 'SUCCESS' ? (
              <Alert tone="success">Đã cộng {order.credits} Credit. Ví được tải lại từ hệ thống.</Alert>
            ) : null}
            {order.reviewNote ? <Alert>Ghi chú của quản trị viên: {order.reviewNote}</Alert> : null}
            <div className="flex flex-wrap justify-end gap-2">
              <Button variant="outline" disabled={busy} onClick={() => void update(false)}>
                {busy ? 'Đang xử lý...' : 'Kiểm tra trạng thái'}
              </Button>
              {order.status === 'PENDING' ? (
                <Button size="lg" disabled={busy || !!order.submittedAt} onClick={() => void update(true)}>
                  Đã thanh toán
                </Button>
              ) : null}
            </div>
          </section>
        </div>
      ) : null}
      {order ? (
        <section aria-labelledby="checkout-after" className="rounded-xl bg-white p-6 ring-1 ring-border/80">
          <h2 id="checkout-after" className="text-base font-bold">Sau khi bấm "Đã thanh toán"</h2>
          <ol className="mt-2 list-decimal space-y-1 pl-5 text-sm leading-6 text-text-secondary">
            <li>Đơn chuyển sang chờ duyệt, bạn có thể rời trang.</li>
            <li>Quản trị viên đối chiếu và xác nhận.</li>
            <li>Credit được cộng vào ví, bạn nhận thông báo.</li>
          </ol>
        </section>
      ) : null}
    </div>
  );
}

function CheckoutRow({ label, value, strong = false }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className="flex items-start justify-between gap-5 py-3">
      <dt className="shrink-0 text-text-secondary">{label}</dt>
      <dd className={`break-all text-right tabular-nums ${strong ? 'text-lg font-bold' : 'font-semibold'}`}>{value}</dd>
    </div>
  );
}
