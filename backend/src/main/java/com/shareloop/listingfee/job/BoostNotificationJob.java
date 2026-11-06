package com.shareloop.listingfee.job;

import com.shareloop.listingfee.service.BoostNotificationService;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job thông báo đợt đẩy bài (C15, SRS 4.6), chạy mỗi giờ. Chỉ gọi {@link BoostNotificationService} và ghi log; mọi
 * luật nằm ở service.
 */
@Component
// Bean chỉ được tạo khi app.jobs.enabled=true (dev, prod); profile test đặt false để job không tự chạy giữa các test.
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true")
public class BoostNotificationJob {

    private static final Logger log = LoggerFactory.getLogger(BoostNotificationJob.class);

    private final BoostNotificationService boostNotificationService;
    private final Clock clock;

    public BoostNotificationJob(BoostNotificationService boostNotificationService, Clock clock) {
        this.boostNotificationService = boostNotificationService;
        this.clock = clock;
    }

    // "0 0 * * * *" = giây 0, phút 0 của mỗi giờ. Dừng đẩy bài bị ẩn trước, để bài đó không lọt vào hai bước sau.
    @Scheduled(cron = "0 0 * * * *")
    public void run() {
        Instant now = clock.instant();
        int stoppedCount = boostNotificationService.stopHiddenBoosts(now);
        int endingSoonCount = boostNotificationService.notifyEndingSoon(now);
        int endedCount = boostNotificationService.notifyEnded(now);
        log.info(
                "BoostNotificationJob finished: {} stopped, {} ending-soon notice(s), {} ended notice(s)",
                stoppedCount,
                endingSoonCount,
                endedCount);
    }
}
