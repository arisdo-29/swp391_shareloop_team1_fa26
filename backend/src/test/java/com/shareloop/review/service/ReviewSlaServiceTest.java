package com.shareloop.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.notification.service.NotificationService;
import com.shareloop.review.repository.ReviewQueueRepository;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReviewSlaServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T03:45:00Z");

    @Mock
    ReviewQueueRepository reviewQueueRepository;

    @Mock
    ConfigService configService;

    @Mock
    NotificationService notificationService;

    ReviewSlaService service;

    @BeforeEach
    void setUp() {
        service = new ReviewSlaService(reviewQueueRepository, configService, notificationService);
    }

    @Test
    @DisplayName("Có bài chờ từ 24 giờ trở lên: gửi một thông báo tổng hợp cho Admin kèm số bài")
    void alertOverdueReviews_whenItemsPastSla_notifiesAdminsOnce() {
        when(configService.getInt(ConfigKey.REVIEW_SLA_HOURS)).thenReturn(24);
        when(reviewQueueRepository.countPendingReviewWaitingSince(NOW.minus(Duration.ofHours(24))))
                .thenReturn(2L);

        long overdue = service.alertOverdueReviews(NOW);

        assertThat(overdue).isEqualTo(2);
        verify(notificationService)
                .notifyAdmins(
                        "REVIEW_SLA_OVERDUE",
                        "Bài chờ duyệt quá SLA",
                        "2 bài đã chờ duyệt từ 24 giờ trở lên. Vui lòng xử lý hàng chờ duyệt.",
                        "/admin/moderation");
    }

    @Test
    @DisplayName("Không có bài nào quá SLA: không gửi thông báo")
    void alertOverdueReviews_whenNoItemPastSla_doesNotNotify() {
        when(configService.getInt(ConfigKey.REVIEW_SLA_HOURS)).thenReturn(24);
        when(reviewQueueRepository.countPendingReviewWaitingSince(any())).thenReturn(0L);

        long overdue = service.alertOverdueReviews(NOW);

        assertThat(overdue).isZero();
        verify(notificationService, never()).notifyAdmins(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Số giờ SLA đọc từ ConfigService: đổi thành 48 giờ thì mốc đếm lùi 48 giờ")
    void alertOverdueReviews_readsSlaHoursFromConfig() {
        when(configService.getInt(ConfigKey.REVIEW_SLA_HOURS)).thenReturn(48);
        when(reviewQueueRepository.countPendingReviewWaitingSince(NOW.minus(Duration.ofHours(48))))
                .thenReturn(1L);

        service.alertOverdueReviews(NOW);

        verify(notificationService)
                .notifyAdmins(
                        "REVIEW_SLA_OVERDUE",
                        "Bài chờ duyệt quá SLA",
                        "1 bài đã chờ duyệt từ 48 giờ trở lên. Vui lòng xử lý hàng chờ duyệt.",
                        "/admin/moderation");
    }
}
