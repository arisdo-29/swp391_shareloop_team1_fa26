package com.shareloop.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.notification.event.RealtimeUpdateEvent;
import com.shareloop.request.dto.ConfirmLogisticsRequest;
import com.shareloop.request.dto.ScheduleRequest;
import com.shareloop.request.service.RequestDeadlineJobService;
import com.shareloop.support.IntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/** Giới hạn giờ hẹn, dời lịch và việc nền tự huỷ giao dịch kẹt (GN-11, GN-12) chạy thật trên PostgreSQL. */
@AutoConfigureMockMvc
@RecordApplicationEvents
class RequestRescheduleIT extends IntegrationTest {
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
    ApplicationEvents applicationEvents;

    private final List<Long> requestIds = new ArrayList<>();
    private final List<Long> itemIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long requestId : requestIds) {
            jdbc.update("DELETE FROM reports WHERE request_id = ?", requestId);
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
    void schedule_whenMeetingTimeIsEightDaysAhead_returnsBadRequest() throws Exception {
        // given
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long itemId = insertItem(donorId);
        long requestId = createReservedRequest(itemId, donorId, receiverId);
        Instant tooFar = Instant.now().plus(8, ChronoUnit.DAYS);

        // when / then
        mockMvc.perform(post("/api/v1/requests/{id}/schedule", requestId)
                        .header("Authorization", token(donorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ScheduleRequest("IN_PERSON", "FPT Park", tooFar, true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_SCHEDULE_TOO_FAR"));
    }

    @Test
    void confirmLogistics_requiresTheLatestProposalRevision() throws Exception {
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long requestId = createReservedRequest(insertItem(donorId), donorId, receiverId);
        Instant meetingTime = Instant.now().plus(1, ChronoUnit.DAYS);
        for (String place : List.of("Cổng trường", "Thư viện")) {
            mockMvc.perform(post("/api/v1/requests/{id}/schedule", requestId)
                            .header("Authorization", token(donorId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new ScheduleRequest("IN_PERSON", place, meetingTime, true))))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                "/api/v1/requests/{id}", requestId)
                        .header("Authorization", token(receiverId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleRevision").value(2))
                .andExpect(jsonPath("$.meetingPlace").value("Thư viện"));

        for (String confirmation :
                List.of("{\"agreeToWaiver\":true}", "{\"agreeToWaiver\":true,\"scheduleRevision\":1}")) {
            mockMvc.perform(post("/api/v1/requests/{id}/confirm-logistics", requestId)
                            .header("Authorization", token(receiverId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(confirmation))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("REQUEST_SCHEDULE_CHANGED"));
            assertThat(statusOf(requestId)).isEqualTo("AWAITING_LOGISTICS");
        }

        mockMvc.perform(post("/api/v1/requests/{id}/confirm-logistics", requestId)
                        .header("Authorization", token(receiverId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreeToWaiver\":true,\"scheduleRevision\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOGISTICS_CONFIRMED"))
                .andExpect(jsonPath("$.meetingPlace").value("Thư viện"));
    }

    @Test
    void confirmLogistics_withChangedAddress_updatesOtherPartyWhileWaitingForConfirmation() throws Exception {
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long requestId = createReservedRequest(insertItem(donorId), donorId, receiverId);
        Instant shippingTime = Instant.now().plus(1, ChronoUnit.DAYS);
        mockMvc.perform(post("/api/v1/requests/{id}/schedule", requestId)
                        .header("Authorization", token(receiverId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ScheduleRequest("SHIPPING", null, shippingTime, true, "12 Lê Lợi"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/requests/{id}/schedule", requestId)
                        .header("Authorization", token(donorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ScheduleRequest("SHIPPING", null, shippingTime, true))))
                .andExpect(status().isOk());
        applicationEvents.clear();

        mockMvc.perform(post("/api/v1/requests/{id}/confirm-logistics", requestId)
                        .header("Authorization", token(receiverId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConfirmLogisticsRequest(true, "14 Lê Lợi", 2L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleRevision").value(3))
                .andExpect(jsonPath("$.status").value("AWAITING_LOGISTICS"));

        assertThat(applicationEvents.stream(RealtimeUpdateEvent.class))
                .contains(new RealtimeUpdateEvent(donorId, "REQUEST", requestId));
    }

    @Test
    void reschedule_whenAccepted_changesMeetingTimeAndRaisesCounterInResponse() throws Exception {
        // given
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long itemId = insertItem(donorId);
        long requestId = createConfirmedRequest(itemId, donorId, receiverId);
        Instant newTime = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        mockMvc.perform(post("/api/v1/requests/{id}/reschedule", requestId)
                        .header("Authorization", token(donorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"meetingTime\":\"" + newTime + "\",\"meetingPlace\":\"Thư viện\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rescheduleProposedBy").value(donorId))
                .andExpect(jsonPath("$.rescheduleExpiresAt").isNotEmpty());

        // when
        mockMvc.perform(post("/api/v1/requests/{id}/reschedule/accept", requestId)
                        .header("Authorization", token(receiverId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rescheduleCount").value(1))
                .andExpect(jsonPath("$.meetingPlace").value("Thư viện"))
                .andExpect(jsonPath("$.status").value("LOGISTICS_CONFIRMED"));

        // then
        assertThat(jdbc.queryForObject("SELECT reschedule_count FROM requests WHERE id = ?", Integer.class, requestId))
                .isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"IN_PERSON, false", "IN_PERSON, true", "SHIPPING, false", "SHIPPING, true"})
    void confirmLogistics_afterEitherDeadline_rejectsBeforeBackgroundJobRuns(
            String deliveryMethod, boolean reservationExpired) throws Exception {
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long requestId = createReservedRequest(insertItem(donorId), donorId, receiverId);
        Instant now = Instant.now();
        mockMvc.perform(post("/api/v1/requests/{id}/schedule", requestId)
                        .header("Authorization", token(donorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ScheduleRequest(
                                deliveryMethod, "Cổng trường", now.plus(1, ChronoUnit.DAYS), true))))
                .andExpect(status().isOk());
        if (reservationExpired) {
            jdbc.update("UPDATE requests SET reserved_at = now() - interval '40 days' WHERE id = ?", requestId);
        } else {
            jdbc.update("UPDATE requests SET meeting_time = now() - interval '1 hour' WHERE id = ?", requestId);
        }

        MvcResult response = mockMvc.perform(
                        get("/api/v1/requests/{id}", requestId).header("Authorization", token(receiverId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_LOGISTICS"))
                .andReturn();
        String elapsedField = reservationExpired ? "autoCancelAt" : "meetingTime";
        String elapsedAt = objectMapper
                .readTree(response.getResponse().getContentAsString())
                .get(elapsedField)
                .asText();
        assertThat(Instant.parse(elapsedAt)).isBefore(now);

        mockMvc.perform(post("/api/v1/requests/{id}/confirm-logistics", requestId)
                        .header("Authorization", token(receiverId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreeToWaiver\":true,\"shippingAddress\":\"12 Lê Lợi\",\"scheduleRevision\":1}"))
                .andExpect(reservationExpired ? status().isConflict() : status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value(reservationExpired ? "REQUEST_SCHEDULE_WINDOW_EXPIRED" : "REQUEST_SCHEDULE_PAST_TIME"));
        assertThat(statusOf(requestId)).isEqualTo("AWAITING_LOGISTICS");
        assertThat(jdbc.queryForObject(
                        "SELECT contact_revealed_at IS NULL FROM requests WHERE id = ?", Boolean.class, requestId))
                .isTrue();
    }

    @Test
    void reschedule_whenReplyDeadlinePassed_keepsOriginalSchedule() throws Exception {
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long requestId = createConfirmedRequest(insertItem(donorId), donorId, receiverId);
        proposeLaterMeeting(requestId, donorId);
        jdbc.update("UPDATE requests SET reschedule_proposed_at = now() - interval '25 hours' WHERE id = ?", requestId);

        assertRescheduleUnavailable(requestId, receiverId);
    }

    @Test
    void reschedule_whenProposedTimePassed_keepsOriginalSchedule() throws Exception {
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long requestId = createConfirmedRequest(insertItem(donorId), donorId, receiverId);
        proposeLaterMeeting(requestId, donorId);
        jdbc.update("UPDATE requests SET reschedule_meeting_time = now() - interval '1 hour' WHERE id = ?", requestId);

        assertRescheduleUnavailable(requestId, receiverId);
    }

    private void proposeLaterMeeting(long requestId, long donorId) throws Exception {
        Instant newTime = Instant.now().plus(3, ChronoUnit.DAYS);
        mockMvc.perform(post("/api/v1/requests/{id}/reschedule", requestId)
                        .header("Authorization", token(donorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"meetingTime\":\"" + newTime + "\",\"meetingPlace\":\"Thư viện\"}"))
                .andExpect(status().isOk());
    }

    private void assertRescheduleUnavailable(long requestId, long receiverId) throws Exception {
        mockMvc.perform(post("/api/v1/requests/{id}/reschedule/accept", requestId)
                        .header("Authorization", token(receiverId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_RESCHEDULE_EXPIRED"));
        assertThat(jdbc.queryForObject("SELECT meeting_place FROM requests WHERE id = ?", String.class, requestId))
                .isEqualTo("FPT Park");
        assertThat(jdbc.queryForObject("SELECT reschedule_count FROM requests WHERE id = ?", Integer.class, requestId))
                .isZero();
    }

    @Test
    void expireReschedules_whenResponseHoursPassed_keepsOldSchedule() throws Exception {
        // given
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long itemId = insertItem(donorId);
        long requestId = createConfirmedRequest(itemId, donorId, receiverId);
        mockMvc.perform(post("/api/v1/requests/{id}/reschedule", requestId)
                        .header("Authorization", token(donorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"meetingTime\":\"" + Instant.now().plus(3, ChronoUnit.DAYS) + "\"}"))
                .andExpect(status().isOk());
        jdbc.update("UPDATE requests SET reschedule_proposed_at = now() - interval '25 hours' WHERE id = ?", requestId);
        applicationEvents.clear();

        // when
        int expired = deadlineJobService.expireReschedules(Instant.now());

        // then
        assertThat(expired).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT reschedule_proposed_by FROM requests WHERE id = ?", Long.class, requestId))
                .isNull();
        assertThat(jdbc.queryForObject("SELECT reschedule_count FROM requests WHERE id = ?", Integer.class, requestId))
                .isZero();
        assertThat(applicationEvents.stream(RealtimeUpdateEvent.class))
                .contains(new RealtimeUpdateEvent(receiverId, "REQUEST", requestId));
    }

    @Test
    void cancelStaleConfirmed_whenThreeDaysPastMeeting_cancelsReopensItemAndSendsReminderBefore() throws Exception {
        // given
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long itemId = insertItem(donorId);
        long requestId = createConfirmedRequest(itemId, donorId, receiverId);
        jdbc.update("UPDATE requests SET meeting_time = now() - interval '30 hours' WHERE id = ?", requestId);

        // when: 30 giờ sau giờ hẹn thì chỉ nhắc, chạy hai lần vẫn một thông báo cho mỗi bên
        deadlineJobService.cancelStaleConfirmed(Instant.now());
        deadlineJobService.remindStaleConfirmed(Instant.now());
        deadlineJobService.remindStaleConfirmed(Instant.now());

        // then
        assertThat(statusOf(requestId)).isEqualTo("LOGISTICS_CONFIRMED");
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = 'HANDOVER_STALE_REMINDER'",
                        Integer.class,
                        donorId))
                .isEqualTo(1);

        // when: qua 4 ngày thì tự huỷ
        jdbc.update("UPDATE requests SET meeting_time = now() - interval '4 days' WHERE id = ?", requestId);
        int cancelled = deadlineJobService.cancelStaleConfirmed(Instant.now());

        // then
        assertThat(cancelled).isGreaterThanOrEqualTo(1);
        assertThat(statusOf(requestId)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT status FROM items WHERE id = ?", String.class, itemId))
                .isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT trust_stars FROM users WHERE id = ?", Integer.class, donorId))
                .isEqualTo(
                        jdbc.queryForObject("SELECT trust_stars FROM users WHERE id = ?", Integer.class, receiverId));
    }

    @Test
    void remindDeadlines_whenMeetingWithin24Hours_runsAllReminderQueriesAndSendsMeetingReminderOnce() throws Exception {
        // given: giờ hẹn sau 20 giờ
        long donorId = insertUser("Donor");
        long receiverId = insertUser("Receiver");
        long itemId = insertItem(donorId);
        long requestId = createConfirmedRequest(itemId, donorId, receiverId);
        jdbc.update("UPDATE requests SET meeting_time = now() + interval '20 hours' WHERE id = ?", requestId);

        // when: chạy hai lần, mọi câu truy vấn nhắc hạn đều phải chạy được trên PostgreSQL
        deadlineJobService.remindDeadlines(Instant.now());
        deadlineJobService.remindDeadlines(Instant.now());

        // then
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = 'MEETING_REMINDER'",
                        Integer.class,
                        donorId))
                .isEqualTo(1);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                "/api/v1/requests/{id}", requestId)
                        .header("Authorization", token(donorId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autoCancelAt").isNotEmpty())
                .andExpect(jsonPath("$.autoConfirmAt").doesNotExist());
    }

    private long createReservedRequest(long itemId, long donorId, long receiverId) throws Exception {
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
        return requestId;
    }

    private long createConfirmedRequest(long itemId, long donorId, long receiverId) throws Exception {
        long requestId = createReservedRequest(itemId, donorId, receiverId);
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
                "reschedule-" + seq + "-" + System.nanoTime() + "@test.local",
                name,
                String.format("0995%06d", seq));
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
