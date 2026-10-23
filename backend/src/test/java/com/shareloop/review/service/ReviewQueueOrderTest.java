package com.shareloop.review.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.review.dto.ReviewQueueItem;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReviewQueueOrderTest {

    private final Instant now = Instant.parse("2026-10-17T10:00:00Z");

    @Test
    @DisplayName("Bài có cờ REVIEW đứng trước bài không có cờ, dù bài không cờ có người đăng rủi ro hơn và chờ lâu hơn")
    void flaggedBeforeUnflaggedEvenIfUnflaggedHasLowerTrustStars() {
        ReviewQueueItem unflaggedRisky = new ReviewQueueItem(
                1L,
                10L,
                null,
                "Áo cũ",
                "LOW",
                "NONE",
                null,
                null,
                List.of(),
                (short) 1,
                now.minus(5, ChronoUnit.HOURS));
        ReviewQueueItem flaggedTrusted = new ReviewQueueItem(
                2L,
                20L,
                null,
                "Sách giáo khoa",
                "HIGH",
                "REVIEW",
                "Nghi ngờ có sđt",
                null,
                List.of(),
                (short) 5,
                now.minus(1, ChronoUnit.HOURS));

        List<ReviewQueueItem> items = new ArrayList<>(List.of(unflaggedRisky, flaggedTrusted));
        items.sort(ReviewQueueItem.QUEUE_ORDER);

        assertThat(items).containsExactly(flaggedTrusted, unflaggedRisky);
    }

    @Test
    @DisplayName("Cùng có cờ: người đăng có uy tín thấp hơn (rủi ro hơn) đứng trước")
    void sameFlag_lowerTrustStarsBeforeHigherTrustStars() {
        ReviewQueueItem riskyUserPost = new ReviewQueueItem(
                10L,
                101L,
                null,
                "Đồng hồ",
                "HIGH",
                "REVIEW",
                "Từ cấm",
                null,
                List.of(),
                (short) 2,
                now.minus(1, ChronoUnit.HOURS));
        ReviewQueueItem trustedUserPost = new ReviewQueueItem(
                11L,
                102L,
                null,
                "Tai nghe",
                "HIGH",
                "REVIEW",
                "Từ cấm",
                null,
                List.of(),
                (short) 4,
                now.minus(2, ChronoUnit.HOURS));

        List<ReviewQueueItem> items = new ArrayList<>(List.of(trustedUserPost, riskyUserPost));
        items.sort(ReviewQueueItem.QUEUE_ORDER);

        assertThat(items).containsExactly(riskyUserPost, trustedUserPost);
    }

    @Test
    @DisplayName("Cùng cờ và cùng số sao: bài vào hàng chờ lâu hơn đứng trước")
    void sameFlagAndTrustStars_olderItemBeforeNewerItem() {
        ReviewQueueItem olderPost = new ReviewQueueItem(
                21L,
                201L,
                null,
                "Balo",
                "MEDIUM",
                "REVIEW",
                "Lỗi ảnh",
                null,
                List.of(),
                (short) 3,
                now.minus(10, ChronoUnit.HOURS));
        ReviewQueueItem newerPost = new ReviewQueueItem(
                22L,
                202L,
                null,
                "Giày",
                "MEDIUM",
                "REVIEW",
                "Lỗi ảnh",
                null,
                List.of(),
                (short) 3,
                now.minus(2, ChronoUnit.HOURS));

        List<ReviewQueueItem> items = new ArrayList<>(List.of(newerPost, olderPost));
        items.sort(ReviewQueueItem.QUEUE_ORDER);

        assertThat(items).containsExactly(olderPost, newerPost);
    }

    @Test
    @DisplayName("Cùng cờ, cùng sao, cùng thời điểm: ID nhỏ hơn đứng trước để phá hòa ổn định")
    void sameFlagTrustStarsAndCreatedAt_smallerIdFirst() {
        ReviewQueueItem postWithSmallerId =
                new ReviewQueueItem(31L, 301L, null, "Món A", "LOW", "NONE", null, null, List.of(), (short) 5, now);
        ReviewQueueItem postWithLargerId =
                new ReviewQueueItem(32L, 302L, null, "Món B", "LOW", "NONE", null, null, List.of(), (short) 5, now);

        List<ReviewQueueItem> items = new ArrayList<>(List.of(postWithLargerId, postWithSmallerId));
        items.sort(ReviewQueueItem.QUEUE_ORDER);

        assertThat(items).containsExactly(postWithSmallerId, postWithLargerId);
    }

    @Test
    @DisplayName("Hàng chờ rỗng hoặc 1 phần tử không gây lỗi khi sắp xếp")
    void emptyOrSingleQueueDoesNotThrow() {
        List<ReviewQueueItem> emptyQueue = new ArrayList<>();
        emptyQueue.sort(ReviewQueueItem.QUEUE_ORDER);
        assertThat(emptyQueue).isEmpty();

        ReviewQueueItem singleItem =
                new ReviewQueueItem(1L, 10L, null, "Áo", "LOW", "NONE", null, null, List.of(), (short) 5, now);
        List<ReviewQueueItem> singleQueue = new ArrayList<>(List.of(singleItem));
        singleQueue.sort(ReviewQueueItem.QUEUE_ORDER);
        assertThat(singleQueue).containsExactly(singleItem);
    }

    @Test
    @DisplayName("Kiểm tra helper isFlagged và riskScore")
    void helperMethodsVerification() {
        ReviewQueueItem flaggedItem =
                new ReviewQueueItem(1L, 10L, null, "Áo", "HIGH", "REVIEW", "Vi phạm", null, List.of(), (short) 2, now);
        ReviewQueueItem unflaggedItem =
                new ReviewQueueItem(2L, 10L, null, "Quần", "LOW", "NONE", null, null, List.of(), (short) 5, now);

        assertThat(flaggedItem.isFlagged()).isTrue();
        assertThat(flaggedItem.riskScore()).isEqualTo(4);

        assertThat(unflaggedItem.isFlagged()).isFalse();
        assertThat(unflaggedItem.riskScore()).isEqualTo(1);
    }
}
