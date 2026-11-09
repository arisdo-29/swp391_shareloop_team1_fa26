package com.shareloop.review.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.review.repository.ReviewQueueRepository;
import com.shareloop.support.IntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/** KD-06: kiểm câu SQL xếp hàng chờ duyệt với người đăng mới (số bài đã từng được duyệt không quá ngưỡng). */
@Transactional
class ReviewQueueNewcomerOrderIT extends IntegrationTest {

    private static final int NEWCOMER_MAX_APPROVED = 3;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    ReviewQueueRepository queueRepository;

    private final Instant now = Instant.now();

    @Test
    void findPendingReviewQueue_whenSameTrustStars_newcomerComesBeforeOlderVeteranPost() {
        // given: người kỳ cựu có 4 bài đã duyệt, bài chờ duyệt của họ đăng sớm hơn
        long veteranId = createUser(5);
        createApprovedItems(veteranId, NEWCOMER_MAX_APPROVED + 1);
        long veteranPendingId = createPendingItem(veteranId, now.minus(5, ChronoUnit.HOURS));
        // và người mới có đúng 3 bài đã duyệt (đúng ngưỡng vẫn là người mới), bài chờ đăng muộn hơn
        long newcomerId = createUser(5);
        createApprovedItems(newcomerId, NEWCOMER_MAX_APPROVED);
        long newcomerPendingId = createPendingItem(newcomerId, now.minus(1, ChronoUnit.HOURS));

        // when
        List<Long> queue = queueIds(veteranPendingId, newcomerPendingId);

        // then
        assertThat(queue).containsExactly(newcomerPendingId, veteranPendingId);
    }

    @Test
    void findPendingReviewQueue_whenVeteranHasFewerTrustStars_veteranStillComesFirst() {
        // given: sao uy tín vẫn là tiêu chí đầu tiên, nên người ít sao đứng trước dù không phải người mới
        long veteranId = createUser(2);
        createApprovedItems(veteranId, NEWCOMER_MAX_APPROVED + 1);
        long veteranPendingId = createPendingItem(veteranId, now.minus(1, ChronoUnit.HOURS));
        long newcomerId = createUser(5);
        long newcomerPendingId = createPendingItem(newcomerId, now.minus(5, ChronoUnit.HOURS));

        // when
        List<Long> queue = queueIds(veteranPendingId, newcomerPendingId);

        // then
        assertThat(queue).containsExactly(veteranPendingId, newcomerPendingId);
    }

    // Lấy thứ tự hàng chờ rồi chỉ giữ hai bài của test, vì database test có thể còn bài chờ duyệt khác.
    private List<Long> queueIds(long firstId, long secondId) {
        return queueRepository.findPendingReviewQueue(PageRequest.of(0, 100), NEWCOMER_MAX_APPROVED).stream()
                .map(row -> ((Number) row[0]).longValue())
                .filter(id -> id == firstId || id == secondId)
                .toList();
    }

    private long createUser(int trustStars) {
        String suffix =
                String.format("%08d", Integer.toUnsignedLong(UUID.randomUUID().hashCode()) % 100000000);
        return jdbc.sql("INSERT INTO users (email, password_hash, full_name, phone, status, trust_stars) "
                        + "VALUES (:email, 'unused', 'Queue Test', :phone, 'ACTIVE', :trustStars) RETURNING id")
                .param("email", "queue-" + UUID.randomUUID() + "@shareloop.local")
                .param("phone", "09" + suffix)
                .param("trustStars", trustStars)
                .query(Long.class)
                .single();
    }

    private void createApprovedItems(long donorId, int count) {
        for (int index = 0; index < count; index++) {
            insertItem(donorId, "APPROVED", now.minus(10, ChronoUnit.DAYS), now.minus(9, ChronoUnit.DAYS));
        }
    }

    private long createPendingItem(long donorId, Instant createdAt) {
        return insertItem(donorId, "PENDING_REVIEW", createdAt, null);
    }

    private long insertItem(long donorId, String status, Instant createdAt, Instant approvedAt) {
        return jdbc.sql("INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, condition,"
                        + " status, created_at, approved_at) "
                        + "VALUES (:donorId, (SELECT id FROM item_categories WHERE is_active ORDER BY id LIMIT 1), "
                        + "(SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1), 'GIVE', 'Sách', 'Sách còn tốt',"
                        + " 'GOOD', :status, :createdAt, :approvedAt) RETURNING id")
                .param("donorId", donorId)
                .param("status", status)
                .param("createdAt", java.sql.Timestamp.from(createdAt))
                .param(
                        "approvedAt",
                        approvedAt == null ? null : java.sql.Timestamp.from(approvedAt),
                        java.sql.Types.TIMESTAMP)
                .query(Long.class)
                .single();
    }
}
