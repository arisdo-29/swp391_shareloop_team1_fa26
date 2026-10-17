package com.shareloop.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.shareloop.auth.dto.RegisterRequest;
import com.shareloop.auth.service.AuthService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.integration.mail.EmailSender;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.support.IntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Test chống tạo nhiều tài khoản khi đăng ký (FR-04, FR-10) trên PostgreSQL thật. Gọi thẳng AuthService với IP tự
 * chọn trong dải 203.0.113.0/24 (dải dành cho tài liệu, không phải IP thật) để không đụng dữ liệu của test khác.
 */
class AuthRegisterGuardIT extends IntegrationTest {

    private static final String PASSWORD = "Dev@12345";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    AuthService authService;

    @Autowired
    ConfigService configService;

    @Autowired
    JdbcTemplate jdbc;

    // Thay SmtpEmailSender bằng mock: OTP gửi sau commit, test không có SMTP thật.
    @MockitoBean
    EmailSender emailSender;

    private final List<String> createdEmails = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (String email : createdEmails) {
            jdbc.update("DELETE FROM users WHERE email = ?", email);
        }
        createdEmails.clear();
    }

    // ---------- email dùng một lần ----------

    @Test
    void register_whenEmailDomainDisposable_throwsDomainNotAllowedAndCreatesNoUser() {
        // given
        String email = newEmail("mailinator.com");

        // when
        Throwable thrown = catchThrowable(() -> authService.register(registerRequest(email), "203.0.113.10"));

        // then
        assertThat(thrown)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_EMAIL_DOMAIN_NOT_ALLOWED));
        assertThat(userCountByEmail(email)).isZero();
    }

    @Test
    void register_whenEmailDomainNormal_createsPendingUser() {
        // given
        String email = newEmail("gmail.com");

        // when
        authService.register(registerRequest(email), "203.0.113.20");

        // then
        assertThat(userCountByEmail(email)).isEqualTo(1);
    }

    // ---------- giới hạn theo IP ----------

    @Test
    void register_whenIpAlreadyReachedLimitWithin24Hours_throwsTooManyAccounts() {
        // given: IP đã tạo đủ accounts_per_ip_24h tài khoản trong 1 giờ trước
        String ip = "203.0.113.30";
        insertAccountsFromIp(ip, accountsPerIpLimit(), Instant.now().minus(1, ChronoUnit.HOURS));
        String email = newEmail("gmail.com");

        // when: tài khoản kế tiếp (thứ 4 với mặc định 3)
        Throwable thrown = catchThrowable(() -> authService.register(registerRequest(email), ip));

        // then
        assertThat(thrown)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_TOO_MANY_ACCOUNTS_FROM_IP));
        assertThat(userCountByEmail(email)).isZero();
    }

    @Test
    void register_whenIpAccountsOlderThan24Hours_doesNotCountThem() {
        // given: IP đã tạo đủ số tài khoản nhưng từ 25 giờ trước
        String ip = "203.0.113.40";
        insertAccountsFromIp(ip, accountsPerIpLimit(), Instant.now().minus(25, ChronoUnit.HOURS));
        String email = newEmail("gmail.com");

        // when
        authService.register(registerRequest(email), ip);

        // then
        assertThat(userCountByEmail(email)).isEqualTo(1);
    }

    // ---------- hàm hỗ trợ ----------

    private int accountsPerIpLimit() {
        return configService.getInt(ConfigKey.ACCOUNTS_PER_IP_24H);
    }

    // Email riêng cho mỗi lần gọi; ghi lại để cleanUp xoá kể cả khi test lỗi giữa chừng.
    private String newEmail(String domain) {
        String email = "guard-it-" + SEQUENCE.incrementAndGet() + "-" + System.nanoTime() + "@" + domain;
        createdEmails.add(email);
        return email;
    }

    // Số di động hợp lệ (đầu 09), đuôi theo SEQUENCE để không trùng giữa các lần gọi.
    private String newPhone() {
        return String.format("0985%06d", SEQUENCE.incrementAndGet());
    }

    private RegisterRequest registerRequest(String email) {
        return new RegisterRequest(email, PASSWORD, "Người Dùng Guard IT", newPhone());
    }

    private void insertAccountsFromIp(String ip, int count, Instant createdAt) {
        for (int index = 0; index < count; index++) {
            jdbc.update(
                    "INSERT INTO users (email, password_hash, full_name, phone, status, registration_ip, created_at)"
                            + " VALUES (?, 'not-a-real-hash', 'Guard IT', ?, 'PENDING_VERIFICATION', ?, ?)",
                    newEmail("shareloop.local"),
                    newPhone(),
                    ip,
                    Timestamp.from(createdAt));
        }
    }

    private long userCountByEmail(String email) {
        return jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Long.class, email);
    }
}
