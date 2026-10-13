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
}
