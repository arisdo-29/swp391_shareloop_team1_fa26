package com.shareloop.notification.event;

import java.util.Map;

/**
 * Sự kiện yêu cầu gửi email thông báo sau khi transaction commit thành công.
 */
public record NotificationEmailEvent(
        String to, String subject, String templateName, Map<String, Object> templateModel) {}
