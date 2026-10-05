package com.shareloop.integration.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** Gửi email qua SMTP. Ở máy dev, SMTP là Mailpit (localhost:1025), xem thư ở http://localhost:8025. */
@Component
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender javaMailSender;
    private final String fromAddress;

    public SmtpEmailSender(JavaMailSender javaMailSender, @Value("${app.mail.from}") String fromAddress) {
        this.javaMailSender = javaMailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            // true = cho phép nội dung nhiều phần; "UTF-8" để tiếng Việt không bị lỗi font.
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true); // true = nội dung là HTML
            javaMailSender.send(message);
        } catch (MessagingException exception) {
            throw new IllegalStateException("Không gửi được email tới " + to, exception);
        }
    }
}
