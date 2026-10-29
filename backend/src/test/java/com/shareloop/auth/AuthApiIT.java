package com.shareloop.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/** Integration test đăng nhập: DB PostgreSQL thật (Testcontainers), gọi API thật qua MockMvc. */
@AutoConfigureMockMvc
class AuthApiIT extends IntegrationTest {

    private static final String EMAIL = "login-it@shareloop.local";
    private static final String PASSWORD = "Dev@12345";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void insertUser() {
        // Không @Transactional: dữ liệu commit thật, nên tự dọn trong @AfterEach.
        jdbc.update(
                "INSERT INTO users (email, password_hash, full_name, phone, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                EMAIL,
                passwordEncoder.encode(PASSWORD),
                "Người Dùng IT",
                "0901999999");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM users WHERE email = ?", EMAIL);
    }

    @Test
    void login_withCorrectPassword_returnsTokenThatCanCallMe() throws Exception {
        // given
        String body = loginBody(PASSWORD);

        // when
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(86400))
                .andExpect(jsonPath("$.user.email").value(EMAIL))
                .andExpect(jsonPath("$.user.isAdmin").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String accessToken = objectMapper.readTree(response).get("accessToken").asString();

        // then
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.fullName").value("Người Dùng IT"));
    }

    @Test
    void login_withWrongPassword_returns401InvalidCredentials() throws Exception {
        // when, then
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("Sai@12345")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
    }

    @Test
    void me_withoutToken_returns401() throws Exception {
        // when, then
        mockMvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_withCorrectCurrentPassword_onlyNewPasswordCanLogin() throws Exception {
        // given
        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(PASSWORD)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String accessToken =
                objectMapper.readTree(loginResponse).get("accessToken").asString();
        String newPassword = "MatKhauMoi@123";
        String body = "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + newPassword + "\"}";

        // when
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());

        // then: hash mới là BCrypt thật, mật khẩu cũ không còn đăng nhập được
        String storedHash = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class, EMAIL);
        assertThat(storedHash).startsWith("$2");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(newPassword)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    private String loginBody(String password) {
        return "{\"email\":\"" + EMAIL + "\",\"password\":\"" + password + "\"}";
    }
}
