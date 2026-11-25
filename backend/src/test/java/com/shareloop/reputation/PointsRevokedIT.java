package com.shareloop.reputation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.reputation.service.ReputationService;
import com.shareloop.support.IntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** F3: khiếu nại giao dịch đã hoàn tất được chấp thuận thì trừ lại điểm người vi phạm đã nhận từ giao dịch đó. */
@AutoConfigureMockMvc
class PointsRevokedIT extends IntegrationTest {

    private static final int PHONE_BASE = ThreadLocalRandom.current().nextInt(10_000_000, 90_000_000);
    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();

    @Autowired
    ReputationService reputationService;

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwt;

    @Autowired
    JdbcTemplate jdbc;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> itemIds = new ArrayList<>();
    private final List<Long> requestIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long requestId : requestIds) {
            jdbc.update("DELETE FROM activity_logs WHERE target_type = 'REQUEST' AND target_id = ?", requestId);
            jdbc.update("DELETE FROM reports WHERE request_id = ?", requestId);
            jdbc.update("DELETE FROM messages WHERE request_id = ?", requestId);
        }
        for (Long userId : userIds) {
            jdbc.update(
                    "DELETE FROM activity_logs WHERE actor_id = ? OR (target_type = 'USER' AND target_id = ?)",
                    userId,
                    userId);
        }
        for (Long requestId : requestIds) {
            jdbc.update("DELETE FROM requests WHERE id = ?", requestId);
        }
        for (Long itemId : itemIds) {
            jdbc.update("DELETE FROM items WHERE id = ?", itemId);
        }
        for (Long userId : userIds) {
            jdbc.update("DELETE FROM notifications WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
        requestIds.clear();
        itemIds.clear();
        userIds.clear();
    }

    @Test
    void revokePointsForRequest_takesBackOnlyViolatorsPointsAndIsIdempotent() {
        // given: giao dịch hoàn tất, cả hai bên đã nhận điểm
        long donorId = insertUser(false);
        long receiverId = insertUser(false);
        long requestId = insertCompletedRequest(donorId, receiverId);
        reputationService.onCompleted(requestId);
        int donorBefore = totalPointsOf(donorId);
        int receiverBefore = totalPointsOf(receiverId);
        assertThat(donorBefore).isPositive();

        // when: thu hồi điểm của chủ bài (người vi phạm), gọi hai lần
        int first = reputationService.revokePointsForRequest(donorId, requestId, "Vi phạm");
        int second = reputationService.revokePointsForRequest(donorId, requestId, "Vi phạm");

        // then: trừ đúng số đã nhận một lần, người bị hại giữ điểm, điểm không âm
        assertThat(first).isEqualTo(donorBefore);
        assertThat(second).isZero();
        assertThat(totalPointsOf(donorId)).isEqualTo(0);
        assertThat(totalPointsOf(receiverId)).isEqualTo(receiverBefore);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM activity_logs WHERE type = 'POINTS_REVOKED' AND target_id = ?",
                        Integer.class,
                        requestId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM notifications WHERE user_id = ? AND type = 'POINTS_REVOKED'",
                        Integer.class,
                        donorId))
                .isEqualTo(1);
    }

    @Test
    void conclude_whenDisputeOfCompletedRequestUpheld_revokesViolatorPoints() throws Exception {
        // given: giao dịch hoàn tất đã cộng điểm, người nhận khiếu nại, chủ bài giải trình
        long donorId = insertUser(false);
        long receiverId = insertUser(false);
        long adminId = insertUser(true);
        long requestId = insertCompletedRequest(donorId, receiverId);
        reputationService.onCompleted(requestId);
        int donorBefore = totalPointsOf(donorId);
        int receiverBefore = totalPointsOf(receiverId);
        String opened = mvc.perform(post("/api/v1/requests/" + requestId + "/disputes")
                        .header("Authorization", "Bearer " + jwt.issue(receiverId, false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"NOT_AS_DESCRIBED\",\"note\":\"Món không đúng mô tả\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long reportId = JsonMapper.builder().build().readTree(opened).get("id").asLong();
        mvc.perform(post("/api/v1/disputes/" + reportId + "/response")
                        .header("Authorization", "Bearer " + jwt.issue(donorId, false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Giải trình\"}"))
                .andExpect(status().isOk());

        // when: Admin chấp thuận khiếu nại
        mvc.perform(post("/api/v1/admin/reports/" + reportId + "/conclude")
                        .header("Authorization", "Bearer " + jwt.issue(adminId, true))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"UPHOLD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        // then: người bị khiếu nại mất điểm của giao dịch này, người khiếu nại giữ điểm
        assertThat(totalPointsOf(donorId)).isEqualTo(0);
        assertThat(totalPointsOf(receiverId)).isEqualTo(receiverBefore);
    }

    private int totalPointsOf(long userId) {
        return jdbc.queryForObject("SELECT total_points FROM users WHERE id = ?", Integer.class, userId);
    }

    private long insertUser(boolean admin) {
        Long id = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name, phone, status, is_admin)"
                        + " VALUES (?, 'not-a-real-hash', 'Revoke IT', ?, 'ACTIVE', ?) RETURNING id",
                Long.class,
                "points-revoked-it-" + UUID.randomUUID() + "@test.local",
                "03" + (PHONE_BASE + PHONE_SEQUENCE.incrementAndGet()),
                admin);
        userIds.add(id);
        return id;
    }

    private long insertCompletedRequest(long donorId, long receiverId) {
        Long categoryId =
                jdbc.queryForObject("SELECT id FROM item_categories WHERE is_active ORDER BY id LIMIT 1", Long.class);
        Long areaId = jdbc.queryForObject("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1", Long.class);
        Long itemId = jdbc.queryForObject(
                "INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, condition, status)"
                        + " VALUES (?, ?, ?, 'GIVE', 'Revoke IT', 'Mô tả', 'GOOD', 'TRADED') RETURNING id",
                Long.class,
                donorId,
                categoryId,
                areaId);
        itemIds.add(itemId);
        Long requestId = jdbc.queryForObject(
                "INSERT INTO requests (item_id, receiver_id, type, status, completed_at)"
                        + " VALUES (?, ?, 'GIVE', 'COMPLETED', ?) RETURNING id",
                Long.class,
                itemId,
                receiverId,
                Timestamp.from(Instant.now()));
        requestIds.add(requestId);
        return requestId;
    }
}
