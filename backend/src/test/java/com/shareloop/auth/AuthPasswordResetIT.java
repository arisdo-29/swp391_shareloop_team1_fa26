package com.shareloop.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.integration.mail.EmailSender;
import com.shareloop.support.IntegrationTest;
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
import org.springframework.test.web.servlet.ResultActions;

/** Integration test quên / đặt lại mật khẩu (FR-09): DB PostgreSQL thật (Testcontainers), email thay bằng mock. */
@AutoConfigureMockMvc
class AuthPasswordResetIT extends IntegrationTest {

    private static final String EMAIL = "reset-it@shareloop.local";
    private static final String UNKNOWN_EMAIL = "khong-co-tai-khoan@shareloop.local";
    private static final String OLD_PASSWORD = "Dev@12345";
    private static final String NEW_PASSWORD = "MatKhauMoi@123";
    private static final String RESET_SUBJECT = "Đặt lại mật khẩu ShareLoop";
    private static final long MAIL_TIMEOUT_MILLIS = 5000;
    private static final long NO_MAIL_WAIT_MILLIS = 1000;
    private static final int MAX_FAILED = 5;
    private static final Pattern SIX_DIGITS = Pattern.compile("\\d{6}");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    // Thay SmtpEmailSender bằng mock: test không gửi mail thật và đọc được mã OTP.
    @MockitoBean
    EmailSender emailSender;

    @BeforeEach
    void insertActiveUser() {
        // Không @Transactional: dữ liệu commit thật (listener gửi mail chạy sau commit), nên tự dọn trong @AfterEach.
        jdbc.update(
                "INSERT INTO users (email, password_hash, full_name, phone, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                EMAIL,
                passwordEncoder.encode(OLD_PASSWORD),
                "Người Dùng IT",
                "0901666666");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM users WHERE email = ?", EMAIL);
    }

    @Test
    void forgotPassword_existingAndUnknownEmail_returnSameResponseButOnlyExistingGetsMail() throws Exception {
        // when
        String existingBody = postForgotPassword(EMAIL)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String unknownBody = postForgotPassword(UNKNOWN_EMAIL)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // then: hai body chỉ khác ở email được trả lại
        assertThat(unknownBody.replace(UNKNOWN_EMAIL, EMAIL)).isEqualTo(existingBody);
        verify(emailSender, timeout(MAIL_TIMEOUT_MILLIS)).send(eq(EMAIL), eq(RESET_SUBJECT), anyString());
        verify(emailSender, never()).send(eq(UNKNOWN_EMAIL), anyString(), anyString());
    }

    @Test
    void forgotPassword_whenAccountLocked_returns200WithoutSendingMail() throws Exception {
        // given
        jdbc.update("UPDATE users SET is_active = false WHERE email = ?", EMAIL);

        // when
        postForgotPassword(EMAIL)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.otpExpiresInSeconds").value(300))
                .andExpect(jsonPath("$.resendCooldownSeconds").value(60));

        // then: chờ một lúc vì mail (nếu có) được gửi bất đồng bộ
        verify(emailSender, after(NO_MAIL_WAIT_MILLIS).never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void resetPassword_withMailedOtp_onlyNewPasswordCanLogin() throws Exception {
        // given
        String otp = requestResetOtp();

        // when
        postResetPassword(otp, NEW_PASSWORD).andExpect(status().isNoContent());

        // then: hash mới là BCrypt, OTP đã bị xoá, chỉ mật khẩu mới đăng nhập được
        String storedHash = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class, EMAIL);
        assertThat(storedHash).startsWith("$2");
        String storedOtpHash =
                jdbc.queryForObject("SELECT otp_code_hash FROM users WHERE email = ?", String.class, EMAIL);
        assertThat(storedOtpHash).isNull();
        postLogin(NEW_PASSWORD).andExpect(status().isOk());
        postLogin(OLD_PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void resetPassword_reusingUsedOtp_returns400OtpExpired() throws Exception {
        // given
        String otp = requestResetOtp();
        postResetPassword(otp, NEW_PASSWORD).andExpect(status().isNoContent());

        // when, then
        postResetPassword(otp, "MatKhauKhac@789")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_OTP_EXPIRED"));
    }

    @Test
    void resetPassword_withWrongOtp_returns400InvalidAndPersistsFailedCount() throws Exception {
        // given
        String otp = requestResetOtp();

        // when
        postResetPassword(wrongCode(otp), NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_OTP_INVALID"))
                .andExpect(jsonPath("$.details.remainingAttempts").value(MAX_FAILED - 1));

        // then: bộ đếm sai đã được lưu dù request ném lỗi (noRollbackFor), mật khẩu cũ vẫn đăng nhập được
        Integer failedCount =
                jdbc.queryForObject("SELECT otp_failed_count FROM users WHERE email = ?", Integer.class, EMAIL);
        assertThat(failedCount).isEqualTo(1);
        postLogin(OLD_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void resetPassword_afterMaxWrongAttempts_returns422AndCorrectOtpNoLongerWorks() throws Exception {
        // given: sai 4 lần đầu
        String otp = requestResetOtp();
        for (int attempt = 1; attempt < MAX_FAILED; attempt++) {
            postResetPassword(wrongCode(otp), NEW_PASSWORD).andExpect(status().isBadRequest());
        }

        // when: lần sai thứ 5
        postResetPassword(wrongCode(otp), NEW_PASSWORD)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("AUTH_OTP_TOO_MANY_ATTEMPTS"));

        // then: mã đã bị xoá, nhập đúng cũng không được nữa
        postResetPassword(otp, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_OTP_EXPIRED"));
    }

    @Test
    void resetPassword_whenOtpExpired_returns400OtpExpired() throws Exception {
        // given
        String otp = requestResetOtp();
        jdbc.update("UPDATE users SET otp_expires_at = now() - interval '1 second' WHERE email = ?", EMAIL);

        // when, then
        postResetPassword(otp, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_OTP_EXPIRED"));
    }

    @Test
    void resetPassword_whenNewPasswordTooShort_returns400ValidationFailed() throws Exception {
        // when, then
        postResetPassword("123456", "ngan")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.field").value("newPassword"));
    }

    @Test
    void resetPassword_whenNewPasswordSameAsOld_returns400SameAsCurrent() throws Exception {
        // given
        String otp = requestResetOtp();

        // when, then
        postResetPassword(otp, OLD_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_NEW_PASSWORD_SAME_AS_CURRENT"))
                .andExpect(jsonPath("$.field").value("newPassword"));
    }

    @Test
    void resetPassword_whenEmailUnknown_returns400OtpExpiredLikeAccountWithoutCode() throws Exception {
        // when, then: giống hệt tài khoản thật chưa xin mã, nên không dò được email
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetBody(UNKNOWN_EMAIL, "123456", NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_OTP_EXPIRED"));
        postResetPassword("123456", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_OTP_EXPIRED"));
    }

    private String requestResetOtp() throws Exception {
        postForgotPassword(EMAIL).andExpect(status().isOk());
        return captureResetOtp();
    }

    // Email gửi bất đồng bộ sau commit nên phải chờ (timeout) rồi mới đọc được nội dung thư.
    private String captureResetOtp() {
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailSender, timeout(MAIL_TIMEOUT_MILLIS)).send(eq(EMAIL), eq(RESET_SUBJECT), bodyCaptor.capture());
        Matcher matcher = SIX_DIGITS.matcher(bodyCaptor.getValue());
        assertThat(matcher.find()).isTrue();
        return matcher.group();
    }

    // Mã 6 chữ số chắc chắn khác mã thật.
    private String wrongCode(String correctCode) {
        return correctCode.equals("000000") ? "111111" : "000000";
    }

    private ResultActions postForgotPassword(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions postResetPassword(String otp, String newPassword) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(resetBody(EMAIL, otp, newPassword)));
    }

    private ResultActions postLogin(String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + password + "\"}"));
    }

    private String resetBody(String email, String otp, String newPassword) {
        return "{\"email\":\"" + email + "\",\"otp\":\"" + otp + "\",\"newPassword\":\"" + newPassword + "\"}";
    }
}
