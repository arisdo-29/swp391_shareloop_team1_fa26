package com.shareloop.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.support.IntegrationTest;
import com.shareloop.user.service.UserService;
import java.util.Map;
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

/**
 * Integration test khoá / mở khoá tài khoản (FR-107): DB PostgreSQL thật (Testcontainers), gọi API thật qua MockMvc
 * để kiểm cả đăng nhập lẫn token đã cấp trước khi khoá (LockedAccountFilter).
 */
@AutoConfigureMockMvc
class UserLockApiIT extends IntegrationTest {

    private static final String EMAIL = "lock-it@shareloop.local";
    private static final String PASSWORD = "Dev@12345";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserService userService;

    long userId;

    @BeforeEach
    void insertUser() {
        // Không @Transactional: dữ liệu commit thật, nên tự dọn trong @AfterEach.
        jdbc.update(
                "INSERT INTO users (email, password_hash, full_name, phone, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                EMAIL,
                passwordEncoder.encode(PASSWORD),
                "Người Bị Khoá",
                "0901888888");
        userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, EMAIL);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM users WHERE email = ?", EMAIL);
    }

    @Test
    void lock_thenOldTokenCallsMe_returns403AccountLockedAndKeepsStatus() throws Exception {
        // given: đăng nhập lấy token khi tài khoản còn hoạt động
        String accessToken = loginAndGetToken();

        // when
        userService.lock(userId, "Spam nhiều lần");

        // then: token cũ còn hạn nhưng bị chặn; DB chỉ đổi is_active, status giữ nguyên
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_LOCKED"));
        Map<String, Object> row = jdbc.queryForMap("SELECT is_active, status FROM users WHERE id = ?", userId);
        assertThat(row.get("is_active")).isEqualTo(false);
        assertThat(row.get("status")).isEqualTo("ACTIVE");
    }

    @Test
    void login_whenAccountLocked_returns403AccountLocked() throws Exception {
        // given
        userService.lock(userId, null);

        // when, then
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_LOCKED"));
    }

    @Test
    void unlock_afterLock_canLoginAndCallMeAgain() throws Exception {
        // given
        userService.lock(userId, null);

        // when
        userService.unlock(userId);

        // then
        String accessToken = loginAndGetToken();
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    private String loginAndGetToken() throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asString();
    }

    private String loginBody() {
        return "{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}";
    }
}
