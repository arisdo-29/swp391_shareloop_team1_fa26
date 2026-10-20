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
            ? 'Chờ admin duyệt'
            : 'Chờ thanh toán';

  return (
    <section className="mx-auto my-8 max-w-xl space-y-5 rounded-xl bg-white p-6 shadow-sm ring-1 ring-border">
      <h1 className="text-2xl font-bold">Nạp Credit bằng QR minh họa</h1>
      <Alert tone="warning">QR minh họa — không chuyển tiền thật</Alert>
      {error ? <Alert tone="error">{error}</Alert> : null}
      {!order && !error ? <p>Đang tải đơn nạp...</p> : null}
      {order ? (
        <>
          {order.status === 'PENDING' && !order.submittedAt ? (
            <img
              className="mx-auto size-56"
              src="/images/manual-topup-qr.svg"
              alt="QR minh họa chứa chuỗi SHARELOOP-DEMO-NO-PAYMENT"
            />
          ) : null}
          <dl className="space-y-2 rounded-lg bg-surface-low p-4 text-sm">
            <div>
              <dt>Mã đơn</dt>
              <dd className="break-all font-semibold">{order.orderCode}</dd>
            </div>
            <div>
              <dt>Số tiền minh họa</dt>
              <dd>{formatVnd(order.amountVnd)}</dd>
            </div>
            <div>
              <dt>Credit của đơn</dt>
              <dd>{order.credits} Credit</dd>
            </div>
            <div>
              <dt>Trạng thái</dt>
              <dd className="font-semibold">{status}</dd>
            </div>
            {!order.submittedAt ? (
              <div>
                <dt>Hạn gửi yêu cầu</dt>
                <dd>{new Date(order.expiresAt).toLocaleString('vi-VN')}</dd>
              </div>
            ) : null}
          </dl>
          {order.status === 'PENDING' && order.submittedAt ? (
            <Alert>Đã gửi yêu cầu. Credit sẽ được cộng sau khi admin duyệt</Alert>
          ) : null}
          {order.status === 'SUCCESS' ? (
            <Alert tone="success">
              Đã cộng {order.credits} Credit. Ví được tải lại từ hệ thống.
            </Alert>
          ) : null}
          {order.reviewNote ? <Alert>Ghi chú của admin: {order.reviewNote}</Alert> : null}
          <div className="flex flex-wrap gap-3">
            {order.status === 'PENDING' ? (
              <Button disabled={busy || !!order.submittedAt} onClick={() => void update(true)}>
                Đã thanh toán
              </Button>
            ) : null}
            <Button variant="outline" disabled={busy} onClick={() => void update(false)}>
              {busy ? 'Đang xử lý...' : 'Kiểm tra trạng thái'}
            </Button>
          </div>
        </>
      ) : null}
      <Link
        className="inline-block text-primary underline"
        to="/credit"
        onClick={() => {
          if (order && order.status !== 'PENDING') clearPendingTopUp();
        }}
      >
        Quay về ví và lịch sử Credit
      </Link>
      {error && !order && code ? (
        <Button variant="outline" disabled={busy} onClick={() => void update(false)}>
          Thử tải lại
        </Button>
      ) : null}
    </section>
  );
}
