package com.shareloop.integration.mail;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Gửi email qua API HTTP của Brevo (cổng 443). Dùng khi nơi deploy chặn cổng SMTP, ví dụ gói free của Render.
 * Bật bằng app.mail.provider=brevo; mặc định hệ thống vẫn dùng SmtpEmailSender.
 */
@Component
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "brevo")
public class BrevoEmailSender implements EmailSender {

    private static final String BREVO_URL = "https://api.brevo.com/v3/smtp/email";

    private final RestClient restClient;
    private final String fromAddress;
    private final String fromName;

    public BrevoEmailSender(
            @Value("${app.mail.brevo.api-key}") String apiKey,
            @Value("${app.mail.from}") String fromAddress,
            @Value("${app.mail.from-name:ShareLoop}") String fromName) {
        this.restClient = RestClient.builder()
                .baseUrl(BREVO_URL)
                .defaultHeader("api-key", apiKey)
                .build();
        this.fromAddress = fromAddress;
        this.fromName = fromName;
    }

    @Override
    public void send(String to, String subject, String htmlBody) {
        // Thân request theo tài liệu Brevo: người gửi, người nhận, tiêu đề, nội dung HTML
        Map<String, Object> body = Map.of(
                "sender",
                Map.of("email", fromAddress, "name", fromName),
                "to",
                List.of(Map.of("email", to)),
                "subject",
                subject,
                "htmlContent",
                htmlBody);
        try {
            restClient
                    .post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Không gửi được email tới " + to, exception);
        }
    }
}
