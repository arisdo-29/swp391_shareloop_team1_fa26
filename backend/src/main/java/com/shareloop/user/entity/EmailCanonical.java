package com.shareloop.user.entity;

import java.util.Locale;
import java.util.Set;

/**
 * Chuẩn hoá email để kiểm trùng (A3). Chữ thường, bỏ khoảng trắng. Gmail: bỏ phần từ dấu + trở đi, bỏ dấu chấm trong
 * phần tên và đổi googlemail.com về gmail.com. Quy tắc này phải khớp hàm f_email_canonical trong database.
 */
public final class EmailCanonical {

    private static final Set<String> GMAIL_DOMAINS = Set.of("gmail.com", "googlemail.com");

    private EmailCanonical() {}

    public static String of(String email) {
        String cleaned = email.replaceAll("\s", "").toLowerCase(Locale.ROOT);
        int atPosition = cleaned.indexOf('@');
        if (atPosition < 0) {
            return cleaned;
        }
        String localPart = cleaned.substring(0, atPosition);
        String domain = cleaned.substring(atPosition + 1);
        if (GMAIL_DOMAINS.contains(domain)) {
            int plusPosition = localPart.indexOf('+');
            if (plusPosition >= 0) {
                localPart = localPart.substring(0, plusPosition);
            }
            localPart = localPart.replace(".", "");
            domain = "gmail.com";
        }
        return localPart + "@" + domain;
    }
}
