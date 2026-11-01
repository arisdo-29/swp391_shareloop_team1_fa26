package com.shareloop.reputation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cấm tự động khi hết sao (D-09, UT-03) trên PostgreSQL thật: Admin chấp thuận báo cáo qua API, kiểm trạng thái tài
 * khoản, đăng nhập, token cũ và giao dịch đang mở. @Transactional: mỗi test rollback, không cần tự dọn.
 */
@AutoConfigureMockMvc
@Transactional
class TrustStarsBanIT extends IntegrationTest {

    private static final String PASSWORD = "Dev@12345";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtService jwt;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("Còn 1 sao bị chấp thuận báo cáo: BANNED, không đăng nhập được, token cũ 403, giao dịch mở bị huỷ")
    void upholdReport_whenOneStarLeft_bansAccountAndCancelsOpenRequests() throws Exception {
        // given: người bị báo cáo còn 1 sao, đã đăng nhập từ trước
        String email = UUID.randomUUID() + "@example.test";
        long target = user(email, "Người còn 1 sao", 1);
        String oldToken = login(email);
        long donor = user(UUID.randomUUID() + "@example.test", "Người cho", 5);
        long reporter = user(UUID.randomUUID() + "@example.test", "Người báo cáo", 5);
        long admin = user(UUID.randomUUID() + "@example.test", "Admin", 5);
        // và đang nhận một món (giao dịch mở) cùng một giao dịch đang tranh chấp
        long reservedItem = item(donor, "RESERVED");
        long openRequest = request(reservedItem, target, "RESERVED");
        long disputedItem = item(donor, "RESERVED");
        long disputedRequest = request(disputedItem, target, "DISPUTED");

        // when: Admin chấp thuận báo cáo
        concludeUphold(reportAbout(target, reporter), admin);

        // then: hết sao thì bị cấm, status đổi chứ không chỉ khoá
        assertThat(jdbc.queryForObject("SELECT trust_stars FROM users WHERE id = ?", Integer.class, target))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, target))
                .isEqualTo("BANNED");
        // giao dịch mở bị huỷ và bài được nhả; giao dịch tranh chấp giữ nguyên để Admin kết luận
        assertThat(requestStatus(openRequest)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT status FROM items WHERE id = ?", String.class, reservedItem))
                .isEqualTo("APPROVED");
        assertThat(requestStatus(disputedRequest)).isEqualTo("DISPUTED");
        // nhật ký USER_BAN do hệ thống ghi và thông báo không còn nhắc sao ân hạn
        assertThat(jdbc.queryForObject(
                        "SELECT data::text FROM activity_logs WHERE type = 'USER_BAN' AND target_id = ?"
                                + " AND actor_id IS NULL",
                        String.class,
                        target))
                .contains("\"cancelledRequests\": 1");
        assertThat(jdbc.queryForObject(
                        "SELECT body FROM notifications WHERE user_id = ? AND type = 'TRUST_STARS_DEPLETED'",
                        String.class,
                        target))
                .contains("bị cấm")
                .doesNotContain("ân hạn");

        // đăng nhập lại bị chặn bằng mã riêng
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_BANNED"));
        // token cấp trước khi bị cấm cũng bị chặn
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_BANNED"));
    }

    @Test
    @DisplayName("Còn 3 sao bị chấp thuận báo cáo: báo còn 2 sao trước khi bị cấm, tài khoản vẫn hoạt động")
    void upholdReport_whenStarsRemain_notifiesRemainingStarsAndKeepsAccountActive() throws Exception {
        // given
        String email = UUID.randomUUID() + "@example.test";
        long target = user(email, "Người còn 3 sao", 3);
        long reporter = user(UUID.randomUUID() + "@example.test", "Người báo cáo", 5);
        long admin = user(UUID.randomUUID() + "@example.test", "Admin", 5);

        // when
        concludeUphold(reportAbout(target, reporter), admin);

        // then
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, target))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject(
                        "SELECT body FROM notifications WHERE user_id = ? AND type = 'TRUST_STARS_DEDUCTED'",
                        String.class,
                        target))
                .isEqualTo("Bạn bị trừ 1 sao. Bạn còn 2 sao trước khi tài khoản bị cấm.");
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk());
    }

    // ---------- Tiện ích hỗ trợ test ----------

    private long user(String email, String name, int trustStars) {
        return jdbc.queryForObject(
                """
                INSERT INTO users (email, password_hash, full_name, phone, status, trust_stars)
                VALUES (?, ?, ?, ?, 'ACTIVE', ?) RETURNING id
                """,
                Long.class,
                email,
                passwordEncoder.encode(PASSWORD),
                name,
                UUID.randomUUID().toString().substring(0, 12),
                trustStars);
    }

    private long item(long donorId, String status) {
        return jdbc.queryForObject("""
                INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, condition, status)
                VALUES (?, (SELECT id FROM item_categories ORDER BY id LIMIT 1),
                        (SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1),
                        'GIVE', 'Ban IT item', 'Mô tả', 'NEW', ?) RETURNING id
                """, Long.class, donorId, status);
    }

    private long request(long itemId, long receiverId, String status) {
        return jdbc.queryForObject(
                "INSERT INTO requests (item_id, receiver_id, type, status, reserved_at)"
                        + " VALUES (?, ?, 'GIVE', ?, now()) RETURNING id",
                Long.class,
                itemId,
                receiverId,
                status);
    }

    private String requestStatus(long requestId) {
        return jdbc.queryForObject("SELECT status FROM requests WHERE id = ?", String.class, requestId);
    }

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonMapper.builder().build().readTree(body).get("accessToken").asString();
    }

    private long reportAbout(long target, long reporter) throws Exception {
        String created = mvc.perform(post("/api/v1/reports")
                        .header("Authorization", "Bearer " + jwt.issue(reporter, false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetType":"USER","targetUserId":%d,"reason":"OTHER","description":"Lừa đảo"}
                                """.formatted(target)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonMapper.builder().build().readTree(created).get("id").asLong();
    }

    private void concludeUphold(long reportId, long admin) throws Exception {
        mvc.perform(post("/api/v1/admin/reports/" + reportId + "/conclude")
                        .header("Authorization", "Bearer " + jwt.issue(admin, true))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"UPHOLD\",\"note\":\"Có cơ sở\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.starsDeducted").value(1));
    }
}
