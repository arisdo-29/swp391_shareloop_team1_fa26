package com.shareloop.catalog.service;

import com.shareloop.audit.ActivityType;
import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.catalog.AreaErrorCode;
import com.shareloop.catalog.dto.AdminAreaRequest;
import com.shareloop.catalog.dto.AdminAreaResponse;
import com.shareloop.catalog.entity.Area;
import com.shareloop.catalog.repository.AreaRepository;
import com.shareloop.common.exception.BusinessException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminAreaService {
    private static final String LOG_TARGET_TYPE = "AREA";

    private final AreaRepository repository;
    private final ActivityLogService activityLogService;

    public AdminAreaService(AreaRepository repository, ActivityLogService activityLogService) {
        this.repository = repository;
        this.activityLogService = activityLogService;
    }

    public List<AdminAreaResponse> listAll() {
        return repository.findAllByOrderByIdAsc().stream()
                .map(AdminAreaService::toResponse)
                .toList();
    }

    /**
     * Thêm khu vực và ghi nhật ký AREA_CREATE (FR-115).
     *
     * @throws BusinessException CATALOG_INVALID_AREA_PARENT khi khu vực cha không hợp lệ
     */
    @Transactional
    public AdminAreaResponse create(AdminAreaRequest request, Long adminId) {
        short level = validateParent(request.parentId(), null);
        Area saved = repository.saveAndFlush(new Area(request.name().trim(), request.parentId(), level));
        logAction(ActivityType.AREA_CREATE, adminId, saved);
        return toResponse(saved);
    }

    /**
     * Sửa khu vực và ghi nhật ký AREA_UPDATE (FR-115).
     *
     * @throws BusinessException CATALOG_AREA_NOT_FOUND, CATALOG_INVALID_AREA_PARENT, CATALOG_AREA_HAS_CHILDREN
     */
    @Transactional
    public AdminAreaResponse update(long id, AdminAreaRequest request, Long adminId) {
        Area area = requireArea(id);
        short level = validateParent(request.parentId(), id);
        if (level == 2 && repository.existsByParentId(id)) {
            throw new BusinessException(AreaErrorCode.CATALOG_AREA_HAS_CHILDREN);
        }
        area.update(request.name().trim(), request.parentId(), level);
        Area saved = repository.saveAndFlush(area);
        logAction(ActivityType.AREA_UPDATE, adminId, saved);
        return toResponse(saved);
    }

    /**
     * Bật/tắt khu vực (tắt thì ẩn luôn khu vực con) và ghi nhật ký AREA_TOGGLE (FR-115).
     *
     * @throws BusinessException CATALOG_AREA_NOT_FOUND, CATALOG_INVALID_AREA_PARENT khi bật lại mà cha đang ẩn
     */
    @Transactional
    public AdminAreaResponse setActive(long id, boolean active, Long adminId) {
        Area area = requireArea(id);
        if (active) {
            validateParent(area.getParentId(), id);
            area.activate();
        } else {
            area.deactivate();
            repository.findByParentId(id).forEach(Area::deactivate);
        }
        Area saved = repository.saveAndFlush(area);
        logAction(ActivityType.AREA_TOGGLE, adminId, saved);
        return toResponse(saved);
    }

    private void logAction(ActivityType type, Long adminId, Area area) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", area.getName());
        data.put("active", area.isActive());
        activityLogService.log(type.name(), adminId, LOG_TARGET_TYPE, area.getId(), data, null);
    }

    private short validateParent(Long parentId, Long areaId) {
        if (parentId == null) {
            return 1;
        }
        if (parentId.equals(areaId)) {
            throw invalidParent();
        }
        requireActiveParent(parentId);
        return 2;
    }

    private void requireActiveParent(long parentId) {
        Area parent = repository.findById(parentId).orElseThrow(this::invalidParent);
        if (!parent.isActive() || parent.getLevel() != 1 || parent.getParentId() != null) {
            throw invalidParent();
        }
    }

    private Area requireArea(long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException(AreaErrorCode.CATALOG_AREA_NOT_FOUND));
    }

    private BusinessException invalidParent() {
        return new BusinessException(
                AreaErrorCode.CATALOG_INVALID_AREA_PARENT,
                AreaErrorCode.CATALOG_INVALID_AREA_PARENT.defaultMessage(),
                "parentId",
                Map.of());
    }

    private static AdminAreaResponse toResponse(Area area) {
        return new AdminAreaResponse(
                area.getId(), area.getName(), area.getParentId(), area.getLevel(), area.isActive());
    }
}
