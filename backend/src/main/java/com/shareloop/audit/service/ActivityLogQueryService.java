package com.shareloop.audit.service;

import com.shareloop.audit.ActivityType;
import com.shareloop.audit.AuditErrorCode;
import com.shareloop.audit.dto.ActivityLogDto;
import com.shareloop.audit.dto.ActivityTypeDto;
import com.shareloop.audit.entity.ActivityLog;
import com.shareloop.audit.repository.ActivityLogRepository;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.service.ItemQueryService;
import com.shareloop.setting.ConfigKey;
import com.shareloop.user.dto.UserBrief;
import com.shareloop.user.service.UserService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phần đọc nhật ký hành động cho Admin (FR-115, Issue #117). Tách khỏi ActivityLogService để tránh vòng phụ thuộc
 * ActivityLogService → UserService → ConfigService → ActivityLogService.
 */
@Service
public class ActivityLogQueryService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int MAX_PAGE_SIZE = 100;
    private static final String SYSTEM_ACTOR_NAME = "Hệ thống";
    // Người dùng đã xoá mềm không còn tên. Không hiện "Người dùng #id" vì Admin không đọc được id.
    private static final String DELETED_USER_NAME = "Tài khoản đã xoá";

    private final ActivityLogRepository repository;
    private final UserService userService;
    private final ItemQueryService itemQueryService;

    public ActivityLogQueryService(
            ActivityLogRepository repository, UserService userService, ItemQueryService itemQueryService) {
        this.repository = repository;
        this.userService = userService;
        this.itemQueryService = itemQueryService;
    }

    /**
     * Tìm kiếm và phân trang nhật ký, mới nhất trước. Tên người thực hiện và tên đối tượng được tra một lần cho cả
     * trang (không tra từng dòng).
     *
     * @throws BusinessException INVALID_DATE_RANGE khi from sau to
     */
    @Transactional(readOnly = true)
    public Page<ActivityLogDto> search(Long actorId, String type, LocalDate from, LocalDate to, int page, int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(AuditErrorCode.INVALID_DATE_RANGE);
        }
        Page<ActivityLog> logs = repository.findAll(buildFilter(actorId, type, from, to), buildPageable(page, size));

        Map<Long, UserBrief> users = userService.findBriefs(collectUserIds(logs.getContent()));
        Map<Long, String> itemTitles = itemQueryService.findTitles(collectItemIds(logs.getContent()));
        return logs.map(log -> toDto(log, users, itemTitles));
    }

    /**
     * Dữ liệu (cột data) của dòng nhật ký mới nhất thuộc một loại hành động trên một đối tượng; rỗng nếu chưa có dòng
     * nào hoặc dòng đó không có data.
     */
    @Transactional(readOnly = true)
    public java.util.Optional<Map<String, Object>> findLatestData(String type, Long targetId) {
        return repository
                .findFirstByTypeAndTargetIdOrderByIdDesc(type, targetId)
                .map(ActivityLog::getData);
    }

    /**
     * Lý do (cột reason) của dòng nhật ký mới nhất thuộc một loại hành động trên một đối tượng; rỗng nếu chưa có dòng
     * nào hoặc dòng đó không có reason.
     */
    @Transactional(readOnly = true)
    public java.util.Optional<String> findLatestReason(String type, Long targetId) {
        if (type == null || targetId == null) {
            return java.util.Optional.empty();
        }
        return repository
                .findFirstByTypeAndTargetIdOrderByIdDesc(type, targetId)
                .map(ActivityLog::getReason);
    }

    /** Danh sách loại hành động kèm nhãn tiếng Việt cho bộ lọc phía FE. */
    public List<ActivityTypeDto> listTypes() {
        List<ActivityTypeDto> types = new ArrayList<>();
        for (ActivityType type : ActivityType.values()) {
            types.add(new ActivityTypeDto(type.code(), type.label()));
        }
        return types;
    }

    private Specification<ActivityLog> buildFilter(Long actorId, String type, LocalDate from, LocalDate to) {
        Specification<ActivityLog> filter = (root, query, builder) -> builder.conjunction();
        if (actorId != null) {
            filter = filter.and((root, query, builder) -> builder.equal(root.get("actorId"), actorId));
        }
        if (type != null && !type.isBlank()) {
            filter = filter.and((root, query, builder) -> builder.equal(root.get("type"), type.trim()));
        }
        if (from != null) {
            filter = filter.and((root, query, builder) -> builder.greaterThanOrEqualTo(
                    root.get("createdAt"), from.atStartOfDay(VIETNAM_ZONE).toInstant()));
        }
        if (to != null) {
            // "Đến ngày" tính trọn ngày: lấy mọi bản ghi trước 0 giờ ngày hôm sau.
            filter = filter.and((root, query, builder) -> builder.lessThan(
                    root.get("createdAt"),
                    to.plusDays(1).atStartOfDay(VIETNAM_ZONE).toInstant()));
        }
        return filter;
    }

    private static Pageable buildPageable(int page, int size) {
        int pageNumber = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    // Gồm người thực hiện và người dùng là đối tượng (targetType USER), để chỉ gọi UserService một lần.
    private static Set<Long> collectUserIds(List<ActivityLog> logs) {
        Set<Long> userIds = new HashSet<>();
        for (ActivityLog log : logs) {
            if (log.getActorId() != null) {
                userIds.add(log.getActorId());
            }
            if ("USER".equals(log.getTargetType()) && log.getTargetId() != null) {
                userIds.add(log.getTargetId());
            }
        }
        return userIds;
    }

    private static Set<Long> collectItemIds(List<ActivityLog> logs) {
        Set<Long> itemIds = new HashSet<>();
        for (ActivityLog log : logs) {
            if ("ITEM".equals(log.getTargetType()) && log.getTargetId() != null) {
                itemIds.add(log.getTargetId());
            }
            // Log báo cáo và giao dịch lưu itemId trong data để hiện tên bài thay cho "#id"
            Long dataItemId = dataItemId(log);
            if (dataItemId != null) {
                itemIds.add(dataItemId);
            }
        }
        return itemIds;
    }

    private static Long dataItemId(ActivityLog log) {
        if (log.getData() != null && log.getData().get("itemId") instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private static ActivityLogDto toDto(ActivityLog log, Map<Long, UserBrief> users, Map<Long, String> itemTitles) {
        UserBrief actor = log.getActorId() == null ? null : users.get(log.getActorId());
        return new ActivityLogDto(
                log.getId(),
                log.getActorId(),
                actorName(log.getActorId(), actor),
                actor == null ? null : actor.email(),
                log.getType(),
                ActivityType.labelOf(log.getType()),
                log.getTargetType(),
                log.getTargetId(),
                targetTitle(log, users, itemTitles),
                log.getData(),
                log.getReason(),
                detail(log),
                log.getCreatedAt());
    }

    private static String actorName(Long actorId, UserBrief actor) {
        if (actorId == null) {
            return SYSTEM_ACTOR_NAME;
        }
        // User đã xoá mềm không có trong map nhưng nhật ký vẫn phải hiện được người làm.
        return actor == null ? DELETED_USER_NAME : actor.fullName();
    }

    private static String targetTitle(ActivityLog log, Map<Long, UserBrief> users, Map<Long, String> itemTitles) {
        Long targetId = log.getTargetId();
        if (log.getTargetType() == null) {
            return null;
        }
        return switch (log.getTargetType()) {
            case "ITEM" -> targetId == null ? null : itemTitles.get(targetId);
            case "USER" -> targetUserName(targetId, users);
            case "CONFIG" -> configLabel(dataValue(log, "key"));
            // Gọi bằng tên bài; log cũ chưa có itemId thì chỉ hiện loại, không hiện "#id"
            case "REPORT" -> withItemTitle("Báo cáo về", "Báo cáo", log, itemTitles);
            case "REQUEST" -> withItemTitle("Giao dịch món", "Giao dịch", log, itemTitles);
            default -> null;
        };
    }

    private static String withItemTitle(String prefix, String fallback, ActivityLog log, Map<Long, String> itemTitles) {
        Long itemId = dataItemId(log);
        String title = itemId == null ? null : itemTitles.get(itemId);
        return title == null || title.isBlank() ? fallback : prefix + " “" + title + "”";
    }

    // Key cấu hình (vd "reputation_gold_min") đổi sang nhãn tiếng Việt mà trang Admin đang hiện
    private static String configLabel(String key) {
        if (key == null) {
            return null;
        }
        for (ConfigKey configKey : ConfigKey.values()) {
            if (configKey.key().equalsIgnoreCase(key)) {
                return configKey.label();
            }
        }
        return key;
    }

    private static String targetUserName(Long targetId, Map<Long, UserBrief> users) {
        if (targetId == null) {
            return null;
        }
        UserBrief user = users.get(targetId);
        return user == null ? DELETED_USER_NAME : user.fullName();
    }

    // Không trả Map.toString() nữa: FE hiển thị thẳng chuỗi này nên phải đọc được.
    private static String detail(ActivityLog log) {
        if (ActivityType.REVIEW_APPROVE.code().equals(log.getType())) {
            return ActivityType.REVIEW_APPROVE.label();
        }
        if (ActivityType.CONFIG_UPDATE.code().equals(log.getType()) && log.getData() != null) {
            return configLabel(dataValue(log, "key")) + ": " + dataValue(log, "oldValue") + " → "
                    + dataValue(log, "newValue");
        }
        if ((ActivityType.AI_USAGE.code().equals(log.getType()) || "AiUsage".equals(log.getType()))
                && log.getData() != null) {
            String outcome = dataValue(log, "outcome");
            if (outcome != null) {
                String action = "SEARCH".equals(dataValue(log, "role")) ? "Tìm đồ AI" : "Kiểm duyệt AI";
                String duration = dataValue(log, "durationMs");
                return action + ": " + outcome + (duration == null ? "" : " (" + duration + " ms)");
            }
        }
        return log.getReason();
    }

    private static String dataValue(ActivityLog log, String key) {
        if (log.getData() == null || log.getData().get(key) == null) {
            return null;
        }
        return String.valueOf(log.getData().get(key));
    }
}
