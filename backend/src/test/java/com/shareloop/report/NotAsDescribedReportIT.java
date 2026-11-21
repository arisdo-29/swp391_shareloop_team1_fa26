package com.shareloop.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.request.dto.RefuseHandoverRequest;
import com.shareloop.request.dto.ScheduleRequest;
import com.shareloop.support.IntegrationTest;
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
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

/**
 * Chạy thật luồng "Từ chối nhận" trên PostgreSQL (GN-07, BC-09): giao dịch huỷ ngay, món bị từ chối tạm ẩn, món của
 * người từ chối mở lại và module report mở báo cáo vi phạm nhắm vào chủ món bị từ chối. Admin chỉ kết luận ai có lỗi,
 * giao dịch luôn ở CANCELLED.
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

    private final List<Long> requestIds = new ArrayList<>();
    private final List<Long> itemIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long requestId : requestIds) {
            jdbc.update("DELETE FROM reports WHERE request_id = ?", requestId);
            // Tin nhắn hệ thống trong cuộc trò chuyện (nếu có) phải xoá trước vì khoá ngoại.
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
    void refuseHandover_whenGiveReceiverRefuses_cancelsHidesMainItemAndReportsDonor() throws Exception {
        // given
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long itemId = insertItem(donorId, "GIVE");
        long requestId = createLogisticsConfirmedRequest(itemId, null, donorId, receiverId);

        // when
        refuse(requestId, receiverId).andExpect(jsonPath("$.status").value("CANCELLED"));

        // then
        assertThat(statusOf(requestId)).isEqualTo("CANCELLED");
        assertThat(itemActiveOf(itemId)).isFalse();
        List<Map<String, Object>> reports = reportsOf(requestId);
        assertThat(reports).hasSize(1);
        Map<String, Object> report = reports.get(0);
        assertThat(report.get("target_type")).isEqualTo("ITEM");
        assertThat(report.get("item_id")).isEqualTo(itemId);
        assertThat(report.get("reason")).isEqualTo("NOT_AS_DESCRIBED");
        assertThat(report.get("reporter_id")).isEqualTo(receiverId);
        assertThat(report.get("target_user_id")).isEqualTo(donorId);
        assertThat(report.get("description")).isEqualTo(REASON);
        assertThat(report.get("status")).isEqualTo("OPEN");
        assertThat(report.get("response_due_at")).isNull();
    }

    @Test
    void refuseHandover_whenDonorConfirmedFirst_stillCancels() throws Exception {
        // given: chủ bài đã bấm hoàn tất, người nhận vẫn từ chối được vì chính họ chưa bấm
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long requestId = createLogisticsConfirmedRequest(insertItem(donorId, "GIVE"), null, donorId, receiverId);
        mockMvc.perform(post("/api/v1/requests/{id}/confirm-handover", requestId)
                        .header("Authorization", token(donorId)))
                .andExpect(status().isOk());

        // when
        refuse(requestId, receiverId).andExpect(jsonPath("$.status").value("CANCELLED"));

        // then
        assertThat(reportsOf(requestId)).hasSize(1);
    }

    @Test
    void refuseHandover_whenSwapDonorRefuses_reopensMainItemAndHidesOfferedItem() throws Exception {
        // given
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long mainItemId = insertItem(donorId, "SWAP");
        long offeredItemId = insertItem(receiverId, "GIVE");
        long requestId = createLogisticsConfirmedRequest(mainItemId, offeredItemId, donorId, receiverId);

        // when
        refuse(requestId, donorId).andExpect(jsonPath("$.status").value("CANCELLED"));

        // then: báo cáo nhắm vào người xin là chủ món đổi
        assertThat(itemStatusOf(mainItemId)).isEqualTo("APPROVED");
        assertThat(itemActiveOf(mainItemId)).isTrue();
        assertThat(itemActiveOf(offeredItemId)).isFalse();
        Map<String, Object> report = reportsOf(requestId).get(0);
        assertThat(report.get("item_id")).isEqualTo(offeredItemId);
        assertThat(report.get("target_user_id")).isEqualTo(receiverId);
    }

    @Test
    void refuseHandover_whenSwapReceiverRefuses_hidesMainItemAndReopensOfferedItem() throws Exception {
        // given
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long mainItemId = insertItem(donorId, "SWAP");
        long offeredItemId = insertItem(receiverId, "GIVE");
        long requestId = createLogisticsConfirmedRequest(mainItemId, offeredItemId, donorId, receiverId);

        // when
        refuse(requestId, receiverId).andExpect(jsonPath("$.status").value("CANCELLED"));

        // then
        assertThat(itemActiveOf(mainItemId)).isFalse();
        assertThat(itemStatusOf(offeredItemId)).isEqualTo("APPROVED");
        assertThat(itemActiveOf(offeredItemId)).isTrue();
        assertThat(reportsOf(requestId).get(0).get("target_user_id")).isEqualTo(donorId);
    }

    @Test
    void refuseHandover_whenGiveDonorRefuses_returns403AndOpensNoReport() throws Exception {
        // given
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long requestId = createLogisticsConfirmedRequest(insertItem(donorId, "GIVE"), null, donorId, receiverId);

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
    void getDetail_afterRefusal_returnsRefusalReportByRoleAndNoDispute() throws Exception {
        // given
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long requestId = createLogisticsConfirmedRequest(insertItem(donorId, "GIVE"), null, donorId, receiverId);
        refuse(requestId, receiverId)
                .andExpect(jsonPath("$.disputes.length()").value(0))
                .andExpect(jsonPath("$.refusalReport.openedByMe").value(true))
                .andExpect(jsonPath("$.refusalReport.canRespond").value(false));

        // when, then: bên bị báo cáo được gửi giải trình, không có hạn
        mockMvc.perform(get("/api/v1/requests/{id}", requestId).header("Authorization", token(donorId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disputes.length()").value(0))
                .andExpect(jsonPath("$.refusalReport.reason").value("NOT_AS_DESCRIBED"))
                .andExpect(jsonPath("$.refusalReport.openedByMe").value(false))
                .andExpect(jsonPath("$.refusalReport.canRespond").value(true))
                .andExpect(jsonPath("$.refusalReport.responseDueAt").isEmpty());
    }

    @Test
    void respondDispute_onRefusalReport_savesOptionalResponse() throws Exception {
        // given
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long requestId = createLogisticsConfirmedRequest(insertItem(donorId, "GIVE"), null, donorId, receiverId);
        refuse(requestId, receiverId);
        long reportId = reportIdOf(requestId);

        // when
        mockMvc.perform(post("/api/v1/disputes/{id}/response", reportId)
                        .header("Authorization", token(donorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Máy bật được, tôi có video lúc giao\"}"))
                .andExpect(status().is2xxSuccessful());

        // then
        assertThat(jdbc.queryForObject("SELECT response FROM reports WHERE id = ?", String.class, reportId))
                .isEqualTo("Máy bật được, tôi có video lúc giao");
    }

    @Test
    void conclude_whenAdminUpholds_deductsStarFromOwnerKeepsItemHiddenAndStaysCancelled() throws Exception {
        // given: Admin kết luận ngay, không phải chờ giải trình
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long adminId = insertUser("Admin");
        long itemId = insertItem(donorId, "GIVE");
        long requestId = createLogisticsConfirmedRequest(itemId, null, donorId, receiverId);
        refuse(requestId, receiverId);

        // when
        conclude(reportIdOf(requestId), adminId, "UPHOLD");

        // then
        assertThat(trustStarsOf(donorId)).isEqualTo(4);
        assertThat(trustStarsOf(receiverId)).isEqualTo(5);
        assertThat(itemActiveOf(itemId)).isFalse();
        assertThat(itemStatusOf(itemId)).isEqualTo("APPROVED");
        assertThat(statusOf(requestId)).isEqualTo("CANCELLED");
        assertThat(totalPointsOf(donorId)).isZero();
        assertThat(totalPointsOf(receiverId)).isZero();
    }

    @Test
    void conclude_whenAdminDismisses_reopensItemPenalizesReporterAndStaysCancelled() throws Exception {
        // given
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long adminId = insertUser("Admin");
        long itemId = insertItem(donorId, "GIVE");
        long requestId = createLogisticsConfirmedRequest(itemId, null, donorId, receiverId);
        refuse(requestId, receiverId);

        // when
        conclude(reportIdOf(requestId), adminId, "DISMISS");

        // then
        assertThat(itemActiveOf(itemId)).isTrue();
        assertThat(trustStarsOf(receiverId)).isEqualTo(4);
        assertThat(trustStarsOf(donorId)).isEqualTo(5);
        assertThat(statusOf(requestId)).isEqualTo("CANCELLED");
        assertThat(totalPointsOf(donorId)).isZero();
    }

    @Test
    void conclude_whenSwapDonorRefusalUpheld_deductsStarFromReceiverAndKeepsOfferedItemHidden() throws Exception {
        // given: giao dịch Trao đổi, chủ bài từ chối món đem đổi nên báo cáo nhắm vào người xin
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long adminId = insertUser("Admin");
        long mainItemId = insertItem(donorId, "SWAP");
        long offeredItemId = insertItem(receiverId, "GIVE");
        long requestId = createLogisticsConfirmedRequest(mainItemId, offeredItemId, donorId, receiverId);
        refuse(requestId, donorId);

        // when
        conclude(reportIdOf(requestId), adminId, "UPHOLD");

        // then: giao dịch vẫn huỷ, bài chính đang hiện, món đổi của người xin (bên vi phạm) vẫn ẩn
        assertThat(statusOf(requestId)).isEqualTo("CANCELLED");
        assertThat(itemActiveOf(mainItemId)).isTrue();
        assertThat(itemActiveOf(offeredItemId)).isFalse();
        assertThat(trustStarsOf(receiverId)).isEqualTo(4);
        assertThat(trustStarsOf(donorId)).isEqualTo(5);
    }

    // ---------- Helper ----------

    private ResultActions refuse(long requestId, long userId) throws Exception {
        return mockMvc.perform(post("/api/v1/requests/{id}/refuse-handover", requestId)
                        .header("Authorization", token(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefuseHandoverRequest(REASON, null))))
                .andExpect(status().isOk());
    }

    private void conclude(long reportId, long adminId, String decision) throws Exception {
        mockMvc.perform(post("/api/v1/admin/reports/{reportId}/conclude", reportId)
                        .header("Authorization", "Bearer " + jwtService.issue(adminId, true))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"" + decision + "\"}"))
                .andExpect(status().isOk());
    }

    /** Đi qua API: gửi yêu cầu (kèm món đổi nếu có), chọn người nhận, chốt lịch, xác nhận hậu cần. */
    private long createLogisticsConfirmedRequest(long itemId, Long offeredItemId, long donorId, long receiverId)
            throws Exception {
        var createCall = post("/api/v1/items/{itemId}/requests", itemId).header("Authorization", token(receiverId));
        if (offeredItemId != null) {
            createCall.contentType(MediaType.APPLICATION_JSON).content("{\"offeredItemId\":" + offeredItemId + "}");
        }
        MvcResult created =
                mockMvc.perform(createCall).andExpect(status().isCreated()).andReturn();
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
                "SELECT reporter_id, target_type, item_id, target_user_id, reason, description, status,"
                        + " response_due_at FROM reports WHERE request_id = ?",
                requestId);
    }

    private long reportIdOf(long requestId) {
        return jdbc.queryForObject("SELECT id FROM reports WHERE request_id = ?", Long.class, requestId);
    }

    private String statusOf(long requestId) {
        return jdbc.queryForObject("SELECT status FROM requests WHERE id = ?", String.class, requestId);
    }

    private String itemStatusOf(long itemId) {
        return jdbc.queryForObject("SELECT status FROM items WHERE id = ?", String.class, itemId);
    }

    private boolean itemActiveOf(long itemId) {
        return jdbc.queryForObject("SELECT is_active FROM items WHERE id = ?", Boolean.class, itemId);
    }

    private int trustStarsOf(long userId) {
        return jdbc.queryForObject("SELECT trust_stars FROM users WHERE id = ?", Integer.class, userId);
    }

    private int totalPointsOf(long userId) {
        return jdbc.queryForObject("SELECT total_points FROM users WHERE id = ?", Integer.class, userId);
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

    private long insertItem(long donorId, String offerType) {
        long categoryId = jdbc.queryForObject("SELECT id FROM item_categories ORDER BY id LIMIT 1", Long.class);
        long areaId = jdbc.queryForObject("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1", Long.class);
        // Bài Trao đổi bắt buộc có món mong muốn
        String desiredItem = "SWAP".equals(offerType) ? "Muốn đổi lấy đồ dùng học tập" : null;
        Long itemId = jdbc.queryForObject(
                "INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, desired_item,"
                        + " condition, status, fee_state, pending_fee, pending_fee_type)"
                        + " VALUES (?, ?, ?, ?, 'Máy cũ', 'Mô tả', ?, 'GOOD', 'APPROVED', 'CHARGED', 0, NULL)"
                        + " RETURNING id",
                Long.class,
                donorId,
                categoryId,
                areaId,
                offerType,
                desiredItem);
        itemIds.add(itemId);
        return itemId;
    }
}
