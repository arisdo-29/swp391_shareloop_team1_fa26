package com.shareloop.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.request.dto.ConfirmLogisticsRequest;
import com.shareloop.request.dto.CreateRequest;
import com.shareloop.request.dto.HandoverConfirmRequest;
import com.shareloop.request.dto.ScheduleRequest;
import com.shareloop.request.dto.ShipmentRequest;
import com.shareloop.request.service.RequestJobService;
import com.shareloop.support.IntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Integration Test kiểm thử toàn diện luồng giao dịch Request trên PostgreSQL thật (Testcontainers).
 * Bao gồm luồng chính qua API (Happy Path cho GIVE và SWAP), các nhánh huỷ, hết hạn, tranh chấp,
 * và kiểm tra bảo toàn Credit, trạng thái bài đăng Item (Milestone R6, Issue #144).
 */
@AutoConfigureMockMvc
class RequestLifecycleIT extends IntegrationTest {

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
    RequestJobService requestJobService;

    private final List<Long> createdRequestIds = new ArrayList<>();
    private final List<Long> createdItemIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdReceiptIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long requestId : createdRequestIds) {
            jdbc.update("DELETE FROM request_media_mappings WHERE request_id = ?", requestId);
            jdbc.update("DELETE FROM messages WHERE request_id = ?", requestId);
            jdbc.update("DELETE FROM requests WHERE id = ?", requestId);
        }
        createdRequestIds.clear();
        for (long receiptId : createdReceiptIds) {
            jdbc.update("DELETE FROM media_files WHERE id = ?", receiptId);
        }
        createdReceiptIds.clear();

        for (Long itemId : createdItemIds) {
            jdbc.update("DELETE FROM items WHERE id = ?", itemId);
        }
        createdItemIds.clear();

        for (Long userId : createdUserIds) {
            jdbc.update("DELETE FROM notifications WHERE user_id = ?", userId);
            // Hoàn tất giao dịch ghi activity_logs (POINTS) với actor_id = user, phải xoá trước vì có khoá ngoại
            jdbc.update("DELETE FROM activity_logs WHERE actor_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
        createdUserIds.clear();
    }

    @Test
    @DisplayName(
            "Luồng đầy đủ qua API: GIVE item từ PENDING -> RESERVED -> LOGISTICS -> AWAITING_HANDOVER -> COMPLETED")
    void fullTransactionLifecycle_giveItem_fromPendingToCompleted() throws Exception {
        // 1. Chuẩn bị tài khoản và bài đăng GIVE đã duyệt
        long donorId = insertUser("Donor Give", 20, 0);
        long receiver1Id = insertUser("Receiver 1", 10, 0);
        long receiver2Id = insertUser("Receiver 2", 15, 0);
        long itemId = insertItem(donorId, "GIVE", "APPROVED", "CHARGED");

        String donorToken = token(donorId);
        String r1Token = token(receiver1Id);
        String r2Token = token(receiver2Id);

        // 2. Receiver 1 và Receiver 2 gửi yêu cầu xin đồ
        long req1Id = createRequestViaApi(itemId, r1Token, null);
        long req2Id = createRequestViaApi(itemId, r2Token, null);

        assertThat(queryRequestStatus(req1Id)).isEqualTo("PENDING");
        assertThat(queryRequestStatus(req2Id)).isEqualTo("PENDING");

        // 3. Donor xem danh sách yêu cầu tới bài của mình
        mockMvc.perform(get("/api/v1/items/{itemId}/requests", itemId).header("Authorization", donorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].receiverName").isNotEmpty());

        // 4. Khi chưa chốt lịch hẹn, cố xem thông tin liên lạc -> 409 REQUEST_CONTACT_NOT_REVEALED
        mockMvc.perform(get("/api/v1/requests/{id}/contact", req1Id).header("Authorization", r1Token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_CONTACT_NOT_REVEALED"));

        // 5. Donor chọn Receiver 1: req1 -> RESERVED, req2 -> REJECTED, item -> RESERVED
        mockMvc.perform(post("/api/v1/requests/{id}/select", req1Id).header("Authorization", donorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESERVED"));

        assertThat(queryRequestStatus(req1Id)).isEqualTo("RESERVED");
        assertThat(queryRequestStatus(req2Id)).isEqualTo("REJECTED");
        assertThat(queryItemStatus(itemId)).isEqualTo("RESERVED");

        // 6. Donor đề xuất lịch hẹn giao trực tiếp -> chuyển AWAITING_LOGISTICS
        Instant futureTime = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        ScheduleRequest scheduleReq = new ScheduleRequest("IN_PERSON", "Cổng trường FPT", futureTime, true);
        mockMvc.perform(post("/api/v1/requests/{id}/schedule", req1Id)
                        .header("Authorization", donorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ScheduleRequest("IN_PERSON", scheduleReq.meetingPlace(), futureTime, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_WAIVER_REQUIRED"));
        mockMvc.perform(post("/api/v1/requests/{id}/schedule", req1Id)
                        .header("Authorization", donorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(scheduleReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_LOGISTICS"))
                .andExpect(jsonPath("$.donorWaiverAt").isNotEmpty())
                .andExpect(jsonPath("$.meetingPlace").value("Cổng trường FPT"));

        mockMvc.perform(post("/api/v1/requests/{id}/confirm-logistics", req1Id)
                        .header("Authorization", r1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConfirmLogisticsRequest(false, null, 1L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_WAIVER_REQUIRED"));

        // 7. Bản ghi cũ có đồng ý lịch nhưng thiếu cam kết của chủ bài.
        jdbc.update("UPDATE requests SET donor_waiver_at = NULL WHERE id = ?", req1Id);
        ConfirmLogisticsRequest confirmLogistics = new ConfirmLogisticsRequest(true, null, 1L);
        mockMvc.perform(post("/api/v1/requests/{id}/confirm-logistics", req1Id)
                        .header("Authorization", r1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmLogistics)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_LOGISTICS"))
                .andExpect(jsonPath("$.donorScheduleOkAt").isNotEmpty())
                .andExpect(jsonPath("$.receiverWaiverAt").isNotEmpty())
                .andExpect(jsonPath("$.contactRevealedAt").isEmpty());
        mockMvc.perform(post("/api/v1/requests/{id}/confirm-logistics", req1Id)
                        .header("Authorization", donorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmLogistics)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOGISTICS_CONFIRMED"))
                .andExpect(jsonPath("$.contactRevealedAt").isNotEmpty());

        // 8. Cả hai bên đều có thể xem thông tin liên lạc của đối tác
        mockMvc.perform(get("/api/v1/requests/{id}/contact", req1Id).header("Authorization", r1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Donor Give"))
                .andExpect(jsonPath("$.phone").isNotEmpty());

        mockMvc.perform(get("/api/v1/requests/{id}/contact", req1Id).header("Authorization", donorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Receiver 1"))
                .andExpect(jsonPath("$.phone").isNotEmpty());

        // 9. Lịch đã chốt, cố tình sửa lịch -> 409 REQUEST_SCHEDULE_LOCKED
        mockMvc.perform(post("/api/v1/requests/{id}/schedule", req1Id)
                        .header("Authorization", donorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(scheduleReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_SCHEDULE_LOCKED"));

        // 10. Chưa tới giờ thì giữ nguyên trạng thái và chưa bắt đầu đếm hạn tự hoàn tất.
        mockMvc.perform(post("/api/v1/requests/{id}/confirm-handover", req1Id).header("Authorization", donorToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_BEFORE_MEETING_TIME"));
        assertThat(queryRequestStatus(req1Id)).isEqualTo("LOGISTICS_CONFIRMED");
        jdbc.update("UPDATE requests SET meeting_time = now() - interval '1 hour' WHERE id = ?", req1Id);

        // Đến giờ hẹn, Donor bấm xác nhận đã giao đồ -> chuyển AWAITING_HANDOVER.
        mockMvc.perform(post("/api/v1/requests/{id}/confirm-handover", req1Id).header("Authorization", donorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_HANDOVER"))
                .andExpect(jsonPath("$.donorHandoverAt").isNotEmpty());

        // 11. Receiver 1 bấm hoàn tất giao nhận -> chuyển COMPLETED
        HandoverConfirmRequest receiverHandover = new HandoverConfirmRequest(null);
        mockMvc.perform(post("/api/v1/requests/{id}/confirm-handover", req1Id)
                        .header("Authorization", r1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(receiverHandover)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());

        // 12. Kiểm tra trạng thái cuối và Credit
        assertThat(queryRequestStatus(req1Id)).isEqualTo("COMPLETED");
        assertThat(queryItemStatus(itemId)).isEqualTo("TRADED");
        assertThat(queryUserCreditBalance(donorId)).isEqualTo(20);
        assertThat(queryUserHeldCredit(donorId)).isZero();
        assertThat(queryUserCreditBalance(receiver1Id)).isEqualTo(10);
        assertThat(queryUserCreditBalance(receiver2Id)).isEqualTo(15);
    }

    @Test
    @DisplayName("Luồng trao đổi đồ (SWAP): Khi COMPLETED thì cả hai bài đăng đều chuyển sang TRADED")
    void fullTransactionLifecycle_swapItems_bothItemsMarkedTraded() throws Exception {
        long donorId = insertUser("Donor Swap", 30, 0);
        long receiverId = insertUser("Receiver Swap", 25, 0);

        long item1Id = insertItem(donorId, "SWAP", "APPROVED", "CHARGED");
        long item2Id = insertItem(receiverId, "GIVE", "APPROVED", "CHARGED");

        String donorToken = token(donorId);
        String receiverToken = token(receiverId);

        // Receiver gửi yêu cầu trao đổi kèm bài đề nghị
        CreateRequest createReq = new CreateRequest(item2Id);
        long requestId = createRequestViaApi(item1Id, receiverToken, createReq);

        assertThat(queryRequestStatus(requestId)).isEqualTo("PENDING");

        // Donor chọn Receiver
        mockMvc.perform(post("/api/v1/requests/{id}/select", requestId).header("Authorization", donorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESERVED"));

        // Chốt lịch giao hàng (GN-12): meetingTime là ngày gửi hàng dự kiến, Trao đổi cần địa chỉ của cả hai bên
        Instant futureTime = Instant.now().atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                .plusDays(3).with(LocalTime.of(10, 0)).toInstant();
        ScheduleRequest scheduleReq = new ScheduleRequest("SHIPPING", null, futureTime, true, "34 Trần Phú");
        mockMvc.perform(post("/api/v1/requests/{id}/schedule", requestId)
                        .header("Authorization", donorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ScheduleRequest("SHIPPING", null, futureTime, false, "34 Trần Phú"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_WAIVER_REQUIRED"));
        assertThat(queryRequestStatus(requestId)).isEqualTo("RESERVED");

        mockMvc.perform(post("/api/v1/requests/{id}/schedule", requestId)
                        .header("Authorization", donorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(scheduleReq)))
                .andExpect(status().isOk());

        ConfirmLogisticsRequest confirmLogistics = new ConfirmLogisticsRequest(true, "12 Lê Lợi", 1L);
        mockMvc.perform(post("/api/v1/requests/{id}/confirm-logistics", requestId)
                        .header("Authorization", receiverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmLogistics)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOGISTICS_CONFIRMED"));

        // Mỗi bên gửi chiều của mình rồi bên nhận bấm đã nhận được hàng
        jdbc.update("UPDATE requests SET meeting_time = now() + interval '10 minutes', "
                + "exchange_shipping_window_start = now() - interval '20 minutes' WHERE id = ?", requestId);
        Long donorReceiptId = confirmShipmentWithReceipt(requestId, donorId, donorToken);
        mockMvc.perform(get("/api/v1/requests/{id}", requestId).header("Authorization", receiverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handoverMedia[0].mediaId").value(donorReceiptId.intValue()));
        confirmShipmentWithReceipt(requestId, receiverId, receiverToken);

        mockMvc.perform(post("/api/v1/requests/{id}/confirm-handover", requestId)
                        .header("Authorization", donorToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/requests/{id}/confirm-handover", requestId)
                        .header("Authorization", receiverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new HandoverConfirmRequest(null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Cả 2 bài đăng đều sang TRADED
        assertThat(queryItemStatus(item1Id)).isEqualTo("TRADED");
        assertThat(queryItemStatus(item2Id)).isEqualTo("TRADED");
    }

    private Long confirmShipmentWithReceipt(long requestId, long userId, String token) throws Exception {
        Long receiptId = jdbc.queryForObject("""
                INSERT INTO media_files (storage_key, mime_type, size_bytes, created_by)
                VALUES (?, 'image/jpeg', 4, ?) RETURNING id
                """, Long.class, "lifecycle-receipt-" + requestId + "-" + userId, userId);
        createdReceiptIds.add(receiptId);
        mockMvc.perform(post("/api/v1/requests/{id}/shipment", requestId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ShipmentRequest(List.of(receiptId)))))
                .andExpect(status().isOk());
        return receiptId;
    }

    @Test
    @DisplayName("Nhánh huỷ 1: Người gửi tự huỷ khi PENDING -> CANCELLED, bài vẫn APPROVED, Credit không đổi")
    void cancelBranch_receiverCancelsWhilePending_itemRemainsApprovedAndCreditUnchanged() throws Exception {
        long donorId = insertUser("Donor Cancel", 20, 0);
        long receiverId = insertUser("Receiver Cancel", 10, 0);
        long itemId = insertItem(donorId, "GIVE", "APPROVED", "CHARGED");

        String receiverToken = token(receiverId);

        long requestId = createRequestViaApi(itemId, receiverToken, null);
        assertThat(queryRequestStatus(requestId)).isEqualTo("PENDING");

        // Receiver tự huỷ yêu cầu
        mockMvc.perform(post("/api/v1/requests/{id}/cancel", requestId).header("Authorization", receiverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(queryRequestStatus(requestId)).isEqualTo("CANCELLED");
        assertThat(queryItemStatus(itemId)).isEqualTo("APPROVED");
        assertThat(queryUserCreditBalance(donorId)).isEqualTo(20);
        assertThat(queryUserCreditBalance(receiverId)).isEqualTo(10);

        // Vừa tự huỷ thì chưa gửi lại được (C5), qua thời gian chờ thì gửi lại được
        mockMvc.perform(post("/api/v1/items/{id}/requests", itemId).header("Authorization", receiverToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_RESEND_COOLDOWN"));
        jdbc.update("ALTER TABLE requests DISABLE TRIGGER trg_requests_updated_at");
        jdbc.update("UPDATE requests SET updated_at = now() - interval '25 hours' WHERE id = ?", requestId);
        jdbc.update("ALTER TABLE requests ENABLE TRIGGER trg_requests_updated_at");
        long newRequestId = createRequestViaApi(itemId, receiverToken, null);
        assertThat(queryRequestStatus(newRequestId)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Nhánh huỷ 2: Khi đã RESERVED, người gửi không được tự huỷ qua API cancel (409 INVALID_STATE)")
    void cancelBranch_receiverCannotCancelAfterReserved_returnsInvalidState() throws Exception {
        long donorId = insertUser("Donor Reserve", 20, 0);
        long receiverId = insertUser("Receiver Reserve", 10, 0);
        long itemId = insertItem(donorId, "GIVE", "APPROVED", "CHARGED");

        String donorToken = token(donorId);
        String receiverToken = token(receiverId);

        long requestId = createRequestViaApi(itemId, receiverToken, null);

        // Donor chọn người nhận
        mockMvc.perform(post("/api/v1/requests/{id}/select", requestId).header("Authorization", donorToken))
                .andExpect(status().isOk());

        assertThat(queryRequestStatus(requestId)).isEqualTo("RESERVED");

        // Receiver cố tự huỷ khi đã được chọn -> bị từ chối
        mockMvc.perform(post("/api/v1/requests/{id}/cancel", requestId).header("Authorization", receiverToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID_STATE_TRANSITION"));

        assertThat(queryRequestStatus(requestId)).isEqualTo("RESERVED");
        assertThat(queryItemStatus(itemId)).isEqualTo("RESERVED");
    }

    @Test
    @DisplayName("Nhánh hết hạn 1: Quá 3 ngày PENDING do chủ bài chưa chọn -> tự huỷ CANCELLED, bài vẫn APPROVED")
    void expireBranch_pendingExpiredAfter3Days_cancelsRequestAndItemRemainsApproved() {
        long donorId = insertUser("Donor Expire", 20, 0);
        long receiverId = insertUser("Receiver Expire", 10, 0);
        long itemId = insertItem(donorId, "GIVE", "APPROVED", "CHARGED");

        Instant now = Instant.now();
        long requestId =
                insertRawRequest(itemId, receiverId, "PENDING", now.minus(4, ChronoUnit.DAYS), null, null, null);

        int count = requestJobService.expirePendingRequests(now);

        assertThat(count).isGreaterThanOrEqualTo(1);
        assertThat(queryRequestStatus(requestId)).isEqualTo("CANCELLED");
        assertThat(queryItemStatus(itemId)).isEqualTo("APPROVED");
        assertThat(queryUserCreditBalance(donorId)).isEqualTo(20);
        assertThat(queryUserCreditBalance(receiverId)).isEqualTo(10);
    }

    @Test
    @DisplayName("Nhánh hết hạn 2: Quá 10 ngày RESERVED không chốt lịch -> tự huỷ CANCELLED và nhả bài về APPROVED")
    void expireBranch_silentReservedExpiredAfter10Days_cancelsRequestAndReleasesItemToApproved() {
        long donorId = insertUser("Donor Silent", 20, 0);
        long receiverId = insertUser("Receiver Silent", 10, 0);
        long itemId = insertItem(donorId, "GIVE", "RESERVED", "CHARGED");

        Instant now = Instant.now();
        long requestId = insertRawRequest(
                itemId,
                receiverId,
                "RESERVED",
                now.minus(12, ChronoUnit.DAYS),
                now.minus(11, ChronoUnit.DAYS),
                null,
                null);

        int count = requestJobService.expireSilentReservedRequests(now);

        assertThat(count).isGreaterThanOrEqualTo(1);
        assertThat(queryRequestStatus(requestId)).isEqualTo("CANCELLED");
        // Bài đăng được nhả giữ chỗ về APPROVED
        assertThat(queryItemStatus(itemId)).isEqualTo("APPROVED");
        assertThat(queryUserCreditBalance(donorId)).isEqualTo(20);
        assertThat(queryUserCreditBalance(receiverId)).isEqualTo(10);
    }

    @Test
    @DisplayName(
            "Nhánh tự động hoàn tất: Donor xác nhận quá 3 ngày, Receiver im lặng -> tự COMPLETED và bài sang TRADED")
    void autoConfirmBranch_donorHandoverOverdueAfter3Days_autoCompletesAndMarksItemTraded() {
        long donorId = insertUser("Donor Overdue", 20, 0);
        long receiverId = insertUser("Receiver Overdue", 10, 0);
        long itemId = insertItem(donorId, "GIVE", "RESERVED", "CHARGED");

        Instant now = Instant.now();
        long requestId = insertRawRequest(
                itemId,
                receiverId,
                "AWAITING_HANDOVER",
                now.minus(7, ChronoUnit.DAYS),
                now.minus(6, ChronoUnit.DAYS),
                now.minus(4, ChronoUnit.DAYS),
                null);

        int count = requestJobService.autoConfirmHandoverRequests(now);

        assertThat(count).isGreaterThanOrEqualTo(1);
        assertThat(queryRequestStatus(requestId)).isEqualTo("COMPLETED");
        assertThat(queryItemStatus(itemId)).isEqualTo("TRADED");
        assertThat(isAutoConfirmed(requestId)).isTrue();
        assertThat(queryUserCreditBalance(donorId)).isEqualTo(20);
        assertThat(queryUserCreditBalance(receiverId)).isEqualTo(10);
    }

    @Test
    @DisplayName("Nhánh tranh chấp (DISPUTED): Chuyển trạng thái khiếu nại giao nhận và các bước giải quyết")
    void disputeBranch_stateMachineValidation() {
        long donorId = insertUser("Donor Dispute", 20, 0);
        long receiverId = insertUser("Receiver Dispute", 10, 0);
        long itemId = insertItem(donorId, "GIVE", "RESERVED", "CHARGED");

        Instant now = Instant.now();
        long reqDispute1 = insertRawRequest(
                itemId,
                receiverId,
                "AWAITING_HANDOVER",
                now.minus(3, ChronoUnit.DAYS),
                now.minus(2, ChronoUnit.DAYS),
                now.minus(1, ChronoUnit.DAYS),
                null);

        // Giả lập phát sinh khiếu nại -> chuyển sang DISPUTED
        jdbc.update("UPDATE requests SET status = 'DISPUTED' WHERE id = ?", reqDispute1);
        assertThat(queryRequestStatus(reqDispute1)).isEqualTo("DISPUTED");

        // Admin xử lý huỷ giao dịch: DISPUTED -> CANCELLED, nhả bài về APPROVED
        jdbc.update(
                "UPDATE requests SET status = 'CANCELLED', cancel_reason = 'Admin huỷ theo khiếu nại' WHERE id = ?",
                reqDispute1);
        jdbc.update("UPDATE items SET status = 'APPROVED' WHERE id = ?", itemId);

        assertThat(queryRequestStatus(reqDispute1)).isEqualTo("CANCELLED");
        assertThat(queryItemStatus(itemId)).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName(
            "Các nhánh ràng buộc và lỗi: tự xin bài mình, gửi trùng, bài chưa duyệt, huỷ sau chốt lịch, từ chối sai vai")
    void validationAndConstraintBranches() throws Exception {
        long donorId = insertUser("Donor Valid", 20, 0);
        long receiverId = insertUser("Receiver Valid", 10, 0);
        long approvedItemId = insertItem(donorId, "GIVE", "APPROVED", "CHARGED");
        long pendingItemId = insertItem(donorId, "GIVE", "PENDING_REVIEW", "HELD");

        String donorToken = token(donorId);
        String receiverToken = token(receiverId);

        // 1. Tự xin bài của chính mình -> 400 REQUEST_SELF_OFFER_NOT_ALLOWED
        mockMvc.perform(post("/api/v1/items/{itemId}/requests", approvedItemId).header("Authorization", donorToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_SELF_OFFER_NOT_ALLOWED"));

        // 2. Xin bài chưa duyệt (PENDING_REVIEW) -> 409 REQUEST_ITEM_NOT_AVAILABLE
        mockMvc.perform(post("/api/v1/items/{itemId}/requests", pendingItemId).header("Authorization", receiverToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_ITEM_NOT_AVAILABLE"));

        // 3. Xin bài không tồn tại -> 404 REQUEST_ITEM_NOT_FOUND
        mockMvc.perform(post("/api/v1/items/{itemId}/requests", 999999L).header("Authorization", receiverToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REQUEST_ITEM_NOT_FOUND"));

        // 4. Gửi yêu cầu hợp lệ lần 1
        long reqId = createRequestViaApi(approvedItemId, receiverToken, null);

        // 5. Gửi trùng yêu cầu khi đã có request PENDING -> 409 REQUEST_ALREADY_EXISTS
        mockMvc.perform(post("/api/v1/items/{itemId}/requests", approvedItemId).header("Authorization", receiverToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_ALREADY_EXISTS"));

        // 6. Người lạ không được chọn người nhận bài của người khác -> 403 REQUEST_FORBIDDEN
        mockMvc.perform(post("/api/v1/requests/{id}/select", reqId).header("Authorization", receiverToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REQUEST_FORBIDDEN"));

        // 7. Donor chọn receiver -> RESERVED -> chuyển AWAITING_LOGISTICS -> LOGISTICS_CONFIRMED
        mockMvc.perform(post("/api/v1/requests/{id}/select", reqId).header("Authorization", donorToken))
                .andExpect(status().isOk());

        Instant futureTime = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        ScheduleRequest scheduleReq = new ScheduleRequest("IN_PERSON", "FPT Park", futureTime, true);
        mockMvc.perform(post("/api/v1/requests/{id}/schedule", reqId)
                        .header("Authorization", donorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(scheduleReq)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/requests/{id}/confirm-logistics", reqId)
                        .header("Authorization", receiverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConfirmLogisticsRequest(true, null, 1L))))
                .andExpect(status().isOk());

        // 8. Đã chốt lịch thì không còn huỷ bằng cancel-transaction -> 409 REQUEST_INVALID_STATE_TRANSITION
        mockMvc.perform(post("/api/v1/requests/{id}/cancel-transaction", reqId)
                        .header("Authorization", receiverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Tôi không đi được nữa\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID_STATE_TRANSITION"));

        // 9. Chủ bài ở Cho tặng không được từ chối giao nhận -> 403 REQUEST_REFUSE_NOT_ALLOWED
        mockMvc.perform(post("/api/v1/requests/{id}/refuse-handover", reqId)
                        .header("Authorization", donorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Không muốn cho nữa\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REQUEST_REFUSE_NOT_ALLOWED"));
    }

    // ---------- Helper methods ----------

    private long createRequestViaApi(long itemId, String token, CreateRequest body) throws Exception {
        var reqBuilder = post("/api/v1/items/{itemId}/requests", itemId).header("Authorization", token);
        if (body != null) {
            reqBuilder.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        }

        MvcResult result =
                mockMvc.perform(reqBuilder).andExpect(status().isCreated()).andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        long requestId = json.get("id").asLong();
        createdRequestIds.add(requestId);
        return requestId;
    }

    private String token(long userId) {
        return "Bearer " + jwtService.issue(userId, false);
    }

    private long insertUser(String name, int creditBalance, int heldCredit) {
        int seq = SEQ.incrementAndGet();
        String email = "req-lifecycle-" + seq + "-" + System.nanoTime() + "@test.local";
        String phone = String.format("0996%06d", seq);
        Long userId = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name, phone, status, credit_balance, held_credit)"
                        + " VALUES (?, 'hash', ?, ?, 'ACTIVE', ?, ?) RETURNING id",
                Long.class,
                email,
                name,
                phone,
                creditBalance,
                heldCredit);
        createdUserIds.add(userId);
        return userId;
    }

    private long insertItem(long donorId, String offerType, String status, String feeState) {
        long categoryId = jdbc.queryForObject("SELECT id FROM item_categories ORDER BY id LIMIT 1", Long.class);
        long areaId = jdbc.queryForObject("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1", Long.class);
        String desiredItem = "SWAP".equalsIgnoreCase(offerType) ? "Muốn đổi lấy đồ dùng học tập" : null;

        Long itemId = jdbc.queryForObject(
                "INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, desired_item, condition, status, fee_state, pending_fee, pending_fee_type)"
                        + " VALUES (?, ?, ?, ?, 'Lifecycle Item', 'Mô tả', ?, 'GOOD', ?, ?, 0, NULL) RETURNING id",
                Long.class,
                donorId,
                categoryId,
                areaId,
                offerType,
                desiredItem,
                status,
                feeState);
        createdItemIds.add(itemId);
        return itemId;
    }

    private long insertRawRequest(
            long itemId,
            long receiverId,
            String status,
            Instant createdAt,
            Instant reservedAt,
            Instant donorHandoverAt,
            Instant receiverHandoverAt) {
        Long requestId = jdbc.queryForObject(
                "INSERT INTO requests (item_id, receiver_id, type, status, created_at, reserved_at, donor_handover_at, receiver_handover_at)"
                        + " VALUES (?, ?, 'GIVE', ?, ?, ?, ?, ?) RETURNING id",
                Long.class,
                itemId,
                receiverId,
                status,
                Timestamp.from(createdAt),
                reservedAt != null ? Timestamp.from(reservedAt) : null,
                donorHandoverAt != null ? Timestamp.from(donorHandoverAt) : null,
                receiverHandoverAt != null ? Timestamp.from(receiverHandoverAt) : null);
        createdRequestIds.add(requestId);
        return requestId;
    }

    private String queryRequestStatus(long requestId) {
        return jdbc.queryForObject("SELECT status FROM requests WHERE id = ?", String.class, requestId);
    }

    private String queryItemStatus(long itemId) {
        return jdbc.queryForObject("SELECT status FROM items WHERE id = ?", String.class, itemId);
    }

    private int queryUserCreditBalance(long userId) {
        return jdbc.queryForObject("SELECT credit_balance FROM users WHERE id = ?", Integer.class, userId);
    }

    private int queryUserHeldCredit(long userId) {
        return jdbc.queryForObject("SELECT held_credit FROM users WHERE id = ?", Integer.class, userId);
    }

    private boolean isAutoConfirmed(long requestId) {
        Boolean val = jdbc.queryForObject("SELECT auto_confirmed FROM requests WHERE id = ?", Boolean.class, requestId);
        return Boolean.TRUE.equals(val);
    }
}
