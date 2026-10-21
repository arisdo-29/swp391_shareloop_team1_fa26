package com.shareloop.integration.payment;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Test data and signatures independent of the production signer. */
public final class VnPayTestData {
    private VnPayTestData() {}

    public static Properties officialFixture() throws IOException {
        var fixture = new Properties();
        try (var input = VnPayTestData.class.getResourceAsStream("/payment/vnpay-official-return.properties")) {
            if (input == null) throw new IOException("Missing VNPAY fixture");
            fixture.load(input);
        }
        return fixture;
    }

    public static Map<String, String> officialCallback() throws IOException {
        var fixture = officialFixture();
        var params = new TreeMap<String, String>();
        for (String pair : fixture.getProperty("canonical").split("&")) {
            String[] parts = pair.split("=", 2);
            params.put(
                    URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        params.put("vnp_SecureHash", fixture.getProperty("expected-hash"));
        return params;
    }

    public static void signCallback(Map<String, String> params) {
        try {
            var canonical = new StringBuilder();
            for (var entry : new TreeMap<>(params).entrySet()) {
                if (entry.getKey().equals("vnp_SecureHash")
                        || entry.getKey().equals("vnp_SecureHashType")
                        || entry.getValue() == null
                        || entry.getValue().isEmpty()) continue;
                if (!canonical.isEmpty()) canonical.append('&');
                canonical
                        .append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                        .append('=')
                        .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            }
            var mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            params.put(
                    "vnp_SecureHash",
                    HexFormat.of().formatHex(mac.doFinal(canonical.toString().getBytes(StandardCharsets.UTF_8))));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
