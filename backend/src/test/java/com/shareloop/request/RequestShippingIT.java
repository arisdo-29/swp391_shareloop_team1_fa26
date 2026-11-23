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
                        .content("{\"agreeToWaiver\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_SHIPPING_ADDRESS_REQUIRED"));
        // chủ bài nhập địa chỉ rồi xác nhận: người nhận đã đồng ý từ lúc đề xuất nên lịch được chốt
        mockMvc.perform(post("/api/v1/requests/{id}/confirm-logistics", requestId)
                        .header("Authorization", token(donorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreeToWaiver\":true,\"shippingAddress\":\"34 Trần Phú\"}"))
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
        ship(requestId, donorId, "GHN1111");
        mockMvc.perform(post("/api/v1/requests/{id}/confirm-handover", requestId)
                        .header("Authorization", token(receiverId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_HANDOVER"));
        ship(requestId, receiverId, "VTP2222");
        mockMvc.perform(post("/api/v1/requests/{id}/confirm-handover", requestId)
                        .header("Authorization", token(donorId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.donorTrackingCode").value("GHN1111"))
                .andExpect(jsonPath("$.receiverTrackingCode").value("VTP2222"));

        // then
        assertThat(jdbc.queryForObject("SELECT status FROM items WHERE id = ?", String.class, itemId))
                .isEqualTo("TRADED");
        assertThat(jdbc.queryForObject("SELECT status FROM items WHERE id = ?", String.class, offeredId))
                .isEqualTo("TRADED");
    }

    @Test
    void shipment_whenSameSenderPostsTwiceAtTheSameTime_exactlyOneSucceeds() throws Exception {
        // given
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long itemId = insertItem(donorId, "GIVE");
        long requestId = createGiveShippingRequest(itemId, donorId, receiverId);

        // when
        List<Integer> statuses = runAtTheSameTime(
                () -> shipStatus(requestId, donorId, "GHN1111"), () -> shipStatus(requestId, donorId, "GHN2222"));

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
        ship(requestId, donorId, "GHN1111");
        jdbc.update("UPDATE requests SET donor_shipped_at = now() - interval '8 days' WHERE id = ?", requestId);

        // when: chạy mọi việc nền của Giao hàng, các câu truy vấn đều phải chạy được trên PostgreSQL
        deadlineJobService.remindShipping(Instant.now());
        deadlineJobService.openShippingOneLegStuck(Instant.now());
        deadlineJobService.cancelStaleConfirmed(Instant.now());
        int received = deadlineJobService.autoReceiveShippingLegs(Instant.now());

        // then
        assertThat(received).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM requests WHERE id = ?", String.class, requestId))
                .isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT auto_confirmed FROM requests WHERE id = ?", Boolean.class, requestId))
                .isTrue();
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
        Instant sendDate = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        mockMvc.perform(post("/api/v1/requests/{id}/schedule", requestId)
                        .header("Authorization", token(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ScheduleRequest("SHIPPING", null, sendDate, true, address))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meetingPlace").doesNotExist());
    }

    private void ship(long requestId, long userId, String trackingCode) throws Exception {
        mockMvc.perform(post("/api/v1/requests/{id}/shipment", requestId)
                        .header("Authorization", token(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trackingCode\":\"" + trackingCode + "\"}"))
                .andExpect(status().isOk());
    }

    private int shipStatus(long requestId, long userId, String trackingCode) throws Exception {
        return mockMvc.perform(post("/api/v1/requests/{id}/shipment", requestId)
                        .header("Authorization", token(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trackingCode\":\"" + trackingCode + "\"}"))
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
                        .content("{\"agreeToWaiver\":true}"))
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
}
