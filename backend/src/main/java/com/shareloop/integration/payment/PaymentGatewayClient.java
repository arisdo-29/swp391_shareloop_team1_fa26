package com.shareloop.integration.payment;

import com.shareloop.wallet.entity.PaymentProvider;

/**
 * Điểm nối tới cổng thanh toán (Lộ trình C11). Hiện chỉ có {@link MockPaymentClient}; khi làm VNPay sẽ thêm
 * VnPayClient cài cùng interface này, PaymentService không phải sửa.
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
