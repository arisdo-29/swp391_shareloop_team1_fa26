package com.shareloop.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.integration.mail.EmailSender;
import com.shareloop.support.IntegrationTest;
import com.shareloop.user.entity.EmailCanonical;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Kiểm trùng email theo dạng chuẩn hoá (A3): DB thật, trigger điền email_canonical. */
@AutoConfigureMockMvc
class AuthEmailCanonicalIT extends IntegrationTest {

    private static final String GMAIL = "canonical.it@gmail.com";
    private static final String PASSWORD = "Dev@12345";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockitoBean
    EmailSender emailSender;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM users WHERE email LIKE '%canonical%it%' OR email LIKE 'canonical%'");
    }

    private void insertActiveUser(String email, String phone) {
        jdbc.update(
                "INSERT INTO users (email, password_hash, full_name, phone, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                email,
                passwordEncoder.encode(PASSWORD),
                "Người Dùng IT",
                phone);
    }

    @Test
    void register_whenGmailPlusVariantOfActiveEmail_returnsAlreadyRegistered() throws Exception {
        // given
        insertActiveUser(GMAIL, "0901555001");

        // when, then
        register("canonical.it+1@gmail.com", "0901555002")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void register_whenGmailDotVariantOfActiveEmail_returnsAlreadyRegistered() throws Exception {
        // given
        insertActiveUser(GMAIL, "0901555001");

        // when, then
        register("c.anonical.it@gmail.com", "0901555002")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void register_whenOtherDomainWithDotAndPlus_keepsThemAsDifferentEmail() throws Exception {
        // given
        insertActiveUser("canonical.it@shareloop.local", "0901555001");

        // when, then
        register("canonical.it+1@shareloop.local", "0901555002").andExpect(status().isCreated());
    }

    @Test
    void login_withGmailVariant_findsTheAccount() throws Exception {
        // given
        insertActiveUser(GMAIL, "0901555001");

        // when, then
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"c.anonical.it+x@gmail.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void forgotPassword_withGmailVariant_sendsOtpToOriginalEmail() throws Exception {
        // given
        insertActiveUser(GMAIL, "0901555001");

        // when
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"c.anonical.it+x@gmail.com\"}"))
                .andExpect(status().isOk());

        // then: thư gửi tới email gốc đã lưu, không gửi tới biến thể người dùng gõ
        verify(emailSender, timeout(5000)).send(eq(GMAIL), anyString(), anyString());
    }

    @Test
    void emailCanonicalColumn_matchesJavaRuleForSampleEmails() {
        // given
        List<String> samples =
                List.of("canonical.a+1@gmail.com", "Canonical.B@GoogleMail.com", "canonical.c+1@shareloop.local");

        for (String sample : samples) {
            // when
            insertActiveUser(sample, "09015550" + Math.abs(sample.hashCode() % 90 + 10));
            String stored =
                    jdbc.queryForObject("SELECT email_canonical FROM users WHERE email = ?", String.class, sample);

            // then
            assertThat(stored).isEqualTo(EmailCanonical.of(sample));
        }
    }

    private ResultActions register(String email, String phone) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                        + "\",\"fullName\":\"Người Dùng IT\",\"phone\":\"" + phone + "\"}"));
    }
}
