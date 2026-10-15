package com.shareloop.user.job;

import com.shareloop.user.service.PendingAccountCleanupService;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job xoá mềm tài khoản chưa xác thực quá hạn (FR-117, BR-U06), chạy mỗi giờ. Chỉ gọi
 * {@link PendingAccountCleanupService} và ghi log; mọi luật nằm ở service.
 */
@Component
// Bean chỉ được tạo khi app.jobs.enabled=true (dev, prod); profile test đặt false để job không tự chạy giữa các test.
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true")
public class PendingAccountCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(PendingAccountCleanupJob.class);

    private final PendingAccountCleanupService pendingAccountCleanupService;
    private final Clock clock;

    public PendingAccountCleanupJob(PendingAccountCleanupService pendingAccountCleanupService, Clock clock) {
        this.pendingAccountCleanupService = pendingAccountCleanupService;
        this.clock = clock;
    }

    // Phút 30 mỗi giờ: lệch với ItemExpiryJob (phút 0) để hai job không chạy cùng lúc.
    @Scheduled(cron = "0 30 * * * *")
    public void run() {
        int deletedCount = pendingAccountCleanupService.deleteExpiredPendingAccounts(clock.instant());
        log.info("PendingAccountCleanupJob finished: {} unverified account(s) soft-deleted", deletedCount);
    }
}
