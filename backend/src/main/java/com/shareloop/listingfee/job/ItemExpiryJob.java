package com.shareloop.listingfee.job;

import com.shareloop.listingfee.service.ItemExpiryNotificationService;
import com.shareloop.listingfee.service.ItemExpiryService;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job hết hạn bài (BD-12, TD-01), chạy mỗi giờ: chuyển bài quá hạn sang EXPIRED rồi nhắc gia hạn các bài sắp hết hạn
 * (B19). Chỉ gọi service và ghi log; mọi luật nằm ở {@link ItemExpiryService} và
 * {@link ItemExpiryNotificationService}.
 */
@Component
// Bean chỉ được tạo khi app.jobs.enabled=true (dev, prod); profile test đặt false để job không tự chạy giữa các test.
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true")
public class ItemExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(ItemExpiryJob.class);

    private final ItemExpiryService itemExpiryService;
    private final ItemExpiryNotificationService itemExpiryNotificationService;
    private final Clock clock;

    public ItemExpiryJob(
            ItemExpiryService itemExpiryService,
            ItemExpiryNotificationService itemExpiryNotificationService,
            Clock clock) {
        this.itemExpiryService = itemExpiryService;
        this.itemExpiryNotificationService = itemExpiryNotificationService;
        this.clock = clock;
    }

    // @Scheduled: Spring tự gọi hàm theo lịch cron "giây phút giờ ngày tháng thứ" (cần @EnableScheduling ở
    // SchedulingConfig). "0 0 * * * *" = giây 0, phút 0 của mỗi giờ. Chuyển EXPIRED trước để bài vừa quá hạn không bị
    // nhắc gia hạn nữa (query nhắc chỉ chọn bài còn hạn).
    @Scheduled(cron = "0 0 * * * *")
    public void run() {
        Instant now = clock.instant();
        int expiredCount = itemExpiryService.expireOverdueItems(now);
        int reminderCount = itemExpiryNotificationService.notifyExpiringSoon(now);
        log.info(
                "ItemExpiryJob finished: {} item(s) moved to EXPIRED, {} expiry reminder(s) sent",
                expiredCount,
                reminderCount);
    }
}
