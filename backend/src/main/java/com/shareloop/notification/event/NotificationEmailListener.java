package com.shareloop.notification.event;

import com.shareloop.integration.mail.EmailSender;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Lắng nghe sự kiện gửi email thông báo sau commit, thực thi bất đồng bộ (@Async).
 */
@Component
public class NotificationEmailListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEmailListener.class);

    private final EmailSender emailSender;

    public NotificationEmailListener(@Autowired(required = false) EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Async
    public void onEmailEvent(NotificationEmailEvent event) {
        if (emailSender == null) {
            log.info("EmailSender not configured. Skipping email to {}", event.to());
            return;
        }

        try {
            String htmlBody = buildHtmlBody(event.templateName(), event.templateModel());
            emailSender.send(event.to(), event.subject(), htmlBody);
        } catch (Exception e) {
            log.error("Failed to send notification email to {}", event.to(), e);
        }
    }

    private String buildHtmlBody(String templateName, Map<String, Object> templateModel) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-family: Arial, sans-serif; line-height: 1.6; padding: 20px;\">");
        sb.append("<h2>Thông báo từ ShareLoop</h2>");
        if (templateModel != null && !templateModel.isEmpty()) {
            for (Map.Entry<String, Object> entry : templateModel.entrySet()) {
                sb.append("<p><strong>")
                        .append(entry.getKey())
                        .append(":</strong> ")
                        .append(entry.getValue())
                        .append("</p>");
            }
        }
        sb.append("<hr style=\"border: none; border-top: 1px solid #eee; margin: 20px 0;\" />");
        sb.append("<p style=\"color: #888; font-size: 12px;\">Đây là email tự động, vui lòng không trả lời.</p>");
        sb.append("</div>");
        return sb.toString();
    }
}
