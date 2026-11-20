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
import java.util.Map;
import org.junit.jupiter.api.Test;

class VnPayClientTest {

    private static final String SECRET = "test-secret";

    @Test
    void redirect_usesOrderTimesInVietnamTimeAmountAndValidSignature() {
        // given: đơn tạo 10:00Z, hạn 10:17Z (17:00 và 17:17 giờ Việt Nam)
        var client = client(mock(ConfigService.class), Instant.parse("2026-10-19T09:00:00Z"));

        // when
        Map<String, String> params = query(client.buildRedirectUrl(
                "abc", 20000, Instant.parse("2026-10-19T10:00:00Z"), Instant.parse("2026-10-19T10:17:00Z")));

        // then
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

    @Test
    void expireDate_isVietnamTimeWithFourteenDigits_evenAcrossMidnight() {
        // given: 17:30Z ngày 31/12 là 00:30 ngày 01/01 năm sau ở Việt Nam (UTC+7)
        var client = client(mock(ConfigService.class), Instant.parse("2026-12-31T17:00:00Z"));

        // when
        Map<String, String> params = query(client.buildRedirectUrl(
                "abc", 20000, Instant.parse("2026-12-31T17:15:00Z"), Instant.parse("2026-12-31T17:30:45Z")));

        // then: yyyyMMddHHmmss theo Asia/Ho_Chi_Minh, cùng múi giờ với vnp_CreateDate
        assertThat(params.get("vnp_ExpireDate")).matches("\\d{14}").isEqualTo("20270101003045");
        assertThat(params.get("vnp_CreateDate")).isEqualTo("20270101001500");
    }

    @Test
    void expireDate_isPartOfSignature_soChangingItBreaksTheHash() {
        // given: URL hợp lệ
        var client = client(mock(ConfigService.class), Instant.parse("2026-10-19T09:00:00Z"));
        Map<String, String> params = query(client.buildRedirectUrl(
                "abc", 20000, Instant.parse("2026-10-19T10:00:00Z"), Instant.parse("2026-10-19T10:17:00Z")));
        assertThat(VnPaySigner.verify(params, SECRET)).isTrue();

        // when: đổi vnp_ExpireDate mà giữ nguyên chữ ký
        params.put("vnp_ExpireDate", "20261019235900");

        // then: chữ ký không còn khớp, tức vnp_ExpireDate nằm trong dữ liệu được ký
        assertThat(VnPaySigner.verify(params, SECRET)).isFalse();
    }

    @Test
    void redirect_withoutOrderTimes_fallsBackToClockAndConfiguredMinutes() {
        // given: đơn chưa có created_at (chỉ xảy ra khi chưa lưu), cấu hình 17 phút
        ConfigService config = mock(ConfigService.class);
        when(config.getInt(ConfigKey.PAYMENT_ORDER_EXPIRE_MINUTES)).thenReturn(17);
        var client = client(config, Instant.parse("2026-10-19T10:00:00Z"));

        // when
        Map<String, String> params = query(client.buildRedirectUrl("abc", 20000, null, null));

        // then
        assertThat(params)
                .containsEntry("vnp_CreateDate", "20261019170000")
                .containsEntry("vnp_ExpireDate", "20261019171700");
    }

    private static VnPayClient client(ConfigService config, Instant now) {
        return new VnPayClient(
                config,
                Clock.fixed(now, ZoneOffset.UTC),
                "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
                "https://example.com/return",
                "TEST",
                SECRET,
                "https://example.com/credit");
    }

    private static Map<String, String> query(String url) {
        var params = new HashMap<String, String>();
        for (String pair : URI.create(url).getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            params.put(
                    URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        return params;
    }
}
