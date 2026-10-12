package com.shareloop.review.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.support.IntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ReviewQueueRepositoryIT extends IntegrationTest {

    @Autowired
    ReviewQueueRepository repository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void ordersReviewFlagThenDonorRiskThenWaitingTime() {
        long areaId = jdbcTemplate.queryForObject(
                "INSERT INTO areas (name, level) VALUES (?, 1) RETURNING id",
                Long.class,
                "test-area-" + UUID.randomUUID());
        long categoryId = jdbcTemplate.queryForObject(
                "INSERT INTO item_categories (name) VALUES (?) RETURNING id",
                Long.class,
                "test-category-" + UUID.randomUUID());

        long riskyDonor = createDonor("risky", (short) 1, Instant.parse("2026-01-02T00:00:00Z"));
        long mediumDonor = createDonor("medium", (short) 3, Instant.parse("2026-01-03T00:00:00Z"));

        long reviewOldest = createItem(
                riskyDonor,
                categoryId,
                areaId,
                "review-oldest",
                "{\"flag\":\"REVIEW\",\"reason\":\"keyword match\"}",
                Instant.parse("2026-10-10T00:00:00Z"));
        long reviewNewer = createItem(
                riskyDonor,
                categoryId,
                areaId,
                "review-newer",
                "{\"flag\":\"REVIEW\",\"reason\":\"keyword match\"}",
                Instant.parse("2026-10-11T00:00:00Z"));
        long reviewSafer = createItem(
                mediumDonor,
                categoryId,
                areaId,
                "review-safer",
                "{\"flag\":\"REVIEW\",\"reason\":\"keyword match\"}",
                Instant.parse("2026-10-09T00:00:00Z"));
        long unflaggedRisky =
                createItem(riskyDonor, categoryId, areaId, "unflagged", null, Instant.parse("2026-10-01T00:00:00Z"));

        var rows = repository.findPendingReviewQueue(0, 10);

        var queueRows = rows.stream()
                .filter(row -> Set.of(reviewOldest, reviewNewer, reviewSafer, unflaggedRisky)
                        .contains(((Number) row[0]).longValue()))
                .toList();
        assertThat(queueRows)
                .extracting(row -> ((Number) row[0]).longValue())
                .containsExactly(reviewOldest, reviewNewer, reviewSafer, unflaggedRisky);
        assertThat(queueRows.get(0)[4]).isEqualTo("REVIEW");
        assertThat(queueRows.get(0)[5]).isEqualTo("keyword match");
        assertThat(queueRows.get(3)[4]).isEqualTo("NONE");
        assertThat(queueRows.get(3)[5]).isNull();
    }

    private long createDonor(String suffix, short trustStars, Instant createdAt) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO users (email, password_hash, full_name, phone, status, trust_stars, created_at)
                VALUES (?, 'test-hash', ?, '0912345678', 'ACTIVE', ?, ?)
                RETURNING id
                """,
                Long.class,
                UUID.randomUUID() + "-" + suffix + "@test.local",
                suffix,
                trustStars,
                Timestamp.from(createdAt));
    }

    private long createItem(
            long donorId, long categoryId, long areaId, String title, String aiResult, Instant createdAt) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, condition,
                                   status, ai_risk, ai_result, created_at)
                VALUES (?, ?, ?, 'GIVE', ?, 'description', 'GOOD', 'PENDING_REVIEW', 'LOW', ?::jsonb, ?)
                RETURNING id
                """, Long.class, donorId, categoryId, areaId, title, aiResult, Timestamp.from(createdAt));
    }
}
