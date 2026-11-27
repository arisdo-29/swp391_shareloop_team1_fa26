package com.shareloop.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.request.dto.RepeatedCancelerRow;
import com.shareloop.request.dto.ScheduleRequest;
import com.shareloop.request.service.RequestDeadlineJobService;
import com.shareloop.request.service.RequestQueryService;
import com.shareloop.support.IntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
 * Đề nghị huỷ có xác nhận (GN-06) chạy thật trên PostgreSQL: các nhánh đồng ý, không đồng ý, im lặng, hai thao tác
 * đồng thời và câu đếm huỷ nhiều lần cho màn Giám sát bất thường.
 */
@AutoConfigureMockMvc
class RequestCancelRequestIT extends IntegrationTest {
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    RequestDeadlineJobService deadlineJobService;

    @Autowired
    RequestQueryService requestQueryService;

    private final List<Long> requestIds = new ArrayList<>();
    private final List<Long> itemIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long requestId : requestIds) {
            jdbc.update("DELETE FROM reports WHERE request_id = ?", requestId);
            jdbc.update("DELETE FROM request_media_mappings WHERE request_id = ?", requestId);
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
    void acceptCancelRequest_whenDonorRequestedBeforeMeeting_cancelsAndReopensItem() throws Exception {
        // given
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long itemId = insertItem(donorId);
        long requestId = createLogisticsConfirmedRequest(itemId, donorId, receiverId, 24);
        mockMvc.perform(post("/api/v1/requests/{id}/cancel-request", requestId)
                        .header("Authorization", token(donorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"BUSY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOGISTICS_CONFIRMED"));

        // when
        mockMvc.perform(post("/api/v1/requests/{id}/cancel-request/accept", requestId)
                        .header("Authorization", token(receiverId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // then
        assertThat(statusOf(requestId)).isEqualTo("CANCELLED");
        assertThat(itemStatusOf(itemId)).isEqualTo("APPROVED");
        assertThat(trustStarsOf(donorId)).isEqualTo(trustStarsOf(receiverId));
    }

    @Test
    void rejectCancelRequest_whenReceiverDisagrees_movesToDisputedAndOpensReport() throws Exception {
        // given
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long itemId = insertItem(donorId);
        long requestId = createLogisticsConfirmedRequest(itemId, donorId, receiverId, 24);
        mockMvc.perform(post("/api/v1/requests/{id}/cancel-request", requestId)
                        .header("Authorization", token(donorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"NO_LONGER_NEEDED\"}"))
                .andExpect(status().isOk());

        // when
        mockMvc.perform(post("/api/v1/requests/{id}/cancel-request/reject", requestId)
                        .header("Authorization", token(receiverId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"explanation\":\"Tôi vẫn muốn nhận món đồ này\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPUTED"));

        // then
        assertThat(statusOf(requestId)).isEqualTo("DISPUTED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reports WHERE request_id = ?", Integer.class, requestId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT description FROM reports WHERE request_id = ?", String.class, requestId))
                .contains("Tôi vẫn muốn nhận món đồ này");
    }

    @Test
    void concludeUphold_whenDisputedRequest_appliesItemOutcomesAndRequiresEveryItem() throws Exception {
        // given: giao dịch DISPUTED sau khi bên kia không đồng ý đề nghị huỷ
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long adminId = insertUser("Admin");
        long itemId = insertItem(donorId);
        long requestId = createLogisticsConfirmedRequest(itemId, donorId, receiverId, 24);
        mockMvc.perform(post("/api/v1/requests/{id}/cancel-request", requestId)
                        .header("Authorization", token(donorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"NO_LONGER_NEEDED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/requests/{id}/cancel-request/reject", requestId)
                        .header("Authorization", token(receiverId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"explanation\":\"Tôi vẫn muốn nhận món đồ này\"}"))
                .andExpect(status().isOk());
        long reportId = jdbc.queryForObject("SELECT id FROM reports WHERE request_id = ?", Long.class, requestId);
        jdbc.update("UPDATE reports SET response_due_at = now() - interval '1 hour' WHERE id = ?", reportId);
        String adminAuth = "Bearer " + jwtService.issue(adminId, true);

        // thiếu lựa chọn cho món thì báo lỗi và giao dịch vẫn DISPUTED
        mockMvc.perform(post("/api/v1/admin/reports/{id}/conclude", reportId)
                        .header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"UPHOLD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_ITEM_OUTCOMES_REQUIRED"));
        assertThat(statusOf(requestId)).isEqualTo("DISPUTED");

        // when: Admin chọn Đã đổi chủ cho món chính
        mockMvc.perform(post("/api/v1/admin/reports/{id}/conclude", reportId)
                        .header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"UPHOLD\",\"itemOutcomes\":[{\"itemId\":" + itemId
                                + ",\"choice\":\"TRANSFERRED\"}]}"))
                .andExpect(status().isOk());

        // then: giao dịch huỷ, bài bị ẩn bằng cờ của Admin và lý do lấy từ nhật ký
        assertThat(statusOf(requestId)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT hidden_by_admin FROM items WHERE id = ?", Boolean.class, itemId))
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT is_active FROM items WHERE id = ?", Boolean.class, itemId))
                .isFalse();
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM activity_logs WHERE type = 'ADMIN_ITEM_HIDDEN' AND target_id = ?"
                                + " AND reason = 'Món đã được trao trong một giao dịch bị huỷ'",
                        Integer.class,
                        itemId))
                .isEqualTo(1);
    }

    @Test
    void concludeUphold_whenRejectedBusyCancelWithoutPenalty_cancelsAndDeductsNoStar() throws Exception {
        // given: người cho đề nghị huỷ vì bận, người nhận không đồng ý
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long itemId = insertItem(donorId);
        long requestId = rejectedCancelRequest(itemId, donorId, receiverId, "BUSY", false);
        long reportId = reportOf(requestId);
        int donorStars = trustStarsOf(donorId);
        int receiverStars = trustStarsOf(receiverId);

        // when: Admin chấp thuận, chọn Ẩn cho món và không chọn ai bị trừ sao
        concludeAsAdmin(
                        reportId,
                        "{\"decision\":\"UPHOLD\",\"itemOutcomes\":[{\"itemId\":" + itemId + ",\"choice\":\"HIDE\"}]}")
                .andExpect(status().isOk());

        // then: lý do khiếu nại là chung, giao dịch huỷ, không ai bị trừ sao, món bị ẩn bằng cờ của Admin
        assertThat(jdbc.queryForObject("SELECT reason FROM reports WHERE id = ?", String.class, reportId))
                .isEqualTo("OTHER");
        assertThat(statusOf(requestId)).isEqualTo("CANCELLED");
        assertThat(trustStarsOf(receiverId)).isEqualTo(receiverStars);
        assertThat(trustStarsOf(donorId)).isEqualTo(donorStars);
        assertThat(jdbc.queryForObject("SELECT stars_deducted FROM reports WHERE id = ?", Integer.class, reportId))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT hidden_by_admin FROM items WHERE id = ?", Boolean.class, itemId))
                .isTrue();
    }

    @Test
    void concludeUphold_whenRejectedBusyCancelAndAdminPicksRequester_deductsOnlyThatPartyOneStar() throws Exception {
        // given
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long itemId = insertItem(donorId);
        long requestId = rejectedCancelRequest(itemId, donorId, receiverId, "NO_LONGER_NEEDED", false);
        long reportId = reportOf(requestId);
        int donorStars = trustStarsOf(donorId);
        int receiverStars = trustStarsOf(receiverId);

        // when: Admin chọn trừ sao người đề nghị huỷ
        concludeAsAdmin(
                        reportId,
                        "{\"decision\":\"UPHOLD\",\"penalizedUserId\":" + donorId + ",\"itemOutcomes\":[{\"itemId\":"
                                + itemId + ",\"choice\":\"REOPEN\"}]}")
                .andExpect(status().isOk());

        // then: chỉ người đề nghị bị trừ, bên không đồng ý vẫn nguyên sao
        assertThat(trustStarsOf(donorId)).isEqualTo(donorStars - 1);
        assertThat(trustStarsOf(receiverId)).isEqualTo(receiverStars);
        assertThat(statusOf(requestId)).isEqualTo("CANCELLED");
        assertThat(itemStatusOf(itemId)).isEqualTo("APPROVED");
    }

    @Test
    void concludeUphold_whenPenalizedUserIsNotAParty_returnsBadRequestAndKeepsDispute() throws Exception {
        // given
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long strangerId = insertUser("Stranger");
        long itemId = insertItem(donorId);
        long requestId = rejectedCancelRequest(itemId, donorId, receiverId, "OTHER", false);

        // when
        concludeAsAdmin(
                        reportOf(requestId),
                        "{\"decision\":\"UPHOLD\",\"penalizedUserId\":" + strangerId + ",\"itemOutcomes\":[{\"itemId\":"
                                + itemId + ",\"choice\":\"REOPEN\"}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REPORT_PENALIZED_USER_INVALID"));

        // then
        assertThat(statusOf(requestId)).isEqualTo("DISPUTED");
    }

    @Test
    void concludeDismiss_whenRejectedBusyCancel_resumesTransactionAndClearsCancelRequest() throws Exception {
        // given
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long itemId = insertItem(donorId);
        long requestId = rejectedCancelRequest(itemId, donorId, receiverId, "BUSY", false);
        int donorStars = trustStarsOf(donorId);
        int receiverStars = trustStarsOf(receiverId);

        // when: Admin bác khiếu nại
        concludeAsAdmin(reportOf(requestId), "{\"decision\":\"DISMISS\"}").andExpect(status().isOk());

        // then: giao dịch tiếp tục, đề nghị huỷ được xoá, món vẫn đang giữ chỗ, không ai bị trừ sao
        assertThat(statusOf(requestId)).isEqualTo("LOGISTICS_CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT cancel_requested_by FROM requests WHERE id = ?", Long.class, requestId))
                .isNull();
        assertThat(itemStatusOf(itemId)).isEqualTo("RESERVED");
        assertThat(trustStarsOf(donorId)).isEqualTo(donorStars);
        assertThat(trustStarsOf(receiverId)).isEqualTo(receiverStars);
    }

    @Test
    void rejectLaterCancellation_whenEarlierReportIsClosed_opensPendingReport() throws Exception {
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long requestId = rejectedCancelRequest(insertItem(donorId), donorId, receiverId, "BUSY", false);
        long earlierReportId = reportOf(requestId);
        concludeAsAdmin(earlierReportId, "{\"decision\":\"DISMISS\"}").andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/requests/{id}/cancel-request", requestId)
                        .header("Authorization", token(donorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"NO_LONGER_NEEDED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/requests/{id}/cancel-request/reject", requestId)
                        .header("Authorization", token(receiverId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"explanation\":\"Tôi muốn tiếp tục giao dịch này\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPUTED"));

        assertThat(jdbc.queryForObject("SELECT status FROM reports WHERE id = ?", String.class, earlierReportId))
                .isEqualTo("DISMISSED");
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM reports WHERE request_id = ? AND status IN ('OPEN', 'AWAITING_RESPONSE')",
                        Integer.class,
                        requestId))
                .isEqualTo(1);
    }

    @Test
    void concludeUphold_whenRejectedNoShowCancel_deductsOneStarFromAccusedParty() throws Exception {
        // given: người nhận tố người cho không đến, người cho không đồng ý
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long itemId = insertItem(donorId);
        long requestId = rejectedCancelRequest(itemId, receiverId, donorId, "COUNTERPART_NO_SHOW", true);
        long reportId = reportOf(requestId);
        int donorStars = trustStarsOf(donorId);
        int receiverStars = trustStarsOf(receiverId);

        // when
        concludeAsAdmin(
                        reportId,
                        "{\"decision\":\"UPHOLD\",\"itemOutcomes\":[{\"itemId\":" + itemId
                                + ",\"choice\":\"REOPEN\"}]}")
                .andExpect(status().isOk());

        // then: lý do khiếu nại là NO_SHOW và bên bị tố bị trừ 1 sao
        assertThat(jdbc.queryForObject("SELECT reason FROM reports WHERE id = ?", String.class, reportId))
                .isEqualTo("NO_SHOW");
        assertThat(trustStarsOf(donorId)).isEqualTo(donorStars - 1);
        assertThat(trustStarsOf(receiverId)).isEqualTo(receiverStars);
        assertThat(statusOf(requestId)).isEqualTo("CANCELLED");
    }

    @Test
    void concludeUphold_whenRejectedNoShowAndAdminNamesRequester_stillDeductsOnlyAccusedParty() throws Exception {
        // given
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long itemId = insertItem(donorId);
        long requestId = rejectedCancelRequest(itemId, receiverId, donorId, "COUNTERPART_NO_SHOW", true);
        int donorStars = trustStarsOf(donorId);
        int receiverStars = trustStarsOf(receiverId);

        // when: penalizedUserId bị bỏ qua với khiếu nại có tố
        concludeAsAdmin(
                        reportOf(requestId),
                        "{\"decision\":\"UPHOLD\",\"penalizedUserId\":" + receiverId + ",\"itemOutcomes\":[{\"itemId\":"
                                + itemId + ",\"choice\":\"REOPEN\"}]}")
                .andExpect(status().isOk());

        // then
        assertThat(trustStarsOf(donorId)).isEqualTo(donorStars - 1);
        assertThat(trustStarsOf(receiverId)).isEqualTo(receiverStars);
    }

    @Test
    void withdrawCancelRequest_whenRequesterWithdraws_keepsTransactionRunning() throws Exception {
        // given
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long itemId = insertItem(donorId);
        long requestId = createLogisticsConfirmedRequest(itemId, donorId, receiverId, 24);
        mockMvc.perform(post("/api/v1/requests/{id}/cancel-request", requestId)
                        .header("Authorization", token(donorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"BUSY\"}"))
                .andExpect(status().isOk());

        // when
        mockMvc.perform(delete("/api/v1/requests/{id}/cancel-request", requestId)
                        .header("Authorization", token(donorId)))
                .andExpect(status().isOk());

        // then
        assertThat(statusOf(requestId)).isEqualTo("LOGISTICS_CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT cancel_requested_by FROM requests WHERE id = ?", Long.class, requestId))
                .isNull();
    }

    @Test
    void expireCancelRequests_whenSilentPastDeadlineWithNoShowReason_cancelsAndOpensNoShowReport() throws Exception {
        // given: giờ hẹn đã qua, người nhận báo người cho không đến điểm hẹn rồi bên kia im lặng
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long itemId = insertItem(donorId);
        long requestId = createLogisticsConfirmedRequest(itemId, donorId, receiverId, 24);
        jdbc.update("UPDATE requests SET meeting_time = now() - interval '2 hours' WHERE id = ?", requestId);
        mockMvc.perform(post("/api/v1/requests/{id}/handover-failed", requestId)
                        .header("Authorization", token(receiverId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"COUNTERPART_NO_SHOW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOGISTICS_CONFIRMED"));
        jdbc.update("UPDATE requests SET cancel_requested_at = now() - interval '25 hours' WHERE id = ?", requestId);

        // when
        int expired = deadlineJobService.expireCancelRequests(Instant.now());

        // then
        assertThat(expired).isGreaterThanOrEqualTo(1);
        assertThat(statusOf(requestId)).isEqualTo("CANCELLED");
        assertThat(itemStatusOf(itemId)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject(
                        "SELECT reason FROM reports WHERE request_id = ? AND target_user_id = ?",
                        String.class,
                        requestId,
                        donorId))
                .isEqualTo("NO_SHOW");
        assertThat(trustStarsOf(donorId)).isEqualTo(trustStarsOf(receiverId));
    }

    @Test
    void acceptAndConfirmHandover_whenRunAtTheSameTime_endInOneConsistentState() throws Exception {
        // given: người cho đề nghị huỷ, người nhận vừa bấm đồng ý vừa bấm đã giao nhận xong cùng lúc
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long itemId = insertItem(donorId);
        long requestId = createLogisticsConfirmedRequest(itemId, donorId, receiverId, 24);
        mockMvc.perform(post("/api/v1/requests/{id}/cancel-request", requestId)
                        .header("Authorization", token(donorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"BUSY\"}"))
                .andExpect(status().isOk());

        // Đến giờ hẹn, đề nghị huỷ vẫn đang trong hạn phản hồi.
        jdbc.update("UPDATE requests SET meeting_time = now() - interval '1 hour' WHERE id = ?", requestId);

        // when
        List<Integer> statuses = runAtTheSameTime(
                () -> mockMvc.perform(post("/api/v1/requests/{id}/cancel-request/accept", requestId)
                                .header("Authorization", token(receiverId)))
                        .andReturn()
                        .getResponse()
                        .getStatus(),
                () -> mockMvc.perform(post("/api/v1/requests/{id}/confirm-handover", requestId)
                                .header("Authorization", token(receiverId)))
                        .andReturn()
                        .getResponse()
                        .getStatus());

        // then: đúng một thao tác thắng, thao tác còn lại bị 409
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        String finalStatus = statusOf(requestId);
        assertThat(finalStatus).isIn("CANCELLED", "DISPUTED");
        assertThat(itemStatusOf(itemId)).isEqualTo("CANCELLED".equals(finalStatus) ? "APPROVED" : "RESERVED");
    }

    @Test
    void findRepeatedCancelers_whenUserCancelledThreeAfterSeeingContact_returnsThatUser() {
        // given
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        for (int index = 0; index < 3; index++) {
            long itemId = insertItem(donorId);
            insertCancelledRequest(itemId, receiverId, donorId, true);
        }
        // giao dịch huỷ khi chưa thấy liên hệ thì không tính
        insertCancelledRequest(insertItem(donorId), receiverId, donorId, false);

        // when
        List<RepeatedCancelerRow> rows =
                requestQueryService.findRepeatedCancelers(Instant.now().minus(30, ChronoUnit.DAYS), 3);

        // then
        assertThat(rows).extracting(RepeatedCancelerRow::userId).contains(donorId);
        assertThat(rows.stream()
                        .filter(row -> row.userId().equals(donorId))
                        .findFirst()
                        .orElseThrow()
                        .cancelCount())
                .isEqualTo(3);
    }

    /**
     * Tạo giao dịch đã chốt lịch, một bên đề nghị huỷ với mã lý do rồi bên kia không đồng ý (giao dịch sang DISPUTED).
     * Lý do tố bên kia chỉ dùng được từ giờ hẹn nên afterMeeting đưa giờ hẹn về quá khứ.
     */
    private long rejectedCancelRequest(
            long itemId, long requesterId, long rejecterId, String code, boolean afterMeeting) throws Exception {
        long donorId = jdbc.queryForObject("SELECT donor_id FROM items WHERE id = ?", Long.class, itemId);
        long receiverId = requesterId == donorId ? rejecterId : requesterId;
        long requestId = createLogisticsConfirmedRequest(itemId, donorId, receiverId, 24);
        if (afterMeeting) {
            jdbc.update("UPDATE requests SET meeting_time = now() - interval '2 hours' WHERE id = ?", requestId);
        }
        String endpoint = afterMeeting ? "handover-failed" : "cancel-request";
        mockMvc.perform(post("/api/v1/requests/{id}/" + endpoint, requestId)
                        .header("Authorization", token(requesterId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"reason\":\"Tôi có việc riêng nên không thể\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/requests/{id}/cancel-request/reject", requestId)
                        .header("Authorization", token(rejecterId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"explanation\":\"Tôi không đồng ý huỷ giao dịch này\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPUTED"));
        return requestId;
    }

    private long reportOf(long requestId) {
        long reportId = jdbc.queryForObject("SELECT id FROM reports WHERE request_id = ?", Long.class, requestId);
        // hết hạn giải trình để Admin kết luận được ngay
        jdbc.update("UPDATE reports SET response_due_at = now() - interval '1 hour' WHERE id = ?", reportId);
        return reportId;
    }

    private org.springframework.test.web.servlet.ResultActions concludeAsAdmin(long reportId, String body)
            throws Exception {
        long adminId = insertUser("Admin");
        return mockMvc.perform(post("/api/v1/admin/reports/{id}/conclude", reportId)
                .header("Authorization", "Bearer " + jwtService.issue(adminId, true))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @SafeVarargs
    private List<Integer> runAtTheSameTime(Callable<Integer>... actions) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(actions.length);
        CountDownLatch ready = new CountDownLatch(actions.length);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (Callable<Integer> action : actions) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                go.await();
                return action.call();
            }));
        }
        ready.await();
        go.countDown();
        List<Integer> results = new ArrayList<>();
        for (Future<Integer> future : futures) {
            results.add(future.get());
        }
        executor.shutdown();
        return results;
    }

    /** Đi qua API: gửi yêu cầu, chọn người, chốt lịch. Giờ hẹn cách bây giờ meetingHoursAhead giờ. */
    private long createLogisticsConfirmedRequest(long itemId, long donorId, long receiverId, int meetingHoursAhead)
            throws Exception {
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
        Instant meetingTime =
                Instant.now().plus(meetingHoursAhead, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
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

    private void insertCancelledRequest(long itemId, long receiverId, long requesterId, boolean contactRevealed) {
        Long requestId = jdbc.queryForObject(
                "INSERT INTO requests (item_id, receiver_id, type, status, contact_revealed_at, cancel_requested_by,"
                        + " cancel_requested_at, cancel_request_code)"
                        + " VALUES (?, ?, 'GIVE', 'CANCELLED', ?, ?, now(), 'BUSY') RETURNING id",
                Long.class,
                itemId,
                receiverId,
                contactRevealed ? java.sql.Timestamp.from(Instant.now()) : null,
                requesterId);
        requestIds.add(requestId);
    }

    private String statusOf(long requestId) {
        return jdbc.queryForObject("SELECT status FROM requests WHERE id = ?", String.class, requestId);
    }

    private String itemStatusOf(long itemId) {
        return jdbc.queryForObject("SELECT status FROM items WHERE id = ?", String.class, itemId);
    }

    private int trustStarsOf(long userId) {
        return jdbc.queryForObject("SELECT trust_stars FROM users WHERE id = ?", Integer.class, userId);
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
                "cancel-request-" + seq + "-" + System.nanoTime() + "@test.local",
                name,
                String.format("0996%06d", seq));
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
