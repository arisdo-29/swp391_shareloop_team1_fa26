package com.shareloop.audit.service;

import com.shareloop.audit.entity.ActivityLog;
import com.shareloop.audit.repository.ActivityLogRepository;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ActivityLogService {

    private final ActivityLogRepository repository;

    public ActivityLogService(ActivityLogRepository repository) {
        this.repository = repository;
    }

    public void log(
            Long actorId, String type, String targetType, Long targetId, Map<String, Object> data, String reason) {
        repository.save(new ActivityLog(actorId, type, targetType, targetId, data, reason));
    }

    /**
     * Chữ ký theo SRS FR-115 / Issue #40: type, actorId, targetType, targetId, data, reason.
     */
    public void log(String type, Long actorId, String targetType, Long targetId, String data, String reason) {
        Map<String, Object> payload = data != null && !data.isBlank() ? Map.of("raw", data) : null;
        log(actorId, type, targetType, targetId, payload, reason);
    }
}
