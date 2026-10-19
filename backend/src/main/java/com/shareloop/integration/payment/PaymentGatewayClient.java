package com.shareloop.integration.payment;

import com.shareloop.wallet.entity.PaymentProvider;

/**
 * Điểm nối tới cổng thanh toán (Lộ trình C11). Chọn {@link MockPaymentClient} hoặc {@link VnPayClient}
 * bằng cấu hình app.payment.provider.
 */
public interface PaymentGatewayClient {

    /** Cổng nào; ghi vào payment_orders.provider. */
    PaymentProvider provider();

    /**
     * Địa chỉ chuyển người dùng tới để thanh toán đơn.
     *
     * @param orderCode mã đơn gửi sang cổng
     * @param amountVnd số tiền cần thanh toán (VNĐ)
     */
    String buildRedirectUrl(String orderCode, long amountVnd);
}
