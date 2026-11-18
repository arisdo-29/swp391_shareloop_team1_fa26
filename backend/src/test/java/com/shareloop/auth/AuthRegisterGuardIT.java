package com.shareloop.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.shareloop.auth.dto.RegisterRequest;
import com.shareloop.auth.service.AuthService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.integration.mail.EmailSender;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.SettingErrorCode;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.support.IntegrationTest;
import com.shareloop.user.service.UserService;
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
 * Test các quy tắc đăng ký và cảnh báo cùng IP trên PostgreSQL thật. Gọi thẳng AuthService với IP tự
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
    UserService userService;

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

    @Test
    void register_fourAccountsOnSameIp_allSucceedAndOnlyVerifiedAccountsCountForAlerts() {
        String ip = "203.0.113.30";
        Instant since = Instant.now().minus(1, ChronoUnit.DAYS);
        for (int index = 0; index < 4; index++) {
            String email = newEmail("gmail.com");
            authService.register(registerRequest(email), ip);
            assertThat(jdbc.queryForObject("SELECT registration_ip FROM users WHERE email = ?", String.class, email))
                    .isEqualTo(ip);
        }

        assertThat(userService.findSharedIpGroups(since, 2)).noneMatch(group -> ip.equals(group.ipAddress()));
        for (String email : createdEmails.subList(0, 3)) {
            jdbc.update("UPDATE users SET status = 'ACTIVE' WHERE email = ?", email);
        }
        assertThat(userService.findSharedIpGroups(since, 3))
                .filteredOn(group -> ip.equals(group.ipAddress()))
                .singleElement()
                .satisfies(group -> assertThat(group.accounts()).hasSize(3));
        assertThat(userService.findSharedIpGroups(since, 4)).noneMatch(group -> ip.equals(group.ipAddress()));
    }

    @Test
    void ipAlertConfig_newDefaultIsFiveAndOneIsRejected() {
        assertThat(configService.getInt(ConfigKey.IP_ALERT_MIN_ACCOUNTS)).isEqualTo(5);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM website_attributes"
                                + " WHERE attr_group = 'CONFIG' AND attr_key = 'accounts_per_ip_24h' AND NOT is_deleted",
                        Long.class))
                .isZero();
        Throwable thrown = catchThrowable(() -> configService.validateValue(ConfigKey.IP_ALERT_MIN_ACCOUNTS, "1"));
        assertThat(thrown)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SettingErrorCode.SETTING_CONFIG_OUT_OF_RANGE));
        configService.validateValue(ConfigKey.IP_ALERT_MIN_ACCOUNTS, "2");
    }

    // ---------- hàm hỗ trợ ----------

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

    private long userCountByEmail(String email) {
        return jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Long.class, email);
    }
}
