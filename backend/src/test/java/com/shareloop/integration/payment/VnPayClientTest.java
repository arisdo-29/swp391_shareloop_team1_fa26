package com.shareloop.integration.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

class VnPayClientTest {
    @Test
    void redirect_usesConfiguredExpiryLocalTimeAmountAndValidSignature() {
        ConfigService config = mock(ConfigService.class);
        when(config.getInt(ConfigKey.PAYMENT_ORDER_EXPIRE_MINUTES)).thenReturn(17);
        var client = new VnPayClient(
                config,
                Clock.fixed(Instant.parse("2026-10-19T10:00:00Z"), ZoneOffset.UTC),
                "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
                "https://example.com/return",
                "TEST",
                "test-secret");
        var params = new HashMap<String, String>();
        for (String pair :
                URI.create(client.buildRedirectUrl("abc", 20000)).getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            params.put(
                    URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        assertThat(params)
                .containsEntry("vnp_CreateDate", "20261019170000")
                .containsEntry("vnp_ExpireDate", "20261019171700")
                .containsEntry("vnp_Amount", "2000000")
                .containsEntry("vnp_Version", "2.1.0")
                .containsEntry("vnp_TmnCode", "TEST")
                .containsEntry("vnp_IpAddr", "127.0.0.1")
                .doesNotContainKey("vnp_SecureHashType")
                .containsEntry("vnp_TxnRef", "abc")
                .containsEntry("vnp_ReturnUrl", "https://example.com/return");
        String actualHash = params.get("vnp_SecureHash");
        VnPayTestData.signCallback(params);
        assertThat(actualHash).isEqualTo(params.get("vnp_SecureHash"));
    }
}
