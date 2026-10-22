package com.shareloop.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.review.service.ReviewSlaService;
import com.shareloop.support.IntegrationTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Test ngưỡng SLA duyệt bài (FR-121, Issue #151) với PostgreSQL thật. Bài mẫu dùng mốc năm 2001 mà test khác không
 * ghi vào, nên số bài quá SLA trong các lượt đếm chỉ là bài của chính test; SLA lấy từ cấu hình đã seed (24 giờ).
 */
class ReviewSlaIT extends IntegrationTest {

    private static final Instant NOW = Instant.parse("2001-03-10T00:00:00Z");
    private static final int PHONE_BASE = ThreadLocalRandom.current().nextInt(10_000_000, 90_000_000);
    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();

    @Autowired
    ReviewSlaService reviewSlaService;

    @Autowired
    JdbcTemplate jdbc;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> itemIds = new ArrayList<>();
    private long admin;

    @BeforeEach
    void givenItemsAroundSla() {
        admin = insertUser(true);
        long donor = insertUser(false);
        // Chờ đúng 24 giờ: tính là quá SLA
        insertItem(donor, "PENDING_REVIEW", NOW.minus(Duration.ofHours(24)));
        // Chờ 23 giờ 59 phút: chưa quá SLA
        insertItem(donor, "PENDING_REVIEW", NOW.minus(Duration.ofHours(24)).plus(Duration.ofMinutes(1)));
        // Đã duyệt từ lâu: không nằm trong hàng chờ
        insertItem(donor, "APPROVED", NOW.minus(Duration.ofHours(48)));
    }

    // Thứ tự xoá: notifications và items tham chiếu users.
    @AfterEach
    void cleanUp() {
        for (Long userId : userIds) {
            jdbc.update("DELETE FROM notifications WHERE user_id = ?", userId);
        }
        for (Long itemId : itemIds) {
            jdbc.update("DELETE FROM items WHERE id = ?", itemId);
        }
        for (Long userId : userIds) {
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
        itemIds.clear();
        userIds.clear();
    }

    @Test
    void alertOverdueReviews_itemWaitingExactlySlaHours_isCountedAndAdminNotifiedOnce() {
        long overdue = reviewSlaService.alertOverdueReviews(NOW);

        assertThat(overdue).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT body FROM notifications WHERE user_id = ? AND type = 'REVIEW_SLA_OVERDUE'",
                        String.class,
                        admin))
                .isEqualTo("1 bài đã chờ duyệt từ 24 giờ trở lên. Vui lòng xử lý hàng chờ duyệt.");
    }

    @Test
    void alertOverdueReviews_onlyItemsBelowSla_sendsNothing() {
        // Lùi mốc 1 phút: bài "đúng 24 giờ" mới chờ 23 giờ 59 phút, không còn bài nào quá SLA
        long overdue = reviewSlaService.alertOverdueReviews(NOW.minus(Duration.ofMinutes(1)));

        assertThat(overdue).isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = 'REVIEW_SLA_OVERDUE'",
                        Integer.class,
                        admin))
                .isZero();
    }

    private long insertUser(boolean isAdmin) {
        Long id = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name, phone, status, is_admin)"
                        + " VALUES (?, 'not-a-real-hash', 'Review SLA IT', ?, 'ACTIVE', ?) RETURNING id",
                Long.class,
                "review-sla-it-" + UUID.randomUUID() + "@test.local",
                "03" + (PHONE_BASE + PHONE_SEQUENCE.incrementAndGet()),
                isAdmin);
        userIds.add(id);
        return id;
    }

    private void insertItem(long donorId, String status, Instant submittedAt) {
        Long categoryId =
                jdbc.queryForObject("SELECT id FROM item_categories WHERE is_active ORDER BY id LIMIT 1", Long.class);
        Long areaId = jdbc.queryForObject("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1", Long.class);
        Long id = jdbc.queryForObject(
                "INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, condition, status,"
                        + " created_at, updated_at) VALUES (?, ?, ?, 'GIVE', 'Review SLA IT', 'Mô tả', 'GOOD', ?, ?, ?)"
                        + " RETURNING id",
                Long.class,
                donorId,
                categoryId,
                areaId,
                status,
                Timestamp.from(submittedAt),
                Timestamp.from(submittedAt));
        itemIds.add(id);
    }
}
