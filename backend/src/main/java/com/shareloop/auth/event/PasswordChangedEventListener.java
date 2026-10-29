package com.shareloop.auth.event;

import com.shareloop.integration.mail.EmailSender;
import com.shareloop.integration.mail.MailTemplateRenderer;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Gửi email báo "mật khẩu vừa được đổi" sau khi transaction đổi mật khẩu đã commit (FR-08). */
@Component
public class PasswordChangedEventListener {

    private static final Logger log = LoggerFactory.getLogger(PasswordChangedEventListener.class);
    private static final String SUBJECT = "Mật khẩu ShareLoop vừa được đổi";
    private static final String TEMPLATE_NAME = "password-changed";
    // Giờ trong email luôn theo giờ Việt Nam, không phụ thuộc múi giờ của máy chủ.
    private static final DateTimeFormatter CHANGED_AT_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm dd/MM").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final EmailSender emailSender;
    private final MailTemplateRenderer mailTemplateRenderer;

    public PasswordChangedEventListener(EmailSender emailSender, MailTemplateRenderer mailTemplateRenderer) {
        this.emailSender = emailSender;
        this.mailTemplateRenderer = mailTemplateRenderer;
    }

    // AFTER_COMMIT: đổi mật khẩu bị rollback thì không gửi mail báo. @Async: SMTP chậm không làm chậm API.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void sendPasswordChangedEmail(PasswordChangedEvent event) {
        try {
            String changedAt = CHANGED_AT_FORMAT.format(event.changedAt());
            String htmlBody = mailTemplateRenderer.render(TEMPLATE_NAME, Map.of("changedAt", changedAt));
            emailSender.send(event.email(), SUBJECT, htmlBody);
        } catch (RuntimeException exception) {
            // Mật khẩu đã đổi xong và commit; gửi mail lỗi chỉ ghi log, không ảnh hưởng việc đổi mật khẩu.
            log.error("Failed to send password-changed email to {}", event.email(), exception);
        }
    }
}
