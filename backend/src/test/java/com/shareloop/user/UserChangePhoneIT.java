package com.shareloop.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.integration.mail.EmailSender;
import com.shareloop.support.IntegrationTest;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration test đổi số điện thoại bằng OTP email (FR-07, BR-U10):
 * DB PostgreSQL thật (Testcontainers), email mock qua EmailSender, kiểm tra đầy đủ các ràng buộc:
 * OTP email, giãn cách 30 ngày, trùng số, giao dịch đang diễn ra.
 */
@AutoConfigureMockMvc
class UserChangePhoneIT extends IntegrationTest {

    private static final String USER_EMAIL = "change-phone-it@shareloop.local";
    private static final String OTHER_EMAIL = "other-phone-it@shareloop.local";
    private static final String INITIAL_PHONE = "0901555555";
    private static final String OTHER_PHONE = "0901777777";
    private static final String NEW_PHONE = "0987654321";
    private static final Pattern SIX_DIGITS = Pattern.compile("\\b(\\d{6})\\b");
    private static final long MAIL_TIMEOUT_MILLIS = 5000;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JwtService jwtService;

    @MockitoBean
    EmailSender emailSender;

    private long userId;
    private long otherUserId;
    private String token;

    @BeforeEach
    void setUp() {
        jdbc.update(
                "INSERT INTO users (email, password_hash, full_name, phone, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                USER_EMAIL,
                passwordEncoder.encode("Dev@12345"),
                "Người Dùng Đổi SĐT",
                INITIAL_PHONE);
        userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, USER_EMAIL);
        token = "Bearer " + jwtService.issue(userId, false);

        jdbc.update(
                "INSERT INTO users (email, password_hash, full_name, phone, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                OTHER_EMAIL,
                passwordEncoder.encode("Dev@12345"),
                "Người Dùng Khác",
                OTHER_PHONE);
        otherUserId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, OTHER_EMAIL);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM requests WHERE receiver_id IN (?, ?)", userId, otherUserId);
        jdbc.update("DELETE FROM items WHERE donor_id IN (?, ?)", userId, otherUserId);
        jdbc.update("DELETE FROM users WHERE id IN (?, ?)", userId, otherUserId);
    }

    @Test
    void changePhone_success_flow() throws Exception {
        // 1. Gửi OTP đến email
        String otpBody = "{\"newPhone\":\"" + NEW_PHONE + "\"}";
        mockMvc.perform(post("/api/v1/me/phone/otp")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(otpBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(USER_EMAIL))
                .andExpect(jsonPath("$.otpExpiresInSeconds").value(300))
                .andExpect(jsonPath("$.resendCooldownSeconds").value(60));

        // 2. Kiểm tra DB đã lưu pending_phone
        String pendingPhone = jdbc.queryForObject("SELECT pending_phone FROM users WHERE id = ?", String.class, userId);
        assertThat(pendingPhone).isEqualTo(NEW_PHONE);

        // 3. Trích xuất mã OTP từ email mock
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailSender, timeout(MAIL_TIMEOUT_MILLIS)).send(eq(USER_EMAIL), anyString(), bodyCaptor.capture());
        Matcher matcher = SIX_DIGITS.matcher(bodyCaptor.getValue());
        assertThat(matcher.find()).isTrue();
        String otp = matcher.group(1);

        // 4. Xác nhận OTP và cập nhật số điện thoại
        String confirmBody = "{\"newPhone\":\"" + NEW_PHONE + "\",\"otp\":\"" + otp + "\"}";
        mockMvc.perform(put("/api/v1/me/phone")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value(NEW_PHONE));

        // 5. Kiểm tra DB sau khi cập nhật thành công
        Map<String, Object> userRow = jdbc.queryForMap(
                "SELECT phone, pending_phone, phone_changed_at, otp_code_hash FROM users WHERE id = ?", userId);
        assertThat(userRow.get("phone")).isEqualTo(NEW_PHONE);
        assertThat(userRow.get("pending_phone")).isNull();
        assertThat(userRow.get("phone_changed_at")).isNotNull();
        assertThat(userRow.get("otp_code_hash")).isNull();
    }

    @Test
    void changePhone_whenWrongOtp_returns400OtpInvalid() throws Exception {
        // Gửi OTP
        String otpBody = "{\"newPhone\":\"" + NEW_PHONE + "\"}";
        mockMvc.perform(post("/api/v1/me/phone/otp")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(otpBody))
                .andExpect(status().isOk());

        // Nhập sai OTP
        String confirmBody = "{\"newPhone\":\"" + NEW_PHONE + "\",\"otp\":\"000000\"}";
        mockMvc.perform(put("/api/v1/me/phone")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_OTP_INVALID"));

        // Số điện thoại không đổi
        String currentPhone = jdbc.queryForObject("SELECT phone FROM users WHERE id = ?", String.class, userId);
        assertThat(currentPhone).isEqualTo(INITIAL_PHONE);
    }

    @Test
    void sendOtp_whenSameAsCurrentPhone_returns400PhoneSameAsCurrent() throws Exception {
        String body = "{\"newPhone\":\"" + INITIAL_PHONE + "\"}";
        mockMvc.perform(post("/api/v1/me/phone/otp")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USER_PHONE_SAME_AS_CURRENT"));
    }

    @Test
    void sendOtp_whenPhoneAlreadyUsedByAnotherUser_returns409PhoneAlreadyUsed() throws Exception {
        String body = "{\"newPhone\":\"" + OTHER_PHONE + "\"}";
        mockMvc.perform(post("/api/v1/me/phone/otp")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_PHONE_ALREADY_USED"));
    }

    @Test
    void sendOtp_whenWithin30DaysCooldown_returns400Cooldown() throws Exception {
        // Giả lập user đã đổi số cách đây 5 ngày
        jdbc.update("UPDATE users SET phone_changed_at = NOW() - INTERVAL '5 days' WHERE id = ?", userId);

        String body = "{\"newPhone\":\"" + NEW_PHONE + "\"}";
        mockMvc.perform(post("/api/v1/me/phone/otp")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USER_PHONE_CHANGE_COOLDOWN"))
                .andExpect(jsonPath("$.details.retryAfterDays").isNumber());
    }

    @Test
    void sendOtp_whenHasActiveDelivery_returns409ActiveDelivery() throws Exception {
        // Tạo 1 item của otherUser và 1 request đang ở trạng thái LOGISTICS_CONFIRMED cho userId
        Long categoryId = jdbc.queryForObject("SELECT id FROM item_categories LIMIT 1", Long.class);
        Long areaId = jdbc.queryForObject("SELECT id FROM areas LIMIT 1", Long.class);
        Long itemId = jdbc.queryForObject("""
                INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, condition, status)
                VALUES (?, ?, ?, 'GIVE', 'Sách test', 'Mô tả', 'GOOD', 'APPROVED')
                RETURNING id
                """, Long.class, otherUserId, categoryId, areaId);

        jdbc.update("""
                INSERT INTO requests (item_id, receiver_id, type, status)
                VALUES (?, ?, 'GIVE', 'LOGISTICS_CONFIRMED')
                """, itemId, userId);

        String body = "{\"newPhone\":\"" + NEW_PHONE + "\"}";
        mockMvc.perform(post("/api/v1/me/phone/otp")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_PHONE_CHANGE_ACTIVE_DELIVERY"));
    }
}
