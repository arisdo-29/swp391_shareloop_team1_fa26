package com.shareloop.request.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.support.IntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Integration test cho RequestJobService trên PostgreSQL thật (Milestone R4, Issue #109).
 */
class RequestJobServiceIT extends IntegrationTest {

    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();

    @Autowired
    RequestJobService requestJobService;

    @Autowired
    JdbcTemplate jdbc;

    private final List<Long> createdRequestIds = new ArrayList<>();
    private final List<Long> createdItemIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long requestId : createdRequestIds) {
            jdbc.update("DELETE FROM activity_logs WHERE target_type = 'REQUEST' AND target_id = ?", requestId);
            jdbc.update("DELETE FROM requests WHERE id = ?", requestId);
        }
        createdRequestIds.clear();

        for (Long itemId : createdItemIds) {
            jdbc.update("DELETE FROM items WHERE id = ?", itemId);
        }
        createdItemIds.clear();

        for (Long userId : createdUserIds) {
            jdbc.update("DELETE FROM activity_logs WHERE actor_id = ?", userId);
            jdbc.update("DELETE FROM notifications WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
        createdUserIds.clear();
    }

    @Test
    @DisplayName("expirePendingRequests: Quá 3 ngày PENDING thì tự chuyển CANCELLED")
    void expirePendingRequests_whenPendingOverdue_cancelsRequest() {
        Instant now = Instant.now();
        long donorId = insertUser();
        long receiverId = insertUser();
        long itemId = insertItem(donorId, "APPROVED");

        // Request tạo 4 ngày trước (quá hạn 3 ngày)
        long requestId = insertRequest(itemId, receiverId, "PENDING", now.minus(4, ChronoUnit.DAYS), null, null, null);

        int count = requestJobService.expirePendingRequests(now);

        assertThat(count).isGreaterThanOrEqualTo(1);
        assertThat(requestStatusOf(requestId)).isEqualTo("CANCELLED");
        assertThat(cancelReasonOf(requestId)).contains("Hết hạn");
    }

    @Test
    @DisplayName(
            "expireSilentReservedRequests: Quá 10 ngày RESERVED chưa chốt lịch thì huỷ request và nhả bài về APPROVED")
    void expireSilentReservedRequests_whenReservedOverdue_cancelsRequestAndReleasesItem() {
        Instant now = Instant.now();
        long donorId = insertUser();
        long receiverId = insertUser();
        long itemId = insertItem(donorId, "RESERVED");

        // Request chọn người nhận 11 ngày trước (quá hạn 10 ngày)
        long requestId = insertRequest(
                itemId,
                receiverId,
                "RESERVED",
                now.minus(12, ChronoUnit.DAYS),
                now.minus(11, ChronoUnit.DAYS),
                null,
                null);

        int count = requestJobService.expireSilentReservedRequests(now);

        assertThat(count).isGreaterThanOrEqualTo(1);
        assertThat(requestStatusOf(requestId)).isEqualTo("CANCELLED");
        assertThat(itemStatusOf(itemId)).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName(
            "autoConfirmHandoverRequests: Donor xác nhận quá 3 ngày, receiver im lặng -> tự COMPLETED và item sang TRADED")
    void autoConfirmHandoverRequests_whenHandoverOverdue_autoCompletesAndMarksItemTraded() {
        Instant now = Instant.now();
        long donorId = insertUser();
        long receiverId = insertUser();
        long itemId = insertItem(donorId, "RESERVED");

        // Request ở AWAITING_HANDOVER, donor xác nhận 4 ngày trước
        long requestId = insertRequest(
                itemId,
                receiverId,
                "AWAITING_HANDOVER",
                now.minus(6, ChronoUnit.DAYS),
                now.minus(5, ChronoUnit.DAYS),
                now.minus(4, ChronoUnit.DAYS),
                null);

        int count = requestJobService.autoConfirmHandoverRequests(now);

        assertThat(count).isGreaterThanOrEqualTo(1);
        assertThat(requestStatusOf(requestId)).isEqualTo("COMPLETED");
        assertThat(isAutoConfirmed(requestId)).isTrue();
        assertThat(itemStatusOf(itemId)).isEqualTo("TRADED");

        Integer logCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM activity_logs WHERE target_type = 'REQUEST' AND target_id = ? AND type = 'POINTS'",
                Integer.class,
                requestId);
        assertThat(logCount).isGreaterThanOrEqualTo(1);
    }

    // ---------- Tiện ích hỗ trợ test ----------

    private long insertUser() {
        int seq = PHONE_SEQUENCE.incrementAndGet();
        String email = "req-job-it-" + seq + "-" + System.nanoTime() + "@test.local";
        String phone = String.format("0995%06d", seq);
        Long userId = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name, phone, status, credit_balance, held_credit)"
                        + " VALUES (?, 'hash', 'Job IT User', ?, 'ACTIVE', 0, 0) RETURNING id",
                Long.class,
                email,
                phone);
        createdUserIds.add(userId);
        return userId;
    }

    private long insertItem(long donorId, String status) {
        Long itemId = jdbc.queryForObject(
                "INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, condition, status)"
                        + " VALUES (?, ?, ?, 'GIVE', 'Job IT Item', 'Mô tả', 'NEW', ?) RETURNING id",
                Long.class,
                donorId,
                seedCategoryId(),
                seedAreaId(),
                status);
        createdItemIds.add(itemId);
        return itemId;
    }

    private long insertRequest(
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

    private String requestStatusOf(long requestId) {
        return jdbc.queryForObject("SELECT status FROM requests WHERE id = ?", String.class, requestId);
    }

    private String cancelReasonOf(long requestId) {
        return jdbc.queryForObject("SELECT cancel_reason FROM requests WHERE id = ?", String.class, requestId);
    }

    private boolean isAutoConfirmed(long requestId) {
        Boolean value =
                jdbc.queryForObject("SELECT auto_confirmed FROM requests WHERE id = ?", Boolean.class, requestId);
        return Boolean.TRUE.equals(value);
    }

    private String itemStatusOf(long itemId) {
        return jdbc.queryForObject("SELECT status FROM items WHERE id = ?", String.class, itemId);
    }

    private long seedCategoryId() {
        return jdbc.queryForObject("SELECT id FROM item_categories ORDER BY id LIMIT 1", Long.class);
    }

    private long seedAreaId() {
        return jdbc.queryForObject("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1", Long.class);
    }
}
