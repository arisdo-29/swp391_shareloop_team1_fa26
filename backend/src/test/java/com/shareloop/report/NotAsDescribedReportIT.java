package com.shareloop.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.request.dto.RefuseHandoverRequest;
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
 * Chạy thật luồng "Từ chối giao nhận" trên PostgreSQL: bên nhận món đồ từ chối thì giao dịch sang DISPUTED và module
 * report mở report NOT_AS_DESCRIBED trong cùng transaction (GN-07), job tự hoàn tất bỏ qua giao dịch đó (GN-09).
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
            // Mở khiếu nại sinh tin nhắn hệ thống trong cuộc trò chuyện, phải xoá trước vì khoá ngoại.
            jdbc.update("DELETE FROM messages WHERE request_id = ?", requestId);
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
    void refuseHandover_whenReceiverRefusesFirst_opensExactlyOneReportAgainstDonor() throws Exception {
        // given
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long requestId = createLogisticsConfirmedRequest(donorId, receiverId);

        // when
        refuseAsReceiver(requestId, receiverId).andExpect(jsonPath("$.status").value("DISPUTED"));

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
    void refuseHandover_whenDonorConfirmedFirst_transitionsToDisputedAndOpensReport() throws Exception {
        // given
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long requestId = createLogisticsConfirmedRequest(donorId, receiverId);
        mockMvc.perform(post("/api/v1/requests/{id}/confirm-handover", requestId)
                        .header("Authorization", token(donorId)))
                .andExpect(status().isOk());

        // when
        refuseAsReceiver(requestId, receiverId).andExpect(jsonPath("$.status").value("DISPUTED"));

        // then
        assertThat(reportsOf(requestId)).hasSize(1);
    }

    @Test
    void confirmHandover_whenReceiverConfirms_opensNoReport() throws Exception {
        // given
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long requestId = createLogisticsConfirmedRequest(donorId, receiverId);

        // when
        mockMvc.perform(post("/api/v1/requests/{id}/confirm-handover", requestId)
                        .header("Authorization", token(receiverId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_HANDOVER"));

        // then
        assertThat(reportsOf(requestId)).isEmpty();
    }

    @Test
    void refuseHandover_whenGiveDonorRefuses_returns403AndOpensNoReport() throws Exception {
        // given
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long requestId = createLogisticsConfirmedRequest(donorId, receiverId);

        // when
        mockMvc.perform(post("/api/v1/requests/{id}/refuse-handover", requestId)
                        .header("Authorization", token(donorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefuseHandoverRequest(REASON, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REQUEST_REFUSE_NOT_ALLOWED"));

        // then
        assertThat(reportsOf(requestId)).isEmpty();
        assertThat(statusOf(requestId)).isEqualTo("LOGISTICS_CONFIRMED");
    }

    @Test
    void getDetail_afterRefusal_returnsDisputesByRole() throws Exception {
        // given
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long requestId = createLogisticsConfirmedRequest(donorId, receiverId);
        refuseAsReceiver(requestId, receiverId)
                .andExpect(jsonPath("$.disputes[0].openedByMe").value(true))
                .andExpect(jsonPath("$.disputes[0].canRespond").value(false));

        // when, then: bên bị khiếu nại thấy được giải trình, còn hạn
        mockMvc.perform(get("/api/v1/requests/{id}", requestId).header("Authorization", token(donorId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disputes.length()").value(1))
                .andExpect(jsonPath("$.disputes[0].reason").value("NOT_AS_DESCRIBED"))
                .andExpect(jsonPath("$.disputes[0].openedByMe").value(false))
                .andExpect(jsonPath("$.disputes[0].canRespond").value(true))
                .andExpect(jsonPath("$.disputes[0].responseDueAt").isNotEmpty());
    }

    @Test
    void autoConfirmHandoverRequests_whenDisputedByRefusal_doesNotComplete() throws Exception {
        // given: chủ bài đã bấm hoàn tất 5 ngày trước, người nhận từ chối giao nhận
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long requestId = createLogisticsConfirmedRequest(donorId, receiverId);
        mockMvc.perform(post("/api/v1/requests/{id}/confirm-handover", requestId)
                        .header("Authorization", token(donorId)))
                .andExpect(status().isOk());
        refuseAsReceiver(requestId, receiverId);
        jdbc.update(
                "UPDATE requests SET donor_handover_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(5, ChronoUnit.DAYS)),
                requestId);

        // when
        requestJobService.autoConfirmHandoverRequests(Instant.now());

        // then
        assertThat(statusOf(requestId)).isEqualTo("DISPUTED");
    }

    // ---------- Helper ----------

    private org.springframework.test.web.servlet.ResultActions refuseAsReceiver(long requestId, long receiverId)
            throws Exception {
        return mockMvc.perform(post("/api/v1/requests/{id}/refuse-handover", requestId)
                        .header("Authorization", token(receiverId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefuseHandoverRequest(REASON, null))))
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
                                new ScheduleRequest("IN_PERSON", "FPT Park", meetingTime, true))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/requests/{id}/confirm-logistics", requestId)
                        .header("Authorization", token(receiverId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreeToWaiver\":true}"))
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
