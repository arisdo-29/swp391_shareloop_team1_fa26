package com.shareloop.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.request.dto.HandoverConfirmRequest;
import com.shareloop.request.dto.ScheduleRequest;
import com.shareloop.request.service.RequestJobService;
import com.shareloop.support.IntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/**
 * Chạy thật luồng xác nhận giao nhận trên PostgreSQL: bên nhận chọn "Không đúng mô tả" thì module report tự mở report
 * sau khi giao dịch commit (FR-85, BR-R05), và job tự hoàn tất bỏ qua giao dịch đó (FR-87, BR-T06).
 * Không dùng @Transactional ở lớp test vì listener AFTER_COMMIT chỉ chạy khi giao dịch thật sự commit.
 */
@AutoConfigureMockMvc
class NotAsDescribedReportIT extends IntegrationTest {
    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final String REASON = "Máy không bật được như mô tả";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    RequestJobService requestJobService;

    private final List<Long> requestIds = new ArrayList<>();
    private final List<Long> itemIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long requestId : requestIds) {
            jdbc.update("DELETE FROM reports WHERE request_id = ?", requestId);
            jdbc.update("DELETE FROM requests WHERE id = ?", requestId);
        }
        for (Long itemId : itemIds) {
            jdbc.update("DELETE FROM items WHERE id = ?", itemId);
        }
        for (Long userId : userIds) {
            jdbc.update("DELETE FROM notifications WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM activity_logs WHERE actor_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
        requestIds.clear();
        itemIds.clear();
        userIds.clear();
    }

    @Test
    void confirmHandover_whenReceiverFirstSaysNotAsDescribed_opensExactlyOneReport() throws Exception {
        // given
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long requestId = createLogisticsConfirmedRequest(donorId, receiverId);

        // when
        confirmAsReceiver(requestId, receiverId, false, REASON)
                .andExpect(jsonPath("$.status").value("AWAITING_HANDOVER"));

        // then
        List<Map<String, Object>> reports = reportsOf(requestId);
        assertThat(reports).hasSize(1);
        Map<String, Object> report = reports.get(0);
        assertThat(report.get("reason")).isEqualTo("NOT_AS_DESCRIBED");
        assertThat(report.get("target_type")).isEqualTo("REQUEST");
        assertThat(report.get("description")).isEqualTo(REASON);
        assertThat(report.get("reporter_id")).isEqualTo(receiverId);
        assertThat(report.get("target_user_id")).isEqualTo(donorId);
        assertThat(report.get("status")).isEqualTo("AWAITING_RESPONSE");
    }

    @Test
    void confirmHandover_whenDonorConfirmedFirstThenReceiverSaysNotAsDescribed_completesAndOpensReport()
            throws Exception {
        // given
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long requestId = createLogisticsConfirmedRequest(donorId, receiverId);
        mockMvc.perform(post("/api/v1/requests/{id}/confirm-handover", requestId)
                        .header("Authorization", token(donorId)))
                .andExpect(status().isOk());

        // when
        confirmAsReceiver(requestId, receiverId, false, REASON)
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // then
        assertThat(reportsOf(requestId)).hasSize(1);
    }

    @Test
    void confirmHandover_whenReceiverSaysAsDescribed_opensNoReport() throws Exception {
        // given
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long requestId = createLogisticsConfirmedRequest(donorId, receiverId);

        // when
        confirmAsReceiver(requestId, receiverId, true, null)
                .andExpect(jsonPath("$.status").value("AWAITING_HANDOVER"));

        // then
        assertThat(reportsOf(requestId)).isEmpty();
    }

    @Test
    void autoConfirmHandoverRequests_whenReceiverReportedNotAsDescribed_doesNotComplete() throws Exception {
        // given: bên nhận báo không đúng mô tả cách đây 5 ngày, bên cho vẫn im lặng
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long requestId = createLogisticsConfirmedRequest(donorId, receiverId);
        confirmAsReceiver(requestId, receiverId, false, REASON);
        jdbc.update(
                "UPDATE requests SET receiver_handover_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(5, ChronoUnit.DAYS)),
                requestId);

        // when
        requestJobService.autoConfirmHandoverRequests(Instant.now());

        // then
        assertThat(statusOf(requestId)).isEqualTo("AWAITING_HANDOVER");
    }

    // ---------- Helper ----------

    private org.springframework.test.web.servlet.ResultActions confirmAsReceiver(
            long requestId, long receiverId, boolean asDescribed, String reason) throws Exception {
        return mockMvc.perform(post("/api/v1/requests/{id}/confirm-handover", requestId)
                        .header("Authorization", token(receiverId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new HandoverConfirmRequest(asDescribed, reason))))
                .andExpect(status().isOk());
    }

    /** Đi qua API: gửi yêu cầu, chọn người nhận, chốt lịch, xác nhận hậu cần để tới LOGISTICS_CONFIRMED. */
    private long createLogisticsConfirmedRequest(long donorId, long receiverId) throws Exception {
        long itemId = insertItem(donorId);
        MvcResult created = mockMvc.perform(
                        post("/api/v1/items/{itemId}/requests", itemId).header("Authorization", token(receiverId)))
                .andExpect(status().isCreated())
                .andReturn();
        long requestId = objectMapper
                .readTree(created.getResponse().getContentAsString())
                .get("id")
                .asLong();
        requestIds.add(requestId);

        mockMvc.perform(post("/api/v1/requests/{id}/select", requestId).header("Authorization", token(donorId)))
                .andExpect(status().isOk());
        Instant meetingTime = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        mockMvc.perform(post("/api/v1/requests/{id}/schedule", requestId)
                        .header("Authorization", token(donorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ScheduleRequest("IN_PERSON", "FPT Park", meetingTime, false))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/requests/{id}/confirm-logistics", requestId)
                        .header("Authorization", token(receiverId)))
                .andExpect(status().isOk());
        return requestId;
    }

    private List<Map<String, Object>> reportsOf(long requestId) {
        return jdbc.queryForList(
                "SELECT reporter_id, target_type, target_user_id, reason, description, status "
                        + "FROM reports WHERE request_id = ?",
                requestId);
    }

    private String statusOf(long requestId) {
        return jdbc.queryForObject("SELECT status FROM requests WHERE id = ?", String.class, requestId);
    }

    private String token(long userId) {
        return "Bearer " + jwtService.issue(userId, false);
    }

    private long insertUser(String name) {
        int seq = SEQ.incrementAndGet();
        Long userId = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name, phone, status, credit_balance, held_credit)"
                        + " VALUES (?, 'hash', ?, ?, 'ACTIVE', 20, 0) RETURNING id",
                Long.class,
                "not-as-described-" + seq + "-" + System.nanoTime() + "@test.local",
                name,
                String.format("0997%06d", seq));
        userIds.add(userId);
        return userId;
    }

    private long insertItem(long donorId) {
        long categoryId = jdbc.queryForObject("SELECT id FROM item_categories ORDER BY id LIMIT 1", Long.class);
        long areaId = jdbc.queryForObject("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1", Long.class);
        Long itemId = jdbc.queryForObject(
                "INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, condition, status,"
                        + " fee_state, pending_fee, pending_fee_type)"
                        + " VALUES (?, ?, ?, 'GIVE', 'Máy cũ', 'Mô tả', 'GOOD', 'APPROVED', 'CHARGED', 0, NULL)"
                        + " RETURNING id",
                Long.class,
                donorId,
                categoryId,
                areaId);
        itemIds.add(itemId);
        return itemId;
    }
}
