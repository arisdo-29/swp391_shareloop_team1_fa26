package com.shareloop.request.job;

import com.shareloop.request.service.RequestJobService;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job định kỳ xử lý tự động vòng đời Request (Milestone R4, Issue #109):
 * <ul>
 *   <li>Huỷ tự động các yêu cầu PENDING quá hạn (FR-119, BR-R06).</li>
 *   <li>Huỷ tự động các yêu cầu RESERVED/AWAITING_LOGISTICS quá 10 ngày chưa chốt lịch và đóng chat (FR-120, BR-T10, FR-77, BR-C03).</li>
 *   <li>Tự động hoàn tất (COMPLETED) khi một bên đã xác nhận giao nhận mà bên kia im lặng quá hạn (FR-87, BR-T06).</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true")
public class RequestJob {

    private static final Logger log = LoggerFactory.getLogger(RequestJob.class);

    private final RequestJobService requestJobService;
    private final Clock clock;

    public RequestJob(RequestJobService requestJobService, Clock clock) {
        this.requestJobService = requestJobService;
        this.clock = clock;
    }

    /**
     * Chạy định kỳ vào phút 15 của mỗi giờ (tránh trùng phút 00 và 30 của các job khác).
     */
    @Scheduled(cron = "0 15 * * * *")
    public void run() {
        Instant now = clock.instant();
        int pendingExpired = requestJobService.expirePendingRequests(now);
        int silentReservedExpired = requestJobService.expireSilentReservedRequests(now);
        int autoConfirmed = requestJobService.autoConfirmHandoverRequests(now);

        log.info(
                "RequestJob finished: {} pending expired, {} silent reserved expired, {} auto confirmed",
                pendingExpired,
                silentReservedExpired,
                autoConfirmed);
    }
}
