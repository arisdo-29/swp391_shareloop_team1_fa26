package com.shareloop.auth.event;

import com.shareloop.integration.mail.EmailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Gửi email OTP sau khi transaction đăng ký đã commit, để không gửi mã cho một tài khoản chưa được lưu. */
@Component
public class OtpIssuedEventListener {

    private static final String SUBJECT = "Mã xác thực ShareLoop";

    private final EmailSender emailSender;

    public OtpIssuedEventListener(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    // AFTER_COMMIT: chỉ chạy khi transaction của người phát sự kiện commit thành công; rollback thì bỏ qua.
    // @Async: chạy ở luồng khác (asyncExecutor) nên SMTP chậm cũng không làm chậm phản hồi API.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void sendOtpEmail(OtpIssuedEvent event) {
        emailSender.send(event.email(), SUBJECT, buildBody(event));
    }

    private String buildBody(OtpIssuedEvent event) {
        return "<p>Mã xác thực của bạn là: <b>" + event.code() + "</b></p>" + "<p>Mã có hiệu lực trong "
                + event.ttlMinutes() + " phút.</p>";
    }
}
