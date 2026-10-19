package com.shareloop.integration.payment;

import com.shareloop.wallet.entity.PaymentProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Cổng thanh toán giả lập: không gọi bên thứ ba, không có tiền thật. Đưa người dùng tới trang giả lập của FE
 * ({@code /wallet/topup/mock-checkout}); trang đó gọi POST .../{orderCode}/mock-confirm để chốt đơn.
 */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentClient implements PaymentGatewayClient {

    // Đường dẫn trang của FE (tương đối), không phải API backend.
    private static final String MOCK_CHECKOUT_PATH = "/wallet/topup/mock-checkout?orderCode=";

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.MOCK;
    }

    @Override
    public String buildRedirectUrl(String orderCode, long amountVnd) {
        return MOCK_CHECKOUT_PATH + orderCode;
    }
}
