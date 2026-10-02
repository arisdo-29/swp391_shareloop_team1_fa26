package com.shareloop.notification.service;

import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Service cửa ngõ cho thông báo trong ứng dụng và gửi email giao dịch (Lộ trình 4.7, SRS mục 9.15).
 *
 * <p>Mọi module khác gọi gửi thông báo (chuông) hoặc gửi email thông báo qua service này.
 *
 * <p>Người gọi: mọi module (auth, item, review, request, report, reputation).
 */
@Service
public class NotificationService {

    private static final String TODO = "TODO BE3 - tuan 5/6";

    /**
     * Tạo thông báo trong app (hiển thị ở icon chuông thông báo).
     *
     * @param userId người nhận thông báo
     * @param type loại thông báo (POST_APPROVED, NEW_REQUEST, SELECTED, LOGISTICS_CONFIRMED, ...)
     * @param title tiêu đề thông báo
     * @param body nội dung thông báo
     * @param link đường dẫn chuyển hướng trong frontend (có thể null)
     */
    public void notify(long userId, String type, String title, String body, String link) {
        throw new UnsupportedOperationException(TODO);
    }

    /**
     * Gửi email theo template HTML (ví dụ contact-revealed.html).
     *
     * @param to địa chỉ email người nhận
     * @param subject tiêu đề email
     * @param templateName tên template trong resources/templates/mail/ (không gồm đuôi .html)
     * @param templateModel dữ liệu cần điền vào template
     */
    public void email(String to, String subject, String templateName, Map<String, Object> templateModel) {
        throw new UnsupportedOperationException(TODO);
    }
}
