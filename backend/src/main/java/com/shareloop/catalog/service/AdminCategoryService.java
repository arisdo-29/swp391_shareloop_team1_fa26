package com.shareloop.catalog.service;

import com.shareloop.audit.ActivityType;
import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.catalog.CatalogErrorCode;
import com.shareloop.catalog.dto.AdminCategoryRequest;
import com.shareloop.catalog.dto.AdminCategoryResponse;
import com.shareloop.catalog.entity.ItemCategory;
import com.shareloop.catalog.repository.ItemCategoryRepository;
import com.shareloop.common.exception.BusinessException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminCategoryService {
    private static final String LOG_TARGET_TYPE = "CATEGORY";

    private final ItemCategoryRepository repository;
    private final ActivityLogService activityLogService;

    public AdminCategoryService(ItemCategoryRepository repository, ActivityLogService activityLogService) {
        this.repository = repository;
        this.activityLogService = activityLogService;
    }

    public List<AdminCategoryResponse> listAll() {
        return repository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(AdminCategoryService::toResponse)
                .toList();
    }

    /**
     * Thêm danh mục và ghi nhật ký CATEGORY_CREATE (FR-115).
     *
     * @throws BusinessException CATALOG_INVALID_PARENT khi danh mục cha không hợp lệ
     */
    @Transactional
    public AdminCategoryResponse create(AdminCategoryRequest request, Long adminId) {
        validateParent(request.parentId(), null);
        ItemCategory category = new ItemCategory(
                request.name().trim(),
                request.parentId(),
                request.restricted(),
                request.boostable(),
                request.sortOrder());
        ItemCategory saved = repository.saveAndFlush(category);
        logAction(ActivityType.CATEGORY_CREATE, adminId, saved);
        return toResponse(saved);
    }

    /**
     * Sửa danh mục và ghi nhật ký CATEGORY_UPDATE (FR-115).
     *
     * @throws BusinessException CATALOG_CATEGORY_NOT_FOUND, CATALOG_INVALID_PARENT
     */
    @Transactional
    public AdminCategoryResponse update(long id, AdminCategoryRequest request, Long adminId) {
        ItemCategory category = requireCategory(id);
        validateParent(request.parentId(), id);
        category.update(
                request.name().trim(),
                request.parentId(),
                request.restricted(),
                request.boostable(),
                request.sortOrder());
        ItemCategory saved = repository.saveAndFlush(category);
        logAction(ActivityType.CATEGORY_UPDATE, adminId, saved);
        return toResponse(saved);
    }

    /**
     * Bật/tắt danh mục (tắt thì ẩn luôn danh mục con) và ghi nhật ký CATEGORY_TOGGLE (FR-115).
     *
     * @throws BusinessException CATALOG_CATEGORY_NOT_FOUND, CATALOG_INVALID_PARENT khi bật lại mà cha đang ẩn
     */
    @Transactional
    public AdminCategoryResponse setActive(long id, boolean active, Long adminId) {
        ItemCategory category = requireCategory(id);
        if (active) {
            validateParent(category.getParentId(), id);
            category.activate();
        } else {
            category.deactivate();
            hideActiveChildren(id);
        }
        ItemCategory saved = repository.saveAndFlush(category);
        logAction(ActivityType.CATEGORY_TOGGLE, adminId, saved);
        return toResponse(saved);
    }

    private void logAction(ActivityType type, Long adminId, ItemCategory category) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", category.getName());
        data.put("active", category.isActive());
        activityLogService.log(type.name(), adminId, LOG_TARGET_TYPE, category.getId(), data, null);
    }

    private void hideActiveChildren(long parentId) {
        for (ItemCategory child : repository.findByParentIdAndIsActiveTrue(parentId)) {
            child.deactivate();
            hideActiveChildren(child.getId());
        }
    }

    private void validateParent(Long parentId, Long categoryId) {
        Long currentId = parentId;
        while (currentId != null) {
            if (currentId.equals(categoryId)) {
                throw invalidParent();
            }
            ItemCategory parent = repository.findByIdAndIsActiveTrue(currentId).orElseThrow(this::invalidParent);
            currentId = parent.getParentId();
        }
    }

    private ItemCategory requireCategory(long id) {
        return repository
                .findById(id)
                .orElseThrow(() -> new BusinessException(CatalogErrorCode.CATALOG_CATEGORY_NOT_FOUND));
    }

    private BusinessException invalidParent() {
        return new BusinessException(
                CatalogErrorCode.CATALOG_INVALID_PARENT,
                CatalogErrorCode.CATALOG_INVALID_PARENT.defaultMessage(),
                "parentId",
                Map.of());
    }

    private static AdminCategoryResponse toResponse(ItemCategory category) {
        return new AdminCategoryResponse(
                category.getId(),
                category.getName(),
                category.getParentId(),
                category.isRestricted(),
                category.isBoostable(),
                category.getSortOrder(),
                category.isActive());
    }
}
