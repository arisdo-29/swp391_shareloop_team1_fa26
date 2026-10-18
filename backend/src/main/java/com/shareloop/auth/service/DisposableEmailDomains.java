package com.shareloop.auth.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Danh sách tên miền email dùng một lần (FR-04), đọc một lần từ {@code disposable-email-domains.txt} khi ứng dụng khởi
 * động rồi giữ trong bộ nhớ. Bean Spring mặc định là singleton nên constructor chỉ chạy một lần.
 */
@Component
public class DisposableEmailDomains {

    private static final String FILE_PATH = "disposable-email-domains.txt";
    private static final String COMMENT_PREFIX = "#";

    private final Set<String> domains;

    public DisposableEmailDomains() {
        this.domains = parseDomains(readFile());
    }

    /** Email có tên miền (phần sau @) nằm trong danh sách hay không; không phân biệt hoa thường. */
    public boolean isDisposable(String email) {
        int atIndex = email.lastIndexOf('@');
        if (atIndex < 0) {
            return false;
        }
        String domain = email.substring(atIndex + 1).trim().toLowerCase(Locale.ROOT);
        return domains.contains(domain);
    }

    // Tách ra hàm riêng (package-private) để unit test kiểm được cách đọc dòng mà không cần file thật.
    static Set<String> parseDomains(String content) {
        Set<String> result = new HashSet<>();
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith(COMMENT_PREFIX)) {
                continue;
            }
            result.add(trimmed.toLowerCase(Locale.ROOT));
        }
        return result;
    }

    // Thiếu file là lỗi đóng gói: báo ngay lúc khởi động thay vì âm thầm cho mọi email đi qua.
    private static String readFile() {
        ClassPathResource resource = new ClassPathResource(FILE_PATH);
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Không đọc được danh sách tên miền email: " + FILE_PATH, exception);
        }
    }
}
