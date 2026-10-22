package com.shareloop.review.job;

import com.shareloop.review.service.ReviewSlaService;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job cảnh báo Admin khi có bài chờ duyệt quá SLA (FR-121, BR-A02), chạy mỗi giờ. Chỉ gọi {@link ReviewSlaService}
 * và ghi log; mọi luật nằm ở service.
 */
@Component
// Bean chỉ được tạo khi app.jobs.enabled=true (dev, prod); profile test đặt false để job không tự chạy giữa các test.
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true")
public class ReviewSlaJob {

    private static final Logger log = LoggerFactory.getLogger(ReviewSlaJob.class);

    private final ReviewSlaService reviewSlaService;
    private final Clock clock;

    public ReviewSlaJob(ReviewSlaService reviewSlaService, Clock clock) {
        this.reviewSlaService = reviewSlaService;
        this.clock = clock;
    }

    // Phút 45 mỗi giờ: lệch với ItemExpiryJob (phút 0), RequestJob (phút 15) và PendingAccountCleanupJob (phút 30).
    @Scheduled(cron = "0 45 * * * *")
    public void run() {
        long overdue = reviewSlaService.alertOverdueReviews(clock.instant());
        log.info("ReviewSlaJob finished: {} item(s) pending review past SLA", overdue);
    }
}
