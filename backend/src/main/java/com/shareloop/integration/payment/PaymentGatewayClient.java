package com.shareloop.integration.payment;

import com.shareloop.wallet.entity.PaymentProvider;
import java.time.Instant;

/**
 * Điểm nối tới cổng thanh toán. Chọn {@link MockPaymentClient} hoặc {@link VnPayClient}
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
     * @param createdAt lúc tạo đơn (vnp_CreateDate)
     * @param expiresAt hạn thanh toán của đơn do PaymentDeadline tính (vnp_ExpireDate); cùng mốc trả cho FE đếm ngược
     */
    String buildRedirectUrl(String orderCode, long amountVnd, Instant createdAt, Instant expiresAt);
}
