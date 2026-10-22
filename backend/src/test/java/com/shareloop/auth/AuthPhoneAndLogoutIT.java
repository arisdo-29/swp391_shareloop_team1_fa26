package com.shareloop.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.integration.mail.EmailSender;
import com.shareloop.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * FR-03 (số điện thoại khi đăng ký) và FR-05 (đăng xuất) qua API thật trên PostgreSQL (Testcontainers). Mọi dữ liệu
 * test dùng email có tiền tố {@code phone-logout-it-} nên dọn được bằng một câu lệnh.
 */
@AutoConfigureMockMvc
class AuthPhoneAndLogoutIT extends IntegrationTest {

    private static final String EMAIL_PREFIX = "phone-logout-it-";
    private static final String NEW_EMAIL = EMAIL_PREFIX + "new@shareloop.local";
    private static final String PASSWORD = "Dev@12345";
    private static final String TAKEN_PHONE = "0901666601";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtService jwtService;

    // Đăng ký thành công sẽ gửi OTP; thay bằng mock để test không gửi mail thật.
    @MockitoBean
    EmailSender emailSender;

    @AfterEach
    void cleanUp() {
        // Không @Transactional nên dữ liệu đã commit thật, phải tự dọn.
        jdbc.update("DELETE FROM users WHERE email LIKE ?", EMAIL_PREFIX + "%");
    }

    // ---------- FR-03: số điện thoại ----------

    // 0123: quá ngắn; 091234567a: có chữ; +11234567890: số nước ngoài; 0281234567: số cố định (đầu 02).
    @ParameterizedTest
    @ValueSource(strings = {"0123", "091234567a", "+11234567890", "0281234567"})
    void register_whenPhoneFormatInvalid_returns400ValidationFailedOnPhoneField(String invalidPhone) throws Exception {
        // given: chưa có tài khoản nào

        // when
        ResultActions result = postRegister(NEW_EMAIL, invalidPhone);

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.field").value("phone"));
        assertThat(countUsersByEmail(NEW_EMAIL)).isZero();
    }

    @Test
    void register_whenPhoneBelongsToActiveAccount_returns409PhoneAlreadyUsedAndCreatesNoUser() throws Exception {
        // given
        insertUser(EMAIL_PREFIX + "active@shareloop.local", TAKEN_PHONE, "ACTIVE", false);

        // when
        ResultActions result = postRegister(NEW_EMAIL, TAKEN_PHONE);

        // then
        result.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AUTH_PHONE_ALREADY_USED"));
        assertThat(countUsersByEmail(NEW_EMAIL)).isZero();
    }

    @Test
    void register_whenPhoneOnlyBelongsToPendingAccount_returns201() throws Exception {
        // given: tài khoản PENDING_VERIFICATION không giữ số (docs/api/auth.md)
        insertUser(EMAIL_PREFIX + "pending@shareloop.local", TAKEN_PHONE, "PENDING_VERIFICATION", false);

        // when
        ResultActions result = postRegister(NEW_EMAIL, TAKEN_PHONE);

        // then
        result.andExpect(status().isCreated());
        assertThat(countUsersByEmail(NEW_EMAIL)).isEqualTo(1);
    }

    @Test
    void register_whenPhoneOnlyBelongsToSoftDeletedAccount_returns201() throws Exception {
        // given: tài khoản ACTIVE nhưng đã xoá mềm thì không giữ số
        insertUser(EMAIL_PREFIX + "deleted@shareloop.local", TAKEN_PHONE, "ACTIVE", true);

        // when
        ResultActions result = postRegister(NEW_EMAIL, TAKEN_PHONE);

        // then
        result.andExpect(status().isCreated());
        assertThat(countUsersByEmail(NEW_EMAIL)).isEqualTo(1);
    }

    // ---------- FR-05: đăng xuất ----------

    @Test
    void logout_withToken_returns204() throws Exception {
        // given
        String token = issueTokenForNewActiveUser("0901666602");

        // when, then
        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    // docs/api/auth.md: logout không bắt buộc xác thực (/api/v1/auth/** công khai) nên không token vẫn 204.
    @Test
    void logout_withoutToken_returns204BecauseLogoutIsPublic() throws Exception {
        // given: không gửi token

        // when, then
        mockMvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent());
    }

    // JWT không lưu trạng thái ở server: logout không thu hồi token, token cũ vẫn dùng được đến khi hết hạn.
    @Test
    void logout_thenUseSameToken_tokenStillWorksUntilExpiry() throws Exception {
        // given
        String token = issueTokenForNewActiveUser("0901666603");
        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // when, then
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private ResultActions postRegister(String email, String phone) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\",\"fullName\":\"Người Dùng IT\",\"phone\":\"" + phone + "\"}";
        return mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private void insertUser(String email, String phone, String status, boolean deleted) {
        jdbc.update(
                "INSERT INTO users (email, password_hash, full_name, phone, status, is_deleted)"
                        + " VALUES (?, 'not-a-real-hash', 'Người Dùng IT', ?, ?, ?)",
                email,
                phone,
                status,
                deleted);
    }

    private String issueTokenForNewActiveUser(String phone) {
        String email = EMAIL_PREFIX + phone + "@shareloop.local";
        insertUser(email, phone, "ACTIVE", false);
        Long userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        return jwtService.issue(userId, false);
    }

    private int countUsersByEmail(String email) {
        return jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, email);
    }
}
