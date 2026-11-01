package com.shareloop.ops.controller;

import com.shareloop.listingfee.job.ItemExpiryJob;
import com.shareloop.request.job.RequestJob;
import com.shareloop.user.job.PendingAccountCleanupJob;
import com.shareloop.wallet.job.PaymentExpiryJob;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint nội bộ để Cloud Scheduler gọi kích hoạt các job định kỳ trên Cloud Run (request-based billing).
 */
@RestController
@RequestMapping("/internal/jobs")
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true")
public class InternalJobController {

    private static final Logger log = LoggerFactory.getLogger(InternalJobController.class);

    private final String triggerToken;
    private final ItemExpiryJob itemExpiryJob;
    private final RequestJob requestJob;
    private final PendingAccountCleanupJob pendingAccountCleanupJob;
    private final PaymentExpiryJob paymentExpiryJob;

    // ReviewSlaJob đã gỡ (KD-06, TD-04): nhóm bỏ thông báo nhắc Admin bài chờ duyệt quá 24 giờ.
    public InternalJobController(
            @Value("${app.jobs.trigger-token:}") String triggerToken,
            ItemExpiryJob itemExpiryJob,
            RequestJob requestJob,
            PendingAccountCleanupJob pendingAccountCleanupJob,
            PaymentExpiryJob paymentExpiryJob) {
        this.triggerToken = triggerToken;
        this.itemExpiryJob = itemExpiryJob;
        this.requestJob = requestJob;
        this.pendingAccountCleanupJob = pendingAccountCleanupJob;
        this.paymentExpiryJob = paymentExpiryJob;
    }

    @PostMapping("/run")
    public ResponseEntity<Map<String, String>> runJobs(
            @RequestHeader(value = "X-Job-Token", required = false) String jobToken) {
        if (triggerToken == null || triggerToken.isBlank() || jobToken == null || jobToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (!MessageDigest.isEqual(
                triggerToken.getBytes(StandardCharsets.UTF_8), jobToken.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Map<String, String> results = new LinkedHashMap<>();
        results.put("itemExpiryJob", executeJob(itemExpiryJob::run, "itemExpiryJob"));
        results.put("requestJob", executeJob(requestJob::run, "requestJob"));
        results.put("pendingAccountCleanupJob", executeJob(pendingAccountCleanupJob::run, "pendingAccountCleanupJob"));
        results.put("paymentExpiryJob", executeJob(paymentExpiryJob::run, "paymentExpiryJob"));

        return ResponseEntity.ok(results);
    }

    private String executeJob(Runnable job, String jobName) {
        try {
            job.run();
            return "ok";
        } catch (Exception e) {
            log.error("Execution of job {} failed via /internal/jobs/run", jobName, e);
            return "failed";
        }
    }
}
