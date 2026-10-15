package com.shareloop.listingfee;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.listingfee.service.ItemExpiryService;
import com.shareloop.support.IntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Test job hết hạn bài (FR-29, BR-D03) trên PostgreSQL thật. Gọi thẳng service với mốc {@code now} tự chọn (profile
 * test tắt app.jobs.enabled nên job không tự chạy). DB dùng chung với test khác nên chỉ kiểm các bài do test này tạo,
 * không kiểm tổng số bài của cả lượt.
 */
class ItemExpiryServiceIT extends IntegrationTest {

    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();

    // Mốc updated_at cũ đặt sẵn khi insert: trigger set_updated_at đổi cột này ở MỌI câu UPDATE, nên còn giữ nguyên
    // nghĩa là job không ghi gì vào dòng đó.
    private static final Instant OLD_UPDATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    ItemExpiryService itemExpiryService;

    @Autowired
    JdbcTemplate jdbc;

    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long userId : createdUserIds) {
            jdbc.update("DELETE FROM items WHERE donor_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
        createdUserIds.clear();
    }

    @Test
    void expireOverdueItems_whenApprovedAndExpireAtPassed_marksExpired() {
        // given
        Instant now = Instant.now();
        long itemId = insertItem(insertUser(), "APPROVED", now.minus(1, ChronoUnit.HOURS));

        // when
        int expiredCount = itemExpiryService.expireOverdueItems(now);

        // then
        assertThat(statusOf(itemId)).isEqualTo("EXPIRED");
        assertThat(expiredCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    void expireOverdueItems_whenExpireAtInFuture_keepsApproved() {
        // given
        Instant now = Instant.now();
        long itemId = insertItem(insertUser(), "APPROVED", now.plus(1, ChronoUnit.HOURS));

        // when
        itemExpiryService.expireOverdueItems(now);

        // then
        assertThat(statusOf(itemId)).isEqualTo("APPROVED");
        assertThat(updatedAtOf(itemId)).isEqualTo(OLD_UPDATED_AT);
    }

    @Test
    void expireOverdueItems_whenAlreadyExpired_leavesRowUntouched() {
        // given
        Instant now = Instant.now();
        long itemId = insertItem(insertUser(), "EXPIRED", now.minus(3, ChronoUnit.DAYS));

        // when
        itemExpiryService.expireOverdueItems(now);

        // then
        assertThat(statusOf(itemId)).isEqualTo("EXPIRED");
        assertThat(updatedAtOf(itemId)).isEqualTo(OLD_UPDATED_AT);
    }

    @Test
    void expireOverdueItems_whenReservedOrPendingReviewPastExpireAt_keepsStatus() {
        // given: bài đang giao dịch và bài có bản sửa chờ duyệt lại, cả hai đã quá expire_at
        Instant now = Instant.now();
        long donorId = insertUser();
        long reservedItemId = insertItem(donorId, "RESERVED", now.minus(1, ChronoUnit.DAYS));
        long pendingReviewItemId = insertItem(donorId, "PENDING_REVIEW", now.minus(1, ChronoUnit.DAYS));

        // when
        itemExpiryService.expireOverdueItems(now);

        // then: chỉ bài APPROVED mới hết hạn (ERD mục 6); hai bài này xử lý khi quay về APPROVED
        assertThat(statusOf(reservedItemId)).isEqualTo("RESERVED");
        assertThat(statusOf(pendingReviewItemId)).isEqualTo("PENDING_REVIEW");
    }

    @Test
    void expireOverdueItems_whenRunTwice_secondRunChangesNothing() {
        // given: lượt đầu đã chuyển bài sang EXPIRED
        Instant now = Instant.now();
        long itemId = insertItem(insertUser(), "APPROVED", now.minus(1, ChronoUnit.HOURS));
        itemExpiryService.expireOverdueItems(now);
        Instant updatedAtAfterFirstRun = updatedAtOf(itemId);

        // when
        itemExpiryService.expireOverdueItems(now);

        // then: lượt hai không chọn lại bài này, không có câu UPDATE nào vào dòng
        assertThat(statusOf(itemId)).isEqualTo("EXPIRED");
        assertThat(updatedAtOf(itemId)).isEqualTo(updatedAtAfterFirstRun);
    }

    // ---------- tiện ích ----------

    private long insertUser() {
        // Email và phone có unique index nên mỗi user một giá trị riêng; đầu số 0994 khác các test khác.
        int sequence = PHONE_SEQUENCE.incrementAndGet();
        String email = "expiry-it-" + sequence + "-" + System.nanoTime() + "@test.local";
        String phone = String.format("0994%06d", sequence);
        Long userId = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name, phone, status, credit_balance, held_credit)"
                        + " VALUES (?, 'not-a-real-hash', 'Expiry IT', ?, 'ACTIVE', 0, 0) RETURNING id",
                Long.class,
                email,
                phone);
        createdUserIds.add(userId);
        return userId;
    }

    private long insertItem(long donorId, String status, Instant expireAt) {
        return jdbc.queryForObject(
                "INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, condition,"
                        + " status, post_fee_paid, fee_state, pending_fee, pending_fee_type, expire_at, updated_at)"
                        + " VALUES (?, ?, ?, 'GIVE', 'Expiry IT item', 'Mô tả', 'NEW', ?, true, 'CHARGED', 5, 'POST',"
                        + " ?, ?) RETURNING id",
                Long.class,
                donorId,
                seedCategoryId(),
                seedAreaId(),
                status,
                Timestamp.from(expireAt),
                Timestamp.from(OLD_UPDATED_AT));
    }

    // Mượn danh mục và khu vực có sẵn từ seed (không tạo thêm, không xoá) để thoả khoá ngoại của items.
    private long seedCategoryId() {
        return jdbc.queryForObject("SELECT id FROM item_categories ORDER BY id LIMIT 1", Long.class);
    }

    private long seedAreaId() {
        return jdbc.queryForObject("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1", Long.class);
    }

    private String statusOf(long itemId) {
        return jdbc.queryForObject("SELECT status FROM items WHERE id = ?", String.class, itemId);
    }

    // Đọc qua Timestamp vì driver PostgreSQL không đổi thẳng TIMESTAMPTZ sang Instant.
    private Instant updatedAtOf(long itemId) {
        return jdbc.queryForObject("SELECT updated_at FROM items WHERE id = ?", Timestamp.class, itemId)
                .toInstant();
    }
}
