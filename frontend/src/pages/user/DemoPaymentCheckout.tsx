import { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useWallet } from '../../app/useWallet';
import { Alert, Button, Icon } from '../../components/ui';
import {
  confirmDemoPayment,
  demoPaymentEnabled,
  demoPaymentErrorMessage,
  demoPaymentUnavailableMessage,
} from '../../services/paymentFlow';
import { clearPendingTopUp, loadPendingTopUp } from '../../services/topUpSession';
import { formatVnd } from '../../utils/formatting';
import { paymentApi, type TopUpOrderResponse } from '../../services/paymentApi';

export function DemoPaymentCheckout() {
  const navigate = useNavigate();
  const location = useLocation();
  const { replaceWallet } = useWallet();
  const [processing, setProcessing] = useState(false);
  const [error, setError] = useState('');
  const orderCode = useMemo(
    () => new URLSearchParams(location.search).get('orderCode'),
    [location.search],
  );
  const [order, setOrder] = useState<TopUpOrderResponse | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const pendingOrderCode = orderCode ?? loadPendingTopUp()?.orderCode;
  const [loading, setLoading] = useState(Boolean(pendingOrderCode) && demoPaymentEnabled);

  const returnToCredit = () => {
    if (!error) clearPendingTopUp();
    navigate('/credit', { replace: true });
  };

  useEffect(() => {
    let active = true;
    const code = orderCode ?? loadPendingTopUp()?.orderCode;
    if (!code || !demoPaymentEnabled) return;
    setLoading(true);
    setError('');
    paymentApi
      .getOrder(code)
      .then((result) => {
        if (active && result.provider === 'MOCK') setOrder({ ...result, redirectUrl: '' });
      })
      .catch((caught) => {
        if (active) setError(demoPaymentErrorMessage(caught));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [orderCode, reloadKey]);

  const confirm = () => {
    if (!order || processing) return;
    setProcessing(true);
    confirmDemoPayment(order)
      .then((result) => {
        replaceWallet(result.wallet);
        clearPendingTopUp();
        navigate('/credit', {
          replace: true,
        });
      })
      .catch((caught) => {
        setError(demoPaymentErrorMessage(caught));
        setProcessing(false);
      });
  };

  const discardInvalidOrder = () => {
    clearPendingTopUp();
    navigate('/credit', { replace: true });
  };

  if (loading) {
    return (
      <div className="page-shell py-10">
        <div className="mx-auto max-w-xl rounded-xl bg-white p-6 text-center shadow-sm ring-1 ring-border/80 sm:p-8">
          <Alert>Đang tải đơn thanh toán...</Alert>
        </div>
      </div>
    );
  }

  if (!order || !demoPaymentEnabled) {
    // Lỗi tải đơn (mất mạng, máy chủ lỗi) cho thử lại; đơn không tồn tại thì chỉ cho quay về.
    const message = !demoPaymentEnabled
      ? demoPaymentUnavailableMessage
      : error || 'Không tìm thấy đơn thanh toán hợp lệ. Vui lòng tạo lại đơn nạp Credit.';
    return (
      <div className="page-shell py-10">
        <div className="mx-auto max-w-xl rounded-xl bg-white p-6 text-center shadow-sm ring-1 ring-border/80 sm:p-8">
          <Alert tone="error">{message}</Alert>
          <div className="mt-5 flex flex-wrap justify-center gap-2">
            {error && demoPaymentEnabled ? (
              <Button variant="outline" onClick={() => setReloadKey((key) => key + 1)}>
                Thử tải lại
              </Button>
            ) : null}
            <Button variant="outline" onClick={discardInvalidOrder}>
              Quay về trang Credit
            </Button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="page-shell py-8 sm:py-12">
      <section className="mx-auto max-w-xl overflow-hidden rounded-xl bg-white shadow-lg ring-1 ring-border/80">
        <header className="bg-primary px-6 py-7 text-center text-white sm:px-8">
          <span className="mx-auto grid size-12 place-items-center rounded-lg bg-white/15 ring-1 ring-white/25">
            <Icon name="renew" className="size-6" weight="bold" />
          </span>
          <p className="mt-3 text-lg font-extrabold tracking-wide">
            SHARE<span className="text-primary-fixed">LOOP</span>
          </p>
          <h1 className="mt-4 text-2xl font-bold">Xác nhận thanh toán thử nghiệm</h1>
          <p className="mt-1 text-xs font-medium text-white/70">Không sử dụng tiền thật</p>
        </header>

        <div className="p-6 sm:p-8">
          {error ? (
            <div className="mb-5">
              <Alert tone="error">{error}</Alert>
            </div>
          ) : null}

          <dl className="divide-y divide-border rounded-lg bg-surface-low px-4">
            <CheckoutRow label="Mã đơn" value={order.orderCode} />
            <CheckoutRow label="Số tiền" value={formatVnd(order.amountVnd)} />
            <CheckoutRow label="Credit nhận được" value={`${order.credits} Credit`} emphasized />
          </dl>

          <p className="mt-5 flex items-center justify-center gap-2 text-xs text-text-muted">
            <Icon name="shield" className="size-4 text-primary" />
            Vui lòng không đóng trang trong lúc giao dịch đang được xử lý.
          </p>

          {processing ? (
            <div className="mt-7 flex justify-center">
              <span className="inline-flex items-center gap-2 text-sm font-semibold text-primary">
                <Icon name="renew" className="size-5 animate-spin" />
                Đang xử lý thanh toán...
              </span>
            </div>
          ) : (
            <div className="mt-7 space-y-3">
              <Button className="w-full" onClick={confirm}>
                Xác nhận thanh toán thử nghiệm
              </Button>
              <Button className="w-full" variant="outline" size="lg" onClick={returnToCredit}>
                Quay về trang Credit
              </Button>
            </div>
          )}
        </div>
      </section>
    </div>
  );
}

function CheckoutRow({
  label,
  value,
  emphasized = false,
}: {
  label: string;
  value: string;
  emphasized?: boolean;
}) {
  return (
    <div className="flex items-start justify-between gap-5 py-4 text-sm">
      <dt className="shrink-0 text-text-muted">{label}</dt>
      <dd
        className={`break-all text-right font-semibold tabular-nums ${emphasized ? 'text-primary' : 'text-text-primary'}`}
      >
        {value}
      </dd>
    </div>
  );
}
