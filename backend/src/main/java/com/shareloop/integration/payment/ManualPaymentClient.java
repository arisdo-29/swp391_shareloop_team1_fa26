package com.shareloop.integration.payment;

import com.shareloop.wallet.entity.PaymentProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "manual")
public class ManualPaymentClient implements PaymentGatewayClient {
    public PaymentProvider provider() {
        return PaymentProvider.MANUAL;
    }

    public String buildRedirectUrl(String orderCode, long amountVnd) {
        return "/wallet/topup/manual-checkout?orderCode=" + orderCode;
    }
}
