package com.shareloop.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@AutoConfigureMockMvc
@Transactional
class SystemReportApiIT extends IntegrationTest {
    private static final String MY_REPORTS = "/api/v1/me/system-reports";
    private static final String ADMIN_REPORTS = "/api/v1/admin/system-reports";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtService jwt;

    @Test
    void sendsTracksAndResolvesSystemReportOnce() throws Exception {
        long reporter = createUser("Người gửi lỗi", false);
        long other = createUser("Người dùng khác", false);
        long admin = createUser("Người xử lý", true);
        String reporterAuth = "Bearer " + jwt.issue(reporter, false);
        String otherAuth = "Bearer " + jwt.issue(other, false);
        String adminAuth = "Bearer " + jwt.issue(admin, true);

        mvc.perform(get(MY_REPORTS)).andExpect(status().isUnauthorized());
        mvc.perform(get(ADMIN_REPORTS).header("Authorization", reporterAuth)).andExpect(status().isForbidden());
        mvc.perform(post(MY_REPORTS)
                        .header("Authorization", reporterAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"   ","description":"Không mở được hộp tin nhắn."}
                                """))
                .andExpect(status().isBadRequest());

        String submitted = mvc.perform(post(MY_REPORTS)
                        .header("Authorization", reporterAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":" Lỗi mở tin nhắn ","description":"Không mở được hộp tin nhắn."}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Lỗi mở tin nhắn"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long reportId =
                JsonMapper.builder().build().readTree(submitted).get("id").asLong();
        String myDetail = MY_REPORTS + "/" + reportId;
        String adminDetail = ADMIN_REPORTS + "/" + reportId;
        String resolution = """
                {"resolution":"Đã sửa lỗi mở tin nhắn."}
                """;

        mvc.perform(get(MY_REPORTS).header("Authorization", reporterAuth))
                .andExpect(jsonPath("$.items[0].id").value(reportId));
        mvc.perform(get(MY_REPORTS).header("Authorization", otherAuth))
                .andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get(myDetail).header("Authorization", otherAuth)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/disputes/" + reportId + "/response")
                        .header("Authorization", otherAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"note":"Nội dung giải trình"}
                                """))
                .andExpect(status().isNotFound());
        mvc.perform(get(ADMIN_REPORTS).param("status", "OPEN").header("Authorization", adminAuth))
                .andExpect(jsonPath("$.items[0].reporterName").value("Người gửi lỗi"));
        mvc.perform(get(adminDetail).header("Authorization", adminAuth))
                .andExpect(jsonPath("$.description").value("Không mở được hộp tin nhắn."));
        mvc.perform(get("/api/v1/admin/reports").header("Authorization", adminAuth))
                .andExpect(jsonPath("$.items[?(@.id == " + reportId + ")]").isEmpty());
        mvc.perform(get("/api/v1/admin/reports/" + reportId).header("Authorization", adminAuth))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/admin/reports/" + reportId + "/conclude")
                        .header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"UPHOLD"}
                                """))
                .andExpect(status().isNotFound());
        assertThat(notificationCount(admin, "SYSTEM_REPORT_CREATED", "/admin/system-reports/" + reportId))
                .isEqualTo(1);

        mvc.perform(post(adminDetail + "/resolve")
                        .header("Authorization", reporterAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resolution))
                .andExpect(status().isForbidden());
        mvc.perform(post(adminDetail + "/resolve")
                        .header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resolution))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty());
        mvc.perform(post(adminDetail + "/resolve")
                        .header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resolution))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SYSTEM_REPORT_ALREADY_RESOLVED"));
        mvc.perform(get(myDetail).header("Authorization", reporterAuth))
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolution").value("Đã sửa lỗi mở tin nhắn."));
        mvc.perform(get(ADMIN_REPORTS).param("status", "RESOLVED").header("Authorization", adminAuth))
                .andExpect(jsonPath("$.items[0].id").value(reportId));
        assertThat(notificationCount(reporter, "SYSTEM_REPORT_RESOLVED", "/me/system-reports/" + reportId))
                .isEqualTo(1);
        assertThat(notificationCount(other, "SYSTEM_REPORT_RESOLVED", "/me/system-reports/" + reportId))
                .isZero();
    }

    private long notificationCount(long userId, String type, String link) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE user_id = ? AND type = ? AND link = ?",
                Long.class,
                userId,
                type,
                link);
    }

    private long createUser(String name, boolean admin) {
        return jdbc.queryForObject(
                """
                INSERT INTO users (email, password_hash, full_name, phone, status, is_admin)
                VALUES (?, 'unused-test-hash', ?, ?, 'ACTIVE', ?) RETURNING id
                """,
                Long.class,
                UUID.randomUUID() + "@example.test",
                name,
                UUID.randomUUID().toString().substring(0, 12),
                admin);
    }
}
