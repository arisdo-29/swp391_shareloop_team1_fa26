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
        assertThat(jdbc.queryForObject("SELECT trust_stars FROM users WHERE id = ?", Integer.class, target))
                .isEqualTo(4);
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
