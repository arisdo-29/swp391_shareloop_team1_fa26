package com.shareloop.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.request.dto.ScheduleRequest;
import com.shareloop.request.service.RequestDeadlineJobService;
import com.shareloop.support.IntegrationTest;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/** Giao hàng hai chiều (GN-12) chạy thật trên PostgreSQL: địa chỉ, gửi và nhận từng chiều, việc nền theo chiều. */
@AutoConfigureMockMvc
class RequestShippingIT extends IntegrationTest {
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

    private final List<Long> requestIds = new ArrayList<>();
    private final List<Long> itemIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> receiptIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long requestId : requestIds) {
            jdbc.update("DELETE FROM reports WHERE request_id = ?", requestId);
            jdbc.update("DELETE FROM request_media_mappings WHERE request_id = ?", requestId);
            jdbc.update("DELETE FROM messages WHERE request_id = ?", requestId);
            jdbc.update("DELETE FROM requests WHERE id = ?", requestId);
        }
        for (Long mediaId : receiptIds) {
            jdbc.update("DELETE FROM media_files WHERE id = ?", mediaId);
        }
        for (Long itemId : itemIds) {
            jdbc.update("DELETE FROM items WHERE id = ?", itemId);
        }
        for (Long userId : userIds) {
            jdbc.update("DELETE FROM notifications WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM activity_logs WHERE actor_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
        receiptIds.clear();
        requestIds.clear();
        itemIds.clear();
        userIds.clear();
    }

    @Test
    void swapShipping_whenBothLegsShippedAndReceived_completesAndHidesAddressBeforeConfirm() throws Exception {
        // given: Trao đổi, mỗi bên nhập địa chỉ của mình
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long itemId = insertItem(donorId, "SWAP");
        long offeredId = insertItem(receiverId, "SWAP");
        long requestId = createSwapRequest(itemId, offeredId, donorId, receiverId);
        propose(requestId, receiverId, "12 Lê Lợi");

        // địa chỉ của người nhận chưa lộ cho chủ bài trước khi chốt lịch
        mockMvc.perform(get("/api/v1/requests/{id}", requestId).header("Authorization", token(donorId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiverAddress").doesNotExist());
        mockMvc.perform(get("/api/v1/requests/{id}", requestId).header("Authorization", token(receiverId)))
                .andExpect(jsonPath("$.receiverAddress").value("12 Lê Lợi"));

        // chủ bài thiếu địa chỉ thì không chốt được
        mockMvc.perform(post("/api/v1/requests/{id}/confirm-logistics", requestId)
                        .header("Authorization", token(donorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduleRevision\":1,\"agreeToWaiver\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_SHIPPING_ADDRESS_REQUIRED"));
        // chủ bài nhập địa chỉ rồi xác nhận: người nhận đã đồng ý từ lúc đề xuất nên lịch được chốt
        mockMvc.perform(post("/api/v1/requests/{id}/confirm-logistics", requestId)
                        .header("Authorization", token(donorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduleRevision\":1,\"agreeToWaiver\":true,\"shippingAddress\":\"34 Trần Phú\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOGISTICS_CONFIRMED"))
                .andExpect(jsonPath("$.donorAddress").value("34 Trần Phú"))
                .andExpect(jsonPath("$.receiverAddress").value("12 Lê Lợi"))
                .andExpect(jsonPath("$.sendDeadlineAt").isNotEmpty());

        // nhận hàng trước khi gửi bị chặn
        mockMvc.perform(post("/api/v1/requests/{id}/confirm-handover", requestId)
                        .header("Authorization", token(receiverId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_LEG_NOT_SHIPPED"));

        // when: hai bên gửi và nhận
        ship(requestId, donorId);
        mockMvc.perform(post("/api/v1/requests/{id}/confirm-handover", requestId)
                        .header("Authorization", token(receiverId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_HANDOVER"));
        ship(requestId, receiverId);
        mockMvc.perform(post("/api/v1/requests/{id}/confirm-handover", requestId)
                        .header("Authorization", token(donorId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.donorShippedAt").isNotEmpty())
                .andExpect(jsonPath("$.receiverShippedAt").isNotEmpty())
                .andExpect(jsonPath("$.donorTrackingCode").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.receiverTrackingCode").doesNotHaveJsonPath());

        // then
        assertThat(jdbc.queryForObject("SELECT status FROM items WHERE id = ?", String.class, itemId))
                .isEqualTo("TRADED");
        assertThat(jdbc.queryForObject("SELECT status FROM items WHERE id = ?", String.class, offeredId))
                .isEqualTo("TRADED");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void refusal_whenSwappingOnlyReopensItemsThatHaveNotBeenSent(boolean otherItemShipped) throws Exception {
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long itemId = insertItem(donorId, "SWAP");
        long offeredId = insertItem(receiverId, "SWAP");
        long requestId = createSwapRequest(itemId, offeredId, donorId, receiverId);
        propose(requestId, receiverId, "12 Lê Lợi");
        mockMvc.perform(post("/api/v1/requests/{id}/confirm-logistics", requestId)
                        .header("Authorization", token(donorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreeToWaiver\":true,\"shippingAddress\":\"34 Trần Phú\",\"scheduleRevision\":1}"))
                .andExpect(status().isOk());
        ship(requestId, donorId);
        if (otherItemShipped) {
            ship(requestId, receiverId);
        }

        mockMvc.perform(post("/api/v1/requests/{id}/refuse-handover", requestId)
                        .header("Authorization", token(receiverId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Món nhận được không đúng mô tả\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(jdbc.queryForObject("SELECT is_active FROM items WHERE id = ?", Boolean.class, itemId))
                .isFalse();
        assertThat(jdbc.queryForObject("SELECT is_active FROM items WHERE id = ?", Boolean.class, offeredId))
                .isEqualTo(!otherItemShipped);
        assertThat(jdbc.queryForObject("SELECT hidden_by_admin FROM items WHERE id = ?", Boolean.class, offeredId))
                .isEqualTo(otherItemShipped);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void exchangeTimeout_cancelsAndHandlesBothReopenSchedulesAfterAdminDecision(boolean upheld) throws Exception {
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long itemId = insertItem(donorId, "SWAP");
        long offeredId = insertItem(receiverId, "SWAP");
        long requestId = createSwapRequest(itemId, offeredId, donorId, receiverId);
        propose(requestId, receiverId, "12 Lê Lợi");
        mockMvc.perform(post("/api/v1/requests/{id}/confirm-logistics", requestId)
                        .header("Authorization", token(donorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduleRevision\":1,\"agreeToWaiver\":true,\"shippingAddress\":\"34 Trần Phú\"}"))
                .andExpect(status().isOk());
        ship(requestId, donorId);

        // Mô phỏng giờ hẹn vừa qua trong DB và chạy tác vụ hai lần.
        jdbc.update("UPDATE requests SET meeting_time = now() - interval '1 second' WHERE id = ?", requestId);
        Instant cancelledAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        int donorStars = trustStarsOf(donorId);
        int receiverStars = trustStarsOf(receiverId);
        deadlineJobService.processExchangeShipping(cancelledAt);
        deadlineJobService.processExchangeShipping(cancelledAt);
        assertThat(requestStatus(requestId)).isEqualTo("CANCELLED");
        assertThat(isItemActive(itemId)).isFalse();
        assertThat(isItemActive(offeredId)).isFalse();
        long reportId = jdbc.queryForObject(
                "SELECT id FROM reports WHERE request_id = ? AND reason = 'NO_SHOW' AND exchange_shipping_timeout",
                Long.class, requestId);
        assertThat(jdbc.queryForObject("SELECT target_user_id FROM reports WHERE id = ?", Long.class, reportId))
                .isEqualTo(receiverId);
        assertThat(trustStarsOf(donorId)).isEqualTo(donorStars);
        assertThat(trustStarsOf(receiverId)).isEqualTo(receiverStars);

        deadlineJobService.processExchangeShipping(cancelledAt.plus(24, ChronoUnit.HOURS).minusSeconds(1));
        assertThat(isItemActive(itemId)).isFalse();
        deadlineJobService.processExchangeShipping(cancelledAt.plus(24, ChronoUnit.HOURS));
        assertThat(isItemActive(itemId)).isTrue();
        assertThat(isItemActive(offeredId)).isFalse();

        jdbc.update("UPDATE reports SET response_due_at = now() - interval '1 hour' WHERE id = ?", reportId);
        long adminId = insertUser("Admin");
        String decision = upheld ? "UPHOLD" : "DISMISS";
        mockMvc.perform(post("/api/v1/admin/reports/{id}/conclude", reportId)
                        .header("Authorization", "Bearer " + jwtService.issue(adminId, true))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"" + decision + "\",\"deliberateFalseReport\":true}"))
                .andExpect(status().isOk());
        assertThat(requestStatus(requestId)).isEqualTo("CANCELLED");
        assertThat(trustStarsOf(donorId)).isEqualTo(donorStars);
        assertThat(trustStarsOf(receiverId)).isEqualTo(receiverStars - (upheld ? 1 : 0));
        assertThat(isItemActive(offeredId)).isFalse();

        deadlineJobService.processExchangeShipping(Instant.now().plus(25, ChronoUnit.HOURS));
        assertThat(isItemActive(offeredId)).isEqualTo(!upheld);
        assertThat(jdbc.queryForObject("SELECT hidden_by_admin FROM items WHERE id = ?", Boolean.class, offeredId))
                .isEqualTo(upheld);
    }

    private String requestStatus(long requestId) {
        return jdbc.queryForObject("SELECT status FROM requests WHERE id = ?", String.class, requestId);
    }

    private boolean isItemActive(long itemId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT is_active FROM items WHERE id = ?", Boolean.class, itemId));
    }

    private int trustStarsOf(long userId) {
        return jdbc.queryForObject("SELECT trust_stars FROM users WHERE id = ?", Integer.class, userId);
    }

    @Test
    void shipment_whenSameSenderPostsTwiceAtTheSameTime_exactlyOneSucceeds() throws Exception {
        // given
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long itemId = insertItem(donorId, "GIVE");
        long requestId = createGiveShippingRequest(itemId, donorId, receiverId);

        // when
        List<Integer> statuses =
                runAtTheSameTime(() -> shipStatus(requestId, donorId), () -> shipStatus(requestId, donorId));

        // then
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
    }

    @Test
    void jobs_whenShippingLegsOverdue_runAllShippingQueriesAndReceiveOnlyShippedLeg() throws Exception {
        // given: Cho tặng, chiều 1 đã gửi 8 ngày trước
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long itemId = insertItem(donorId, "GIVE");
        long requestId = createGiveShippingRequest(itemId, donorId, receiverId);
        ship(requestId, donorId);
        jdbc.update("UPDATE requests SET donor_shipped_at = now() - interval '8 days' WHERE id = ?", requestId);

        // when: chạy mọi việc nền của Giao hàng, các câu truy vấn đều phải chạy được trên PostgreSQL
        deadlineJobService.remindShipping(Instant.now());
        deadlineJobService.processExchangeShipping(Instant.now());
        deadlineJobService.cancelStaleConfirmed(Instant.now());
        int received = deadlineJobService.autoReceiveShippingLegs(Instant.now());

        // then
        assertThat(received).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM requests WHERE id = ?", String.class, requestId))
                .isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT auto_confirmed FROM requests WHERE id = ?", Boolean.class, requestId))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void dismissMissingShipmentReport_whenOneLegNotReceived_resumesWithoutCompleting(boolean swap) throws Exception {
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long itemId = insertItem(donorId, swap ? "SWAP" : "GIVE");
        long requestId;
        if (swap) {
            long offeredId = insertItem(receiverId, "SWAP");
            requestId = createSwapRequest(itemId, offeredId, donorId, receiverId);
            propose(requestId, receiverId, "12 Lê Lợi");
            mockMvc.perform(
                            post("/api/v1/requests/{id}/confirm-logistics", requestId)
                                    .header("Authorization", token(donorId))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            "{\"agreeToWaiver\":true,\"shippingAddress\":\"34 Trần Phú\",\"scheduleRevision\":1}"))
                    .andExpect(status().isOk());
            ship(requestId, receiverId);
            mockMvc.perform(post("/api/v1/requests/{id}/confirm-handover", requestId)
                            .header("Authorization", token(donorId)))
                    .andExpect(status().isOk());
        } else {
            requestId = createGiveShippingRequest(itemId, donorId, receiverId);
        }
        ship(requestId, donorId);
        mockMvc.perform(post("/api/v1/requests/{id}/handover-failed", requestId)
                        .header("Authorization", token(receiverId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"OTHER\",\"reason\":\"Tôi chưa nhận được kiện hàng\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPUTED"));
        long reportId = jdbc.queryForObject("SELECT id FROM reports WHERE request_id = ?", Long.class, requestId);
        jdbc.update("UPDATE reports SET response_due_at = now() - interval '1 hour' WHERE id = ?", reportId);
        long adminId = insertUser("Admin");

        mockMvc.perform(post("/api/v1/admin/reports/{id}/conclude", reportId)
                        .header("Authorization", "Bearer " + jwtService.issue(adminId, true))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"DISMISS\"}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT status FROM requests WHERE id = ?", String.class, requestId))
                .isEqualTo("AWAITING_HANDOVER");
        assertThat(jdbc.queryForObject("SELECT status FROM items WHERE id = ?", String.class, itemId))
                .isEqualTo("RESERVED");

        mockMvc.perform(post("/api/v1/requests/{id}/confirm-handover", requestId)
                        .header("Authorization", token(receiverId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void shippingReminderIncludesDeadlineHourInVietnam() throws Exception {
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long itemId = insertItem(donorId, "GIVE");
        long requestId = createGiveShippingRequest(itemId, donorId, receiverId);
        jdbc.update("UPDATE requests SET meeting_time = '2026-11-25 07:22:00+00' WHERE id = ?", requestId);

        deadlineJobService.remindShipping(Instant.parse("2026-11-26T00:00:00Z"));

        assertThat(jdbc.queryForObject(
                        "SELECT body FROM notifications WHERE user_id = ? AND type = 'SHIP_DATE_REMINDER'",
                        String.class,
                        donorId))
                .contains("14:22 28/11");
    }

    @Test
    void shipmentAcceptsLegacyBodyButNoticeOnlyAttributesDispatchToTheSender() throws Exception {
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long itemId = insertItem(donorId, "GIVE");
        long requestId = createGiveShippingRequest(itemId, donorId, receiverId);

        mockMvc.perform(post("/api/v1/requests/{id}/shipment", requestId)
                        .header("Authorization", token(donorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trackingCode\":\"LEGACY-CODE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.donorTrackingCode").doesNotHaveJsonPath());

        assertThat(jdbc.queryForObject(
                        "SELECT title FROM notifications WHERE user_id = ? AND type = 'SHIPMENT_SENT'",
                        String.class,
                        receiverId))
                .isEqualTo("Người gửi báo đã gửi hàng");
        assertThat(jdbc.queryForObject(
                        "SELECT body FROM notifications WHERE user_id = ? AND type = 'SHIPMENT_SENT'",
                        String.class,
                        receiverId))
                .contains("Người gửi báo đã gửi hàng", "tự ghi nhận")
                .doesNotContain("LEGACY-CODE");
    }

    @Test
    void jobs_whenNothingSentPastDeadlinePlusStaleDays_cancelsAndNotShippedRequestIsRemindedOnce() throws Exception {
        // given: ngày gửi dự kiến cách đây 4 ngày, đã quá hạn gửi 3 ngày
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long itemId = insertItem(donorId, "GIVE");
        long requestId = createGiveShippingRequest(itemId, donorId, receiverId);
        jdbc.update("UPDATE requests SET meeting_time = now() - interval '4 days' WHERE id = ?", requestId);

        // when: nhắc hai lần chỉ có một thông báo cho mỗi loại
        deadlineJobService.remindShipping(Instant.now());
        deadlineJobService.remindShipping(Instant.now());

        // then
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = 'SHIP_OVERDUE_REMINDER'",
                        Integer.class,
                        donorId))
                .isEqualTo(1);

        // when: qua 7 ngày thì tự huỷ
        jdbc.update("UPDATE requests SET meeting_time = now() - interval '7 days' WHERE id = ?", requestId);
        int cancelled = deadlineJobService.cancelStaleConfirmed(Instant.now());

        // then
        assertThat(cancelled).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM requests WHERE id = ?", String.class, requestId))
                .isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT status FROM items WHERE id = ?", String.class, itemId))
                .isEqualTo("APPROVED");
    }

    @Test
    void migration_whenOldShippingRequestHadMeetingPlace_copiesItToReceiverAddressColumnExists() {
        // then: cột mới có mặt và bảng vẫn là bảng requests (ERD 17 bảng)
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'requests'"
                                + " AND column_name IN ('receiver_address','donor_address','donor_shipped_at',"
                                + "'donor_tracking_code','receiver_shipped_at','receiver_tracking_code')",
                        Integer.class))
                .isEqualTo(6);
    }

    // ---------- helpers ----------

    private void propose(long requestId, long userId, String address) throws Exception {
        Instant sendDate = Instant.now().atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                .plusDays(2).with(LocalTime.of(10, 0)).toInstant();
        mockMvc.perform(post("/api/v1/requests/{id}/schedule", requestId)
                        .header("Authorization", token(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ScheduleRequest("SHIPPING", null, sendDate, true, address))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meetingPlace").doesNotExist());
    }

    private void ship(long requestId, long userId) throws Exception {
        Long offeredItemId = jdbc.queryForObject(
                "SELECT offered_item_id FROM requests WHERE id = ?", Long.class, requestId);
        var shipment = post("/api/v1/requests/{id}/shipment", requestId).header("Authorization", token(userId));
        if (offeredItemId != null) {
            jdbc.update("UPDATE requests SET meeting_time = now() + interval '10 minutes', "
                    + "exchange_shipping_window_start = now() - interval '20 minutes' WHERE id = ?", requestId);
            Long receiptId = jdbc.queryForObject("""
                    INSERT INTO media_files (storage_key, mime_type, size_bytes, created_by)
                    VALUES (?, 'image/jpeg', 4, ?) RETURNING id
                    """, Long.class, "test-shipping-receipt-" + SEQ.incrementAndGet(), userId);
            receiptIds.add(receiptId);
            shipment.contentType(MediaType.APPLICATION_JSON).content("{\"mediaIds\":[" + receiptId + "]}");
        }
        mockMvc.perform(shipment).andExpect(status().isOk());
    }

    private int shipStatus(long requestId, long userId) throws Exception {
        return mockMvc.perform(post("/api/v1/requests/{id}/shipment", requestId).header("Authorization", token(userId)))
                .andReturn()
                .getResponse()
                .getStatus();
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

    private long createSwapRequest(long itemId, long offeredId, long donorId, long receiverId) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/items/{itemId}/requests", itemId)
                        .header("Authorization", token(receiverId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeredItemId\":" + offeredId + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        long requestId = objectMapper
                .readTree(created.getResponse().getContentAsString())
                .get("id")
                .asLong();
        requestIds.add(requestId);
        mockMvc.perform(post("/api/v1/requests/{id}/select", requestId).header("Authorization", token(donorId)))
                .andExpect(status().isOk());
        return requestId;
    }

    private long createGiveShippingRequest(long itemId, long donorId, long receiverId) throws Exception {
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
        propose(requestId, receiverId, "12 Lê Lợi");
        mockMvc.perform(post("/api/v1/requests/{id}/confirm-logistics", requestId)
                        .header("Authorization", token(donorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduleRevision\":1,\"agreeToWaiver\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOGISTICS_CONFIRMED"));
        return requestId;
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
                "shipping-" + seq + "-" + System.nanoTime() + "@test.local",
                name,
                String.format("0994%06d", seq));
        userIds.add(userId);
        return userId;
    }

    private long insertItem(long donorId, String offerType) {
        long categoryId = jdbc.queryForObject("SELECT id FROM item_categories ORDER BY id LIMIT 1", Long.class);
        long areaId = jdbc.queryForObject("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1", Long.class);
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

    @Test
    void autoReceiveShipping_whenDisputeIsOpen_keepsShipmentAwaitingHandover() throws Exception {
        assertAutoReceiveShipping("AWAITING_RESPONSE", false, false);
    }

    @Test
    void autoReceiveShipping_whenDisputeIsClosedAndDeadlinePassed_completes() throws Exception {
        assertAutoReceiveShipping("DISMISSED", false, true);
    }

    @Test
    void autoReceiveShipping_whenRecentlyResumed_waitsUntilTheNewDeadline() throws Exception {
        assertAutoReceiveShipping("DISMISSED", true, false);
    }

    private void assertAutoReceiveShipping(String reportStatus, boolean resumedRecently, boolean shouldReceive)
            throws Exception {
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long itemId = insertItem(donorId, "GIVE");
        long requestId = createGiveShippingRequest(itemId, donorId, receiverId);
        ship(requestId, donorId);
        jdbc.update("UPDATE requests SET donor_shipped_at = now() - interval '8 days' WHERE id = ?", requestId);
        insertEarlierReport(receiverId, itemId, donorId, requestId, reportStatus);
        if (resumedRecently) {
            jdbc.update("UPDATE requests SET resumed_at = now() WHERE id = ?", requestId);
        }

        deadlineJobService.autoReceiveShippingLegs(Instant.now());

        assertThat(jdbc.queryForObject("SELECT status FROM requests WHERE id = ?", String.class, requestId))
                .isEqualTo(shouldReceive ? "COMPLETED" : "AWAITING_HANDOVER");
        if (resumedRecently) {
            MvcResult response = mockMvc.perform(
                            get("/api/v1/requests/{id}", requestId).header("Authorization", token(receiverId)))
                    .andExpect(status().isOk())
                    .andReturn();
            String deadline = objectMapper
                    .readTree(response.getResponse().getContentAsString())
                    .get("leg1AutoReceiveAt")
                    .asText();
            assertThat(Instant.parse(deadline)).isAfter(Instant.now());
        }
    }

    private void insertEarlierReport(long reporterId, long itemId, long targetId, long requestId, String status) {
        jdbc.update(
                "INSERT INTO reports (reporter_id, target_type, item_id, target_user_id, request_id,"
                        + " reason, description, stage, status) VALUES (?, 'REQUEST', ?, ?, ?, 'OTHER',"
                        + " 'Báo sự cố trước đó', 'AFTER', ?)",
                reporterId,
                itemId,
                targetId,
                requestId,
                status);
    }
}
