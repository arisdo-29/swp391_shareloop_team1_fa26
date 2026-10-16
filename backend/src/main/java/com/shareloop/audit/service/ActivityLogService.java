package com.shareloop.audit.service;

import com.shareloop.audit.entity.ActivityLog;
import com.shareloop.audit.repository.ActivityLogRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityLogService {

    private final ActivityLogRepository repository;
    private final Clock clock;

    public ActivityLogService(ActivityLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Chữ ký chính theo SRS FR-115 / Issue #99: type, actorId, targetType, targetId, data, reason.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void log(String type, Long actorId, String targetType, Long targetId, String data, String reason) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Activity log type must not be null or blank");
        }
        Map<String, Object> payload = data != null && !data.isBlank() ? Map.of("raw", data) : null;
        log(type, actorId, targetType, targetId, payload, reason);
    }

    /**
     * Overload nhận data có cấu trúc dưới dạng Map.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void log(
            String type, Long actorId, String targetType, Long targetId, Map<String, Object> data, String reason) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Activity log type must not be null or blank");
        }
        ActivityLog activityLog =
                new ActivityLog(type, actorId, targetType, targetId, data, reason, Instant.now(clock));
        repository.save(activityLog);
    }

    /**
     * Chữ ký tương thích ngược theo #40.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void log(
            Long actorId, String type, String targetType, Long targetId, Map<String, Object> data, String reason) {
        log(type, actorId, targetType, targetId, data, reason);
    }
}
