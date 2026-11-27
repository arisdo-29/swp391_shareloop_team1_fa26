package com.shareloop.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.request.dto.ScheduleRequest;
import com.shareloop.support.IntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
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
 * F1: bài bị báo cáo vi phạm mà đang có giao dịch mở. Lý do nghiêm trọng thì giao dịch bị huỷ, lý do khác hoặc tự ẩn do đủ
 * ngưỡng thì giao dịch tiếp tục và hai bên được báo. Chạy thật trên PostgreSQL.
 */
@AutoConfigureMockMvc
class ReportOpenTransactionIT extends IntegrationTest {
    private static final AtomicInteger SEQ = new AtomicInteger();

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
        for (Long itemId : itemIds) {
            jdbc.update("DELETE FROM reports WHERE item_id = ?", itemId);
        }
        for (Long requestId : requestIds) {
            jdbc.update("DELETE FROM messages WHERE request_id = ?", requestId);
            jdbc.update("DELETE FROM requests WHERE id = ?", requestId);
        }
        for (Long itemId : itemIds) {
            jdbc.update("DELETE FROM items WHERE id = ?", itemId);
        }
        for (Long userId : userIds) {
            jdbc.update("DELETE FROM reports WHERE reporter_id = ? OR target_user_id = ?", userId, userId);
            jdbc.update("DELETE FROM notifications WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM activity_logs WHERE actor_id = ? OR target_id = ?", userId, userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
        requestIds.clear();
        itemIds.clear();
        userIds.clear();
    }

    @Test
    void conclude_whenSeriousReasonAndItemHasOpenTransaction_cancelsTransactionWithoutPoints() throws Exception {
        // given: hàng cấm, hai bên đã chốt lịch
        long donorId = insertUser("Donor", false);
        long receiverId = insertUser("Receiver", false);
        long reporterId = insertUser("Reporter", false);
        long adminId = insertUser("Admin", true);
        long itemId = insertItem(donorId);
        long requestId = createLogisticsConfirmedRequest(itemId, donorId, receiverId);
        long reportId = insertReport(reporterId, itemId, donorId, "PROHIBITED_ITEM");
        int donorPointsBefore = totalPointsOf(donorId);
        int receiverPointsBefore = totalPointsOf(receiverId);

        // when
        conclude(reportId, adminId);

        // then: giao dịch huỷ với lý do rõ ràng, hai bên được báo, người đăng không được cộng điểm
        assertThat(requestStatusOf(requestId)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT cancel_reason FROM requests WHERE id = ?", String.class, requestId))
                .contains("Món đồ vi phạm quy định nên giao dịch bị huỷ");
        assertThat(noticeCount(donorId, "TRANSACTION_CANCELLED")).isEqualTo(1);
        assertThat(noticeCount(receiverId, "TRANSACTION_CANCELLED")).isEqualTo(1);
        assertThat(totalPointsOf(donorId)).isEqualTo(donorPointsBefore);
        assertThat(totalPointsOf(receiverId)).isEqualTo(receiverPointsBefore);
        assertThat(itemActiveOf(itemId)).isFalse();
    }

    @Test
    void conclude_whenOtherReasonAndItemHasOpenTransaction_keepsTransactionAndNotifiesBoth() throws Exception {
        // given
        long donorId = insertUser("Donor", false);
        long receiverId = insertUser("Receiver", false);
        long reporterId = insertUser("Reporter", false);
        long adminId = insertUser("Admin", true);
        long itemId = insertItem(donorId);
        long requestId = createLogisticsConfirmedRequest(itemId, donorId, receiverId);
        long reportId = insertReport(reporterId, itemId, donorId, "OTHER");

        // when
        conclude(reportId, adminId);

        // then: giao dịch vẫn chạy, hai bên được báo, trang tiến trình có cờ cảnh báo
        assertThat(requestStatusOf(requestId)).isEqualTo("LOGISTICS_CONFIRMED");
        assertThat(noticeCount(donorId, "TRANSACTION_ITEM_UNDER_REVIEW")).isEqualTo(1);
        assertThat(noticeCount(receiverId, "TRANSACTION_ITEM_UNDER_REVIEW")).isEqualTo(1);
        mockMvc.perform(get("/api/v1/requests/{id}", requestId).header("Authorization", token(receiverId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flags[0]").value("ITEM_UNDER_REVIEW"));
    }

    @Test
    void create_whenReportsReachThresholdOnItemWithOpenTransaction_keepsTransactionAndNotifiesBothOnce()
            throws Exception {
        // given: bài có giao dịch đang mở, ba người báo cáo đủ tin cậy
        long donorId = insertUser("Donor", false);
        long receiverId = insertUser("Receiver", false);
        long itemId = insertItem(donorId);
        long requestId = createLogisticsConfirmedRequest(itemId, donorId, receiverId);

        // when
        for (int i = 0; i < 3; i++) {
            long reporterId = insertUser("Reporter" + i, false);
            jdbc.update("UPDATE users SET created_at = now() - interval '30 days' WHERE id = ?", reporterId);
            mockMvc.perform(post("/api/v1/reports")
                            .header("Authorization", token(reporterId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"targetType\":\"ITEM\",\"itemId\":" + itemId
                                    + ",\"reason\":\"OTHER\",\"description\":\"Mô tả có vấn đề\"}"))
                    .andExpect(status().isCreated());
        }

        // then: bài tự ẩn, giao dịch giữ nguyên, mỗi bên chỉ nhận một thông báo
        assertThat(itemActiveOf(itemId)).isFalse();
        assertThat(requestStatusOf(requestId)).isEqualTo("LOGISTICS_CONFIRMED");
        assertThat(noticeCount(donorId, "TRANSACTION_ITEM_UNDER_REVIEW")).isEqualTo(1);
        assertThat(noticeCount(receiverId, "TRANSACTION_ITEM_UNDER_REVIEW")).isEqualTo(1);
    }

    @Test
    void conclude_whenUpheld_notifiesReporterWithAdminNote() throws Exception {
        // given
        long donorId = insertUser("Donor", false);
        long reporterId = insertUser("Reporter", false);
        long adminId = insertUser("Admin", true);
        long itemId = insertItem(donorId);
        long reportId = insertReport(reporterId, itemId, donorId, "OTHER");

        // when
        concludeWith(reportId, adminId, "UPHOLD", "Đã xác minh");

        // then
        assertThat(noticeCount(reporterId, "REPORT_CONCLUDED")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT body FROM notifications WHERE user_id = ? AND type = 'REPORT_CONCLUDED'",
                        String.class,
                        reporterId))
                .contains("chấp thuận")
                .contains("Đã xác minh");
        assertThat(noticeCount(donorId, "REPORT_CONCLUDED")).isZero();
    }

    @Test
    void conclude_whenDismissed_notifiesReporterAndReportedOwner() throws Exception {
        // given
        long donorId = insertUser("Donor", false);
        long reporterId = insertUser("Reporter", false);
        long adminId = insertUser("Admin", true);
        long itemId = insertItem(donorId);
        long reportId = insertReport(reporterId, itemId, donorId, "OTHER");

        // when
        concludeWith(reportId, adminId, "DISMISS", null);

        // then
        assertThat(noticeCount(reporterId, "REPORT_CONCLUDED")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT body FROM notifications WHERE user_id = ? AND type = 'REPORT_CONCLUDED'",
                        String.class,
                        donorId))
                .isEqualTo("Báo cáo về bài hoặc tài khoản của bạn đã được xem xét và không có vi phạm.");
    }

    @Test
    void create_whenReasonDoesNotMatchTargetType_returnsReasonNotAllowed() throws Exception {
        long donorId = insertUser("Donor", false);
        long reporterId = insertUser("Reporter", false);
        long itemId = insertItem(donorId);

        mockMvc.perform(post("/api/v1/reports")
                        .header("Authorization", token(reporterId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"ITEM\",\"itemId\":" + itemId
                                + ",\"reason\":\"NO_SHOW\",\"description\":\"Sai loại\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REPORT_REASON_NOT_ALLOWED"));
    }

    @Test
    void create_whenWrongContactInfoWithoutConfirmedSchedule_isRejectedAndAllowedAfterwards() throws Exception {
        long donorId = insertUser("Donor", false);
        long receiverId = insertUser("Receiver", false);
        String body = "{\"targetType\":\"USER\",\"targetUserId\":" + donorId
                + ",\"reason\":\"WRONG_CONTACT_INFO\",\"description\":\"Số sai\"}";

        mockMvc.perform(post("/api/v1/reports")
                        .header("Authorization", token(receiverId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REPORT_CONTACT_REASON_NOT_ALLOWED"));

        long itemId = insertItem(donorId);
        long requestId = createLogisticsConfirmedRequest(itemId, donorId, receiverId);
        assertThat(requestId).isPositive();
        mockMvc.perform(post("/api/v1/reports")
                        .header("Authorization", token(receiverId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void conclude_whenDismissedAsDeliberateFalse_deductsOneStarFromReporterOnlyWhenTicked() throws Exception {
        long donorId = insertUser("Donor", false);
        long reporterId = insertUser("Reporter", false);
        long otherReporterId = insertUser("Reporter2", false);
        long adminId = insertUser("Admin", true);
        long itemId = insertItem(donorId);
        long plainReport = insertReport(otherReporterId, itemId, donorId, "OTHER");
        long falseReport = insertReport(reporterId, itemId, donorId, "OTHER");
        int before = starsOf(reporterId);

        concludeRaw(plainReport, adminId, "{\"decision\":\"DISMISS\"}");
        assertThat(starsOf(otherReporterId)).isEqualTo(before);

        concludeRaw(falseReport, adminId, "{\"decision\":\"DISMISS\",\"deliberateFalseReport\":true}");
        assertThat(starsOf(reporterId)).isEqualTo(before - 1);
    }

    private int starsOf(long userId) {
        return jdbc.queryForObject("SELECT trust_stars FROM users WHERE id = ?", Integer.class, userId);
    }

    private void concludeRaw(long reportId, long adminId, String body) throws Exception {
        mockMvc.perform(post("/api/v1/admin/reports/{reportId}/conclude", reportId)
                        .header("Authorization", "Bearer " + jwtService.issue(adminId, true))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private void concludeWith(long reportId, long adminId, String decision, String note) throws Exception {
        String body = note == null
                ? "{\"decision\":\"" + decision + "\"}"
                : "{\"decision\":\"" + decision + "\",\"note\":\"" + note + "\"}";
        mockMvc.perform(post("/api/v1/admin/reports/{reportId}/conclude", reportId)
                        .header("Authorization", "Bearer " + jwtService.issue(adminId, true))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    // ---------- Helper ----------

    private void conclude(long reportId, long adminId) throws Exception {
        mockMvc.perform(post("/api/v1/admin/reports/{reportId}/conclude", reportId)
                        .header("Authorization", "Bearer " + jwtService.issue(adminId, true))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"UPHOLD\"}"))
                .andExpect(status().isOk());
    }

    private long createLogisticsConfirmedRequest(long itemId, long donorId, long receiverId) throws Exception {
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
                        .content("{\"scheduleRevision\":1,\"agreeToWaiver\":true}"))
                .andExpect(status().isOk());
        return requestId;
    }

    private long insertReport(long reporterId, long itemId, long donorId, String reason) {
        return jdbc.queryForObject(
                "INSERT INTO reports (reporter_id, target_type, item_id, target_user_id, reason, description, stage,"
                        + " status) VALUES (?, 'ITEM', ?, ?, ?, 'Mô tả vi phạm', 'AFTER', 'OPEN') RETURNING id",
                Long.class,
                reporterId,
                itemId,
                donorId,
                reason);
    }

    private String requestStatusOf(long requestId) {
        return jdbc.queryForObject("SELECT status FROM requests WHERE id = ?", String.class, requestId);
    }

    private boolean itemActiveOf(long itemId) {
        return jdbc.queryForObject("SELECT is_active FROM items WHERE id = ?", Boolean.class, itemId);
    }

    private int totalPointsOf(long userId) {
        return jdbc.queryForObject("SELECT total_points FROM users WHERE id = ?", Integer.class, userId);
    }

    private int noticeCount(long userId, String type) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE user_id = ? AND type = ?", Integer.class, userId, type);
    }

    private String token(long userId) {
        return "Bearer " + jwtService.issue(userId, false);
    }

    private long insertUser(String name, boolean admin) {
        int seq = SEQ.incrementAndGet();
        Long userId = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name, phone, status, credit_balance, held_credit,"
                        + " is_admin) VALUES (?, 'hash', ?, ?, 'ACTIVE', 20, 0, ?) RETURNING id",
                Long.class,
                "report-open-tx-" + seq + "-" + System.nanoTime() + "@test.local",
                name,
                String.format("0996%06d", 100 + seq),
                admin);
        userIds.add(userId);
        return userId;
    }

    private long insertItem(long donorId) {
        long categoryId = jdbc.queryForObject("SELECT id FROM item_categories ORDER BY id LIMIT 1", Long.class);
        long areaId = jdbc.queryForObject("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1", Long.class);
        Long itemId = jdbc.queryForObject(
                "INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, condition, status,"
                        + " fee_state, pending_fee, pending_fee_type)"
                        + " VALUES (?, ?, ?, 'GIVE', 'Hàng bị báo cáo', 'Mô tả', 'GOOD', 'APPROVED', 'CHARGED', 0, NULL)"
                        + " RETURNING id",
                Long.class,
                donorId,
                categoryId,
                areaId);
        itemIds.add(itemId);
        return itemId;
    }
}
