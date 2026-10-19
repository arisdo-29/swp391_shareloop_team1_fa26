package com.shareloop.integration.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VnPaySignerTest {
    @Test
    void sign_matchesIndependentlyCalculatedHmacSha512() {
        var params =
                new HashMap<>(Map.of("vnp_TxnRef", "abc", "vnp_OrderInfo", "Nap Credit abc", "vnp_Amount", "2000000"));
        assertThat(VnPaySigner.encodeParameters(params))
                .isEqualTo("vnp_Amount=2000000&vnp_OrderInfo=Nap+Credit+abc&vnp_TxnRef=abc");
        // Fixture calculated independently with System.Security.Cryptography.HMACSHA512.
        String expected =
                "0d7573de782f529285947ce0c1f9e90705c98b5ed1e7ded216630d7c3f29a66d2f123924c26f893adb01b4dd40dc108cdecd941f7019f57bd01c92b20dbc113f";
        assertThat(VnPaySigner.sign(params, "test-secret")).isEqualTo(expected);
        params.put("vnp_SecureHash", expected.toUpperCase(java.util.Locale.ROOT));
        params.put("vnp_SecureHashType", "SHA512");
        assertThat(VnPaySigner.verify(params, "test-secret")).isTrue();
        params.put("vnp_Amount", "3000000");
        assertThat(VnPaySigner.verify(params, "test-secret")).isFalse();
    }

    @Test
    void verify_rejectsMissingMalformedHashAndEmptySecret() {
        assertThat(VnPaySigner.verify(Map.of(), "test-secret")).isFalse();
        assertThat(VnPaySigner.verify(Map.of("vnp_SecureHash", "bad"), "test-secret"))
                .isFalse();
        assertThat(VnPaySigner.verify(Map.of("vnp_SecureHash", "0".repeat(128)), ""))
                .isFalse();
    }
}
