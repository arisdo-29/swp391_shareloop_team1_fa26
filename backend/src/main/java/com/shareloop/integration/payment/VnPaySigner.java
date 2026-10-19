package com.shareloop.integration.payment;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class VnPaySigner {
    private VnPaySigner() {}

    public static String encodeParameters(Map<String, String> parameters) {
        return new TreeMap<>(parameters)
                .entrySet().stream()
                        .filter(e -> !e.getKey().equals("vnp_SecureHash")
                                && !e.getKey().equals("vnp_SecureHashType"))
                        .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                        .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                        .collect(Collectors.joining("&"));
    }

    public static String sign(Map<String, String> parameters, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of()
                    .formatHex(mac.doFinal(encodeParameters(parameters).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Cannot sign VNPAY parameters", ex);
        }
    }

    public static boolean verify(Map<String, String> parameters, String secret) {
        String supplied = parameters.get("vnp_SecureHash");
        if (secret == null || secret.isBlank() || supplied == null || !supplied.matches("[0-9a-fA-F]{128}")) {
            return false;
        }
        return MessageDigest.isEqual(
                HexFormat.of().parseHex(supplied), HexFormat.of().parseHex(sign(parameters, secret)));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
