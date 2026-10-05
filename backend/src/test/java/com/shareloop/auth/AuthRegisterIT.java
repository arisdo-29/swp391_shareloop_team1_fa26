package com.shareloop.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.integration.mail.EmailSender;
import com.shareloop.support.IntegrationTest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
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
import tools.jackson.databind.ObjectMapper;

/** Integration test đăng ký + OTP: DB PostgreSQL thật (Testcontainers), email được thay bằng mock. */
@AutoConfigureMockMvc
class AuthRegisterIT extends IntegrationTest {

    private static final String EMAIL = "register-it@shareloop.local";
    private static final String PASSWORD = "Dev@12345";
    private static final String PHONE = "0901888888";
    private static final long MAIL_TIMEOUT_MILLIS = 5000;
    private static final Pattern SIX_DIGITS = Pattern.compile("\\d{6}");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    ObjectMapper objectMapper;

    // Thay SmtpEmailSender bằng mock: test không gửi mail thật và đọc được mã OTP.
    @MockitoBean
    EmailSender emailSender;

    @AfterEach
    void cleanUp() {
        // Không @Transactional nên dữ liệu đã commit thật, phải tự dọn.
        jdbc.update("DELETE FROM users WHERE email = ?", EMAIL);
    }

    @Test
    void register_thenVerifyOtp_returnsTokenThatCanCallMe() throws Exception {
        // given
        postRegister()
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.otpExpiresInSeconds").value(300))
                .andExpect(jsonPath("$.resendCooldownSeconds").value(60));
        String otp = captureLatestOtp(1);

        // when
        String response = mockMvc.perform(post("/api/v1/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"otp\":\"" + otp + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value(EMAIL))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String accessToken = objectMapper.readTree(response).get("accessToken").asString();

        // then
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    void register_whenEmailAlreadyActive_returns409EmailAlreadyRegistered() throws Exception {
        // given
        jdbc.update(
                "INSERT INTO users (email, password_hash, full_name, phone, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                EMAIL,
                passwordEncoder.encode(PASSWORD),
                "Người Dùng IT",
                "0901777777");

        // when, then
        postRegister()
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void register_whenEmailStillPending_returns201AndSendsNewOtp() throws Exception {
        // given: lần đăng ký đầu tạo tài khoản PENDING
        postRegister().andExpect(status().isCreated());
        // Lùi hạn mã 2 phút để coi như lần gửi trước đã quá cooldown 60 giây (nếu không sẽ bị 429).
        jdbc.update("UPDATE users SET otp_expires_at = otp_expires_at - interval '2 minutes' WHERE email = ?", EMAIL);

        // when
        postRegister().andExpect(status().isCreated());

        // then
        captureLatestOtp(2);
    }

    private ResultActions postRegister() throws Exception {
        String body = "{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD
                + "\",\"fullName\":\"Người Dùng IT\",\"phone\":\"" + PHONE + "\"}";
        return mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    // Email gửi bất đồng bộ sau commit nên phải chờ (timeout) rồi mới đọc được nội dung thư.
    private String captureLatestOtp(int expectedMailCount) {
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailSender, timeout(MAIL_TIMEOUT_MILLIS).times(expectedMailCount))
                .send(eq(EMAIL), anyString(), bodyCaptor.capture());
        String lastBody = bodyCaptor.getAllValues().get(expectedMailCount - 1);
        Matcher matcher = SIX_DIGITS.matcher(lastBody);
        assertThat(matcher.find()).isTrue();
        return matcher.group();
    }
}
