package com.shareloop.media.job;

import com.shareloop.media.service.MediaService;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job dọn ảnh đã tải lên nhưng chưa gắn quá lâu (D2), chạy mỗi giờ. Chỉ gọi {@link MediaService} và ghi log; luật nằm ở service.
 */
@Component
// Bean chỉ được tạo khi app.jobs.enabled=true (dev, prod); profile test đặt false để job không tự chạy giữa các test.
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true")
public class MediaOrphanCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(MediaOrphanCleanupJob.class);

    private final MediaService mediaService;
    private final Clock clock;

    public MediaOrphanCleanupJob(MediaService mediaService, Clock clock) {
        this.mediaService = mediaService;
        this.clock = clock;
    }

    // "0 30 * * * *" = giây 0, phút 30 của mỗi giờ (lệch với job hết hạn bài chạy phút 0).
    @Scheduled(cron = "0 30 * * * *")
    public void run() {
        int cleaned = mediaService.cleanupOrphanedUploads(clock.instant());
        log.info("MediaOrphanCleanupJob finished: {} orphaned upload(s) removed", cleaned);
    }
}
