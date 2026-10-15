package com.shareloop.listingfee.job;

import com.shareloop.listingfee.service.ItemExpiryService;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job hết hạn bài (FR-29, BR-D03), chạy mỗi giờ. Chỉ gọi {@link ItemExpiryService} và ghi log; mọi luật nằm ở service.
 */
@Component
// Bean chỉ được tạo khi app.jobs.enabled=true (dev, prod); profile test đặt false để job không tự chạy giữa các test.
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true")
public class ItemExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(ItemExpiryJob.class);

    private final ItemExpiryService itemExpiryService;
    private final Clock clock;

    public ItemExpiryJob(ItemExpiryService itemExpiryService, Clock clock) {
        this.itemExpiryService = itemExpiryService;
        this.clock = clock;
    }

    // @Scheduled: Spring tự gọi hàm theo lịch cron "giây phút giờ ngày tháng thứ" (cần @EnableScheduling ở
    // SchedulingConfig). "0 0 * * * *" = giây 0, phút 0 của mỗi giờ.
    @Scheduled(cron = "0 0 * * * *")
    public void run() {
        int expiredCount = itemExpiryService.expireOverdueItems(clock.instant());
        log.info("ItemExpiryJob finished: {} item(s) moved to EXPIRED", expiredCount);
    }
}
