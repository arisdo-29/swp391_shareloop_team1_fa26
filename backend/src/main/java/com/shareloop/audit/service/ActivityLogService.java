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

    /**
     * Tìm kiếm và phân trang nhật ký hành động của Admin (FR-115, Issue #117).
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<com.shareloop.audit.dto.ActivityLogDto> search(
            Long actorId, String type, java.time.LocalDate from, java.time.LocalDate to, int page, int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new com.shareloop.common.exception.BusinessException(
                    com.shareloop.audit.AuditErrorCode.INVALID_DATE_RANGE);
        }

        java.time.ZoneId zone = java.time.ZoneId.of("Asia/Ho_Chi_Minh");
        org.springframework.data.jpa.domain.Specification<ActivityLog> spec = (root, query, cb) -> cb.conjunction();

        if (actorId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("actorId"), actorId));
        }
        if (type != null && !type.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("type"), type.trim()));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(
                    root.get("createdAt"), from.atStartOfDay(zone).toInstant()));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThan(
                    root.get("createdAt"), to.plusDays(1).atStartOfDay(zone).toInstant()));
        }

        int pageNumber = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), 100);
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                pageNumber,
                pageSize,
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));

        return repository.findAll(spec, pageable).map(this::toDto);
    }

    private com.shareloop.audit.dto.ActivityLogDto toDto(ActivityLog entity) {
        String detail = entity.getReason();
        if ((detail == null || detail.isBlank())
                && entity.getData() != null
                && !entity.getData().isEmpty()) {
            detail = entity.getData().toString();
        }
        return new com.shareloop.audit.dto.ActivityLogDto(
                entity.getId(),
                entity.getActorId(),
                entity.getType(),
                entity.getTargetType(),
                entity.getTargetId(),
                entity.getData(),
                entity.getReason(),
                detail,
                entity.getCreatedAt());
    }
}
