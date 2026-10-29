package com.shareloop.integration.mail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Ghép nội dung email từ file mẫu trong templates/mail. Mẫu dùng placeholder dạng {{ten}};
 * giá trị được escape HTML trước khi thay vào để chữ người dùng nhập không thành thẻ HTML.
 */
@Component
public class MailTemplateRenderer {

    private static final String TEMPLATE_FOLDER = "templates/mail/";
    private static final String TEMPLATE_EXTENSION = ".html";
    private static final String PLACEHOLDER_START = "{{";
    private static final String PLACEHOLDER_END = "}}";

    private static final String REGISTER_NOTE = "Nếu bạn không đăng ký tài khoản ShareLoop, hãy bỏ qua email này.";
    private static final String PASSWORD_RESET_NOTE = "Bạn vừa yêu cầu đặt lại mật khẩu ShareLoop. Nếu không phải bạn,"
            + " hãy bỏ qua email này. Mật khẩu hiện tại vẫn giữ nguyên.";

    private static final String CHANGE_PHONE_NOTE = "Bạn vừa yêu cầu đổi số điện thoại trên ShareLoop. Nếu không phải"
            + " bạn, hãy bỏ qua email này; số điện thoại hiện tại vẫn giữ nguyên.";

    /**
     * Nội dung email OTP đăng ký (FR-02) từ mẫu otp.html.
     *
     * @param code mã OTP thô 6 chữ số
     * @param ttlMinutes số phút mã còn hiệu lực, lấy từ ConfigKey.OTP_TTL_MINUTES ở nơi phát sự kiện
     */
    public String renderOtp(String code, int ttlMinutes) {
        return renderOtpWithNote(code, ttlMinutes, REGISTER_NOTE);
    }

    /** Nội dung email OTP đặt lại mật khẩu (FR-09): cùng mẫu otp.html, chỉ khác dòng ghi chú cuối. */
    public String renderPasswordResetOtp(String code, int ttlMinutes) {
        return renderOtpWithNote(code, ttlMinutes, PASSWORD_RESET_NOTE);
    }

    /** Nội dung email OTP đổi số điện thoại (FR-07) từ mẫu riêng otp-change-phone.html. */
    public String renderChangePhoneOtp(String code, int ttlMinutes) {
        return render(
                "otp-change-phone",
                Map.of("otp", code, "ttlMinutes", String.valueOf(ttlMinutes), "purposeNote", CHANGE_PHONE_NOTE));
    }

    /**
     * Nội dung email thông báo chung (FR-118) từ mẫu notification.html.
     *
     * @throws IllegalArgumentException khi link không bắt đầu bằng http:// hoặc https://
     */
    public String renderNotification(String title, String content, String link) {
        // Escape HTML không chặn được href="javascript:...", nên chỉ nhận link web thật.
        if (!link.startsWith("https://") && !link.startsWith("http://")) {
            throw new IllegalArgumentException("Link trong email phải bắt đầu bằng http:// hoặc https://: " + link);
        }
        return render("notification", Map.of("title", title, "content", content, "link", link));
    }

    /**
     * Đọc templates/mail/{templateName}.html và thay từng {{key}} bằng giá trị đã escape HTML.
     *
     * @throws IllegalStateException khi không có file mẫu hoặc mẫu còn placeholder chưa có giá trị
     */
    public String render(String templateName, Map<String, String> values) {
        String html = readTemplate(templateName);
        checkAllPlaceholdersHaveValues(templateName, html, values);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String placeholder = PLACEHOLDER_START + entry.getKey() + PLACEHOLDER_END;
            // Mã hoá "UTF-8": chỉ escape & < > " ', giữ nguyên chữ tiếng Việt có dấu.
            String safeValue = HtmlUtils.htmlEscape(entry.getValue(), StandardCharsets.UTF_8.name());
            html = html.replace(placeholder, safeValue);
        }
        return html;
    }

    private String renderOtpWithNote(String code, int ttlMinutes, String purposeNote) {
        return render("otp", Map.of("otp", code, "ttlMinutes", String.valueOf(ttlMinutes), "purposeNote", purposeNote));
    }

    private String readTemplate(String templateName) {
        String path = TEMPLATE_FOLDER + templateName + TEMPLATE_EXTENSION;
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw new IllegalStateException("Không tìm thấy mẫu email: " + path);
        }
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Không đọc được mẫu email: " + path, exception);
        }
    }

    // Báo lỗi ngay thay vì gửi cho người dùng một email còn "{{otp}}" trần.
    // Kiểm trên mẫu gốc (trước khi thay) để chữ "{{...}}" do người dùng nhập không bị báo lỗi nhầm.
    private void checkAllPlaceholdersHaveValues(String templateName, String template, Map<String, String> values) {
        int start = template.indexOf(PLACEHOLDER_START);
        while (start >= 0) {
            int end = template.indexOf(PLACEHOLDER_END, start);
            if (end < 0) {
                return;
            }
            String key = template.substring(start + PLACEHOLDER_START.length(), end);
            if (!values.containsKey(key)) {
                throw new IllegalStateException("Mẫu email " + templateName + " thiếu giá trị cho {{" + key + "}}");
            }
            start = template.indexOf(PLACEHOLDER_START, end);
        }
    }
}
