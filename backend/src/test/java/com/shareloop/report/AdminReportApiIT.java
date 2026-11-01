package com.shareloop.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.shareloop.auth.service.JwtService;
import com.shareloop.integration.storage.StorageService;
import com.shareloop.report.repository.ReportRepository;
import com.shareloop.support.IntegrationTest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {"app.storage.type=local", "app.storage.local-dir=target/issue-118-test-media"})
class AdminReportApiIT extends IntegrationTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtService jwt;

    @Autowired
    ReportRepository reports;

    @Autowired
    StorageService storage;

    final Map<Long, String> mediaKeys = new HashMap<>();
    static final byte[] IMAGE = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

    @AfterEach
    void cleanStorage() throws Exception {
        for (String key : mediaKeys.values()) storage.delete(key);
    }

    @Test
    void existingDisputeEndpointsKeepPrivateEvidenceAndReturnRespondedReportToOpen() throws Exception {
        long donor = user("Dispute donor");
        long receiver = user("Dispute receiver");
        long category = jdbc.queryForObject(
                "INSERT INTO item_categories (name) VALUES ('Dispute merge IT') RETURNING id", Long.class);
        long area = jdbc.queryForObject(
                "INSERT INTO areas (name, level) VALUES ('Dispute merge IT', 1) RETURNING id", Long.class);
        long item = jdbc.queryForObject("""
                INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, condition, status)
                VALUES (?, ?, ?, 'GIVE', 'Dispute merge IT', 'Dispute merge IT', 'GOOD', 'TRADED') RETURNING id
                """, Long.class, donor, category, area);
        long requestId = jdbc.queryForObject("""
                INSERT INTO requests (item_id, receiver_id, type, status, completed_at)
                VALUES (?, ?, 'GIVE', 'COMPLETED', now()) RETURNING id
                """, Long.class, item, receiver);
        long senderMedia = media(receiver);
        long responseMedia = media(donor);
        String opened = mvc.perform(post("/api/v1/requests/" + requestId + "/disputes")
                        .header("Authorization", "Bearer " + jwt.issue(receiver, false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"NOT_AS_DESCRIBED","note":"Nội dung khiếu nại","evidenceMediaIds":[%d]}
                                """.formatted(senderMedia)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.responseDueAt").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long reportId = JsonMapper.builder().build().readTree(opened).get("id").asLong();
        mvc.perform(post("/api/v1/disputes/" + reportId + "/response")
                        .header("Authorization", "Bearer " + jwt.issue(donor, false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"note":"Nội dung giải trình","evidenceMediaIds":[%d]}
                                """.formatted(responseMedia)))
                .andExpect(status().isOk());
        reports.flush();
        for (long id : new long[] {senderMedia, responseMedia}) {
            mvc.perform(get("/api/v1/media/" + id)).andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/admin/reports/" + reportId + "/evidence/" + id)
                            .header("Authorization", "Bearer " + jwt.issue(donor, true)))
                    .andExpect(status().isOk())
                    .andExpect(content().bytes(IMAGE));
        }
        mvc.perform(get("/api/v1/admin/reports/" + reportId)
                        .header("Authorization", "Bearer " + jwt.issue(donor, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.report.status").value("OPEN"))
                .andExpect(jsonPath("$.report.stage").value("AFTER"))
                .andExpect(jsonPath("$.response").value("Nội dung giải trình"))
                .andExpect(jsonPath("$.responseEvidence[0].id").value(responseMedia));
        mvc.perform(post("/api/v1/disputes/" + reportId + "/response")
                        .header("Authorization", "Bearer " + jwt.issue(donor, false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Gửi lại\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DISPUTE_ALREADY_RESPONDED"));
    }

    @Test
    void concludeIsBlockedWhileResponseWindowIsOpen() throws Exception {
        long reporter = user("Window reporter");
        long target = user("Window target");
        long admin = user("Window admin");
        String adminAuth = "Bearer " + jwt.issue(admin, true);
        String created = mvc.perform(post("/api/v1/reports")
                        .header("Authorization", "Bearer " + jwt.issue(reporter, false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetType":"USER","targetUserId":%d,"reason":"OTHER","description":"Chờ giải trình"}
                                """.formatted(target)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long reportId = JsonMapper.builder().build().readTree(created).get("id").asLong();
        var report = reports.findById(reportId).orElseThrow();
        report.awaitResponse(java.time.Instant.now().plusSeconds(3600));
        reports.flush();

        // D-15, BC-10: còn hạn giải trình thì trang chi tiết báo khoá và kết luận bị chặn
        mvc.perform(get("/api/v1/admin/reports/" + reportId).header("Authorization", adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseWindowOpen").value(true));
        mvc.perform(post("/api/v1/admin/reports/" + reportId + "/conclude")
                        .header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"UPHOLD\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_RESPONSE_WINDOW_OPEN"))
                .andExpect(jsonPath("$.details.responseDueAt").isNotEmpty());
        assertThat(jdbc.queryForObject("SELECT trust_stars FROM users WHERE id = ?", Integer.class, target))
                .isEqualTo(5);

        // Giải trình xong thì kết luận được ngay, không cần chờ hết hạn
        mvc.perform(post("/api/v1/reports/" + reportId + "/response")
                        .header("Authorization", "Bearer " + jwt.issue(target, false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\":\"Giải trình\",\"evidenceMediaIds\":[]}"))
                .andExpect(status().isNoContent());
        reports.flush();
        mvc.perform(get("/api/v1/admin/reports/" + reportId).header("Authorization", adminAuth))
                .andExpect(jsonPath("$.responseWindowOpen").value(false));
        mvc.perform(post("/api/v1/admin/reports/" + reportId + "/conclude")
                        .header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"DISMISS\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void bothPartiesEvidenceIsPrivateAndAdminCanConcludeOnlyOnce() throws Exception {
        long reporter = user("Reporter");
        long target = user("Target");
        long admin = user("Admin");
        long secondAdmin = user("Second admin");
        long reporterMedia = media(reporter);
        long targetMedia = media(target);
        String adminAuth = "Bearer " + jwt.issue(admin, true);
        String secondAdminAuth = "Bearer " + jwt.issue(secondAdmin, true);
        String created = mvc.perform(post("/api/v1/reports")
                        .header("Authorization", "Bearer " + jwt.issue(reporter, false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetType":"USER","targetUserId":%d,"reason":"HARASSMENT",
                                 "description":"Nội dung khiếu nại","evidenceMediaIds":[%d]}
                                """.formatted(target, reporterMedia)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long reportId = JsonMapper.builder().build().readTree(created).get("id").asLong();
        var report = reports.findById(reportId).orElseThrow();
        report.awaitResponse(java.time.Instant.now().plusSeconds(3600));
        reports.flush();

        mvc.perform(post("/api/v1/reports/" + reportId + "/response")
                        .header("Authorization", "Bearer " + jwt.issue(target, false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"response":"Giải trình hai bên","evidenceMediaIds":[%d]}
                                """.formatted(targetMedia)))
                .andExpect(status().isNoContent());
        reports.flush();

        mvc.perform(get("/api/v1/admin/reports/" + reportId).header("Authorization", adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reporter.fullName").value("Reporter"))
                .andExpect(jsonPath("$.reportedUser.fullName").value("Target"))
                .andExpect(jsonPath("$.response").value("Giải trình hai bên"))
                .andExpect(jsonPath("$.responseDueAt").isNotEmpty())
                .andExpect(jsonPath("$.evidence[0].id").value(reporterMedia))
                .andExpect(jsonPath("$.responseEvidence[0].id").value(targetMedia))
                .andExpect(jsonPath("$.reporter.email").doesNotExist());
        String imagePath = "/api/v1/admin/reports/" + reportId + "/evidence/" + reporterMedia;
        mvc.perform(get(imagePath)).andExpect(status().isUnauthorized());
        mvc.perform(get(imagePath).header("Authorization", "Bearer " + jwt.issue(reporter, false)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/media/" + reporterMedia)).andExpect(status().isNotFound());
        mvc.perform(get(imagePath).header("Authorization", adminAuth))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().bytes(IMAGE));
        storage.delete(mediaKeys.get(reporterMedia));
        mvc.perform(get(imagePath).header("Authorization", adminAuth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEDIA_FILE_MISSING"));
        mvc.perform(get("/api/v1/admin/reports/" + reportId).header("Authorization", adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidence[0].id").value(reporterMedia));
        mvc.perform(get("/api/v1/admin/reports/" + reportId + "/evidence/99999999")
                        .header("Authorization", adminAuth))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/admin/reports/" + reportId + "/conclude")
                        .header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"DISMISS\",\"note\":\"Không có cơ sở\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.starsDeducted").value(0));
        assertThat(jdbc.queryForObject("SELECT trust_stars FROM users WHERE id = ?", Integer.class, target))
                .isEqualTo(5);
        mvc.perform(post("/api/v1/admin/reports/" + reportId + "/conclude")
                        .header("Authorization", secondAdminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"UPHOLD\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_ALREADY_CONCLUDED"));

        String secondCreated = mvc.perform(post("/api/v1/reports")
                        .header("Authorization", secondAdminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetType":"USER","targetUserId":%d,"reason":"OTHER","description":"Hồ sơ không ảnh"}
                                """.formatted(target)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long secondReportId =
                JsonMapper.builder().build().readTree(secondCreated).get("id").asLong();
        mvc.perform(get("/api/v1/admin/reports/" + secondReportId).header("Authorization", adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidence").isEmpty())
                .andExpect(jsonPath("$.responseEvidence").isEmpty());
        mvc.perform(post("/api/v1/admin/reports/" + secondReportId + "/conclude")
                        .header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"UPHOLD\",\"note\":\"Có cơ sở\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.starsDeducted").value(1));
        // Trừ sao đi qua JPA trong cùng transaction của test: flush trước để JDBC đọc thấy
        reports.flush();
        assertThat(jdbc.queryForObject("SELECT trust_stars FROM users WHERE id = ?", Integer.class, target))
                .isEqualTo(4);
    }

    @Test
    void itemReportFlow_requestResponseThenDismissAllReopensHiddenItem() throws Exception {
        long donor = user("Item donor");
        long item = approvedItem(donor);
        String adminAuth = "Bearer " + jwt.issue(user("Item admin"), true);
        // 3 báo cáo đang chờ: chạm ngưỡng report_hide_threshold nên bài tự ẩn
        long first = reportItem(user("Reporter 1"), item);
        long second = reportItem(user("Reporter 2"), item);
        long third = reportItem(user("Reporter 3"), item);
        assertThat(itemActive(item)).isFalse();

        // BC-03: Admin yêu cầu giải trình một lần, cả 3 báo cáo của bài cùng chờ giải trình với chung một hạn
        requestResponseAndRespond(first, donor, adminAuth, second, third);
        mvc.perform(post("/api/v1/admin/reports/" + first + "/request-response").header("Authorization", adminAuth))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_RESPONSE_REQUEST_NOT_ALLOWED"));

        // BC-04: bác bỏ lần lượt, chỉ mở lại bài khi không còn báo cáo nào chờ
        dismiss(first, adminAuth);
        dismiss(second, adminAuth);
        assertThat(itemActive(item)).isFalse();
        dismiss(third, adminAuth);
        assertThat(itemActive(item)).isTrue();
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = 'ITEM_REOPENED'",
                        Integer.class,
                        donor))
                .isEqualTo(1);
    }

    @Test
    void itemReportFlow_upholdClosesOtherPendingReportsAndDeductsOneStar() throws Exception {
        long donor = user("Upheld donor");
        long item = approvedItem(donor);
        String adminAuth = "Bearer " + jwt.issue(user("Upheld admin"), true);
        long first = reportItem(user("Reporter A"), item);
        long second = reportItem(user("Reporter B"), item);
        long third = reportItem(user("Reporter C"), item);
        // Xong khi (L1): yêu cầu giải trình, chủ bài giải trình một lần, Admin chấp thuận một lần
        requestResponseAndRespond(first, donor, adminAuth, second, third);

        mvc.perform(post("/api/v1/admin/reports/" + first + "/conclude")
                        .header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"UPHOLD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.starsDeducted").value(1));
        reports.flush();

        // BC-05: hai báo cáo còn lại đóng theo, không trừ thêm sao; bài vẫn ẩn
        for (long other : new long[] {second, third}) {
            var row = jdbc.queryForMap("SELECT status, stars_deducted, resolution FROM reports WHERE id = ?", other);
            assertThat(row)
                    .containsEntry("status", "RESOLVED")
                    .containsEntry("resolution", "Đóng theo kết luận báo cáo #" + first);
            // Driver PostgreSQL trả SMALLINT thành Integer, so theo giá trị số để không lệch kiểu Short/Integer
            assertThat(((Number) row.get("stars_deducted")).intValue()).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT trust_stars FROM users WHERE id = ?", Integer.class, donor))
                .isEqualTo(4);
        assertThat(itemActive(item)).isFalse();
    }

    private long approvedItem(long donor) {
        return jdbc.queryForObject("""
                INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, condition, status)
                VALUES (?, (SELECT id FROM item_categories ORDER BY id LIMIT 1),
                        (SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1),
                        'GIVE', 'Item report IT', 'Mô tả', 'GOOD', 'APPROVED') RETURNING id
                """, Long.class, donor);
    }

    private long reportItem(long reporter, long item) throws Exception {
        String created = mvc.perform(post("/api/v1/reports")
                        .header("Authorization", "Bearer " + jwt.issue(reporter, false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetType":"ITEM","itemId":%d,"reason":"OTHER","description":"Ảnh lấy trên mạng"}
                                """.formatted(item)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        reports.flush();
        return JsonMapper.builder().build().readTree(created).get("id").asLong();
    }

    // Admin yêu cầu giải trình qua một báo cáo; mọi báo cáo của bài cùng chờ, chung hạn, chủ bài nhận một thông báo.
    // Chủ bài giải trình một lần qua báo cáo đó thì cả bài có giải trình và trở về OPEN.
    private void requestResponseAndRespond(long reportId, long donor, String adminAuth, long... siblings)
            throws Exception {
        mvc.perform(post("/api/v1/admin/reports/" + reportId + "/request-response")
                        .header("Authorization", adminAuth))
                .andExpect(status().isNoContent());
        reports.flush();
        java.sql.Timestamp dueAt = jdbc.queryForObject(
                "SELECT response_due_at FROM reports WHERE id = ?", java.sql.Timestamp.class, reportId);
        for (long sibling : siblings) {
            assertThat(jdbc.queryForMap("SELECT status, response_due_at FROM reports WHERE id = ?", sibling))
                    .containsEntry("status", "AWAITING_RESPONSE")
                    .containsEntry("response_due_at", dueAt);
        }
        assertThat(jdbc.queryForObject(
                        "SELECT link FROM notifications WHERE user_id = ? AND type = 'REPORT_RESPONSE_REQUESTED'",
                        String.class,
                        donor))
                .isEqualTo("/me/reports/" + reportId);

        mvc.perform(post("/api/v1/reports/" + reportId + "/response")
                        .header("Authorization", "Bearer " + jwt.issue(donor, false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\":\"Ảnh tự chụp\",\"evidenceMediaIds\":[]}"))
                .andExpect(status().isNoContent());
        reports.flush();
        for (long sibling : siblings) {
            assertThat(jdbc.queryForMap("SELECT status, response FROM reports WHERE id = ?", sibling))
                    .containsEntry("status", "OPEN")
                    .containsEntry("response", "Ảnh tự chụp");
        }
    }

    private void dismiss(long reportId, String adminAuth) throws Exception {
        mvc.perform(post("/api/v1/admin/reports/" + reportId + "/conclude")
                        .header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"DISMISS\"}"))
                .andExpect(status().isOk());
        reports.flush();
    }

    private boolean itemActive(long item) {
        return jdbc.queryForObject("SELECT is_active FROM items WHERE id = ?", Boolean.class, item);
    }

    private long user(String name) {
        return jdbc.queryForObject(
                """
                INSERT INTO users (email, password_hash, full_name, phone, status)
                VALUES (?, 'unused-test-hash', ?, ?, 'ACTIVE') RETURNING id
                """,
                Long.class,
                UUID.randomUUID() + "@example.test",
                name,
                UUID.randomUUID().toString().substring(0, 12));
    }

    private long media(long owner) throws Exception {
        String key = storage.save(IMAGE, "png");
        long id = jdbc.queryForObject("""
                INSERT INTO media_files (storage_key, mime_type, size_bytes, created_by)
                VALUES (?, 'image/png', ?, ?) RETURNING id
                """, Long.class, key, IMAGE.length, owner);
        mediaKeys.put(id, key);
        return id;
    }
}
