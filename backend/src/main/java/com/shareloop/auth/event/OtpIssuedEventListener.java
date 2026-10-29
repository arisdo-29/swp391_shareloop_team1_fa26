package com.shareloop.auth.event;

import com.shareloop.integration.mail.EmailSender;
import com.shareloop.integration.mail.MailTemplateRenderer;
import com.shareloop.user.entity.OtpPurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Gửi email OTP sau khi transaction cấp mã đã commit, để không gửi mã cho một tài khoản chưa được lưu. */
@Component
public class OtpIssuedEventListener {

    private static final String REGISTER_SUBJECT = "Mã xác thực ShareLoop";
    private static final String RESET_PASSWORD_SUBJECT = "Đặt lại mật khẩu ShareLoop";
    private static final String CHANGE_PHONE_SUBJECT = "Mã xác nhận đổi số điện thoại";

    private static final Logger log = LoggerFactory.getLogger(OtpIssuedEventListener.class);

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
        try {
            sendByPurpose(event);
        } catch (RuntimeException exception) {
            // Gửi mail lỗi không được làm hỏng việc cấp OTP (đã commit). Log kèm mục đích và người nhận để tra lỗi;
            // tuyệt đối không log event.code() vì đó là mã OTP thô.
            log.error("Failed to send OTP email (purpose={}) to {}", event.purpose(), event.email(), exception);
        }
    }

    private void sendByPurpose(OtpIssuedEvent event) {
        if (event.purpose() == OtpPurpose.RESET_PASSWORD) {
            String htmlBody = mailTemplateRenderer.renderPasswordResetOtp(event.code(), event.ttlMinutes());
            emailSender.send(event.email(), RESET_PASSWORD_SUBJECT, htmlBody);
            return;
        }
        if (event.purpose() == OtpPurpose.CHANGE_PHONE) {
            String htmlBody = mailTemplateRenderer.renderChangePhoneOtp(event.code(), event.ttlMinutes());
            emailSender.send(event.email(), CHANGE_PHONE_SUBJECT, htmlBody);
            return;
        }
        String htmlBody = mailTemplateRenderer.renderOtp(event.code(), event.ttlMinutes());
        emailSender.send(event.email(), REGISTER_SUBJECT, htmlBody);
    }
}
