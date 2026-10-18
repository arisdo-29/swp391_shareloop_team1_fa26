package com.shareloop.wallet.job;

import com.shareloop.wallet.service.PaymentExpiryService;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job hết hạn đơn nạp (FR-18), chạy mỗi 5 phút. Chỉ gọi {@link PaymentExpiryService} và ghi log; mọi luật nằm ở
 * service.
 */
@Component
// Bean chỉ được tạo khi app.jobs.enabled=true (dev, prod); profile test đặt false để job không tự chạy giữa các test.
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true")
public class PaymentExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentExpiryJob.class);

    private final PaymentExpiryService paymentExpiryService;
    private final Clock clock;

    public PaymentExpiryJob(PaymentExpiryService paymentExpiryService, Clock clock) {
        this.paymentExpiryService = paymentExpiryService;
        this.clock = clock;
    }

    // "0 */5 * * * *" = giây 0 của mỗi phút chia hết cho 5 (00, 05, 10, ...).
    @Scheduled(cron = "0 */5 * * * *")
    public void run() {
        int expiredCount = paymentExpiryService.expireStaleOrders(clock.instant());
        log.info("PaymentExpiryJob finished: {} payment order(s) moved to EXPIRED", expiredCount);
    }
}
