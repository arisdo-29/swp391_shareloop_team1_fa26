package com.shareloop.auth.event;

import com.shareloop.integration.mail.EmailSender;
import com.shareloop.integration.mail.MailTemplateRenderer;
import com.shareloop.user.entity.OtpPurpose;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Gửi email OTP sau khi transaction cấp mã đã commit, để không gửi mã cho một tài khoản chưa được lưu. */
@Component
public class OtpIssuedEventListener {

    private static final String REGISTER_SUBJECT = "Mã xác thực ShareLoop";
    private static final String RESET_PASSWORD_SUBJECT = "Đặt lại mật khẩu ShareLoop";

    private final EmailSender emailSender;
    private final MailTemplateRenderer mailTemplateRenderer;

    public OtpIssuedEventListener(EmailSender emailSender, MailTemplateRenderer mailTemplateRenderer) {
        this.emailSender = emailSender;
        this.mailTemplateRenderer = mailTemplateRenderer;
    }

    // AFTER_COMMIT: chỉ chạy khi transaction của người phát sự kiện commit thành công; rollback thì bỏ qua.
    // @Async: chạy ở luồng khác (asyncExecutor) nên SMTP chậm cũng không làm chậm phản hồi API.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void sendOtpEmail(OtpIssuedEvent event) {
        if (event.purpose() == OtpPurpose.RESET_PASSWORD) {
            String htmlBody = mailTemplateRenderer.renderPasswordResetOtp(event.code(), event.ttlMinutes());
            emailSender.send(event.email(), RESET_PASSWORD_SUBJECT, htmlBody);
            return;
        }
        String htmlBody = mailTemplateRenderer.renderOtp(event.code(), event.ttlMinutes());
        emailSender.send(event.email(), REGISTER_SUBJECT, htmlBody);
    }
}
