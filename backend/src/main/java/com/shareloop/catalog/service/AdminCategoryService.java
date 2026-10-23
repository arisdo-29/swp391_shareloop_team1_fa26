package com.shareloop.catalog.service;

import com.shareloop.audit.ActivityType;
import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.catalog.CatalogErrorCode;
import com.shareloop.catalog.dto.AdminCategoryReorderRequest;
import com.shareloop.catalog.dto.AdminCategoryRequest;
import com.shareloop.catalog.dto.AdminCategoryResponse;
import com.shareloop.catalog.entity.ItemCategory;
import com.shareloop.catalog.repository.ItemCategoryRepository;
import com.shareloop.common.exception.BusinessException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
     * Thêm danh mục và ghi nhật ký CATEGORY_CREATE (FR-115). Không gửi sortOrder thì danh mục được đặt xuống cuối.
     *
     * @throws BusinessException CATALOG_INVALID_PARENT khi danh mục cha không hợp lệ
     */
    @Transactional
    public AdminCategoryResponse create(AdminCategoryRequest request, Long adminId) {
        validateParent(request.parentId(), null);
        int sortOrder = request.sortOrder() != null ? request.sortOrder() : nextSortOrder();
        ItemCategory category = new ItemCategory(
                request.name().trim(), request.parentId(), request.restricted(), request.boostable(), sortOrder);
        ItemCategory saved = repository.saveAndFlush(category);
        logAction(ActivityType.CATEGORY_CREATE, adminId, saved);
        return toResponse(saved);
    }

    /**
     * Sửa danh mục và ghi nhật ký CATEGORY_UPDATE (FR-115). Không gửi sortOrder thì giữ thứ tự hiện tại.
     *
     * @throws BusinessException CATALOG_CATEGORY_NOT_FOUND, CATALOG_INVALID_PARENT
     */
    @Transactional
    public AdminCategoryResponse update(long id, AdminCategoryRequest request, Long adminId) {
        ItemCategory category = requireCategory(id);
        validateParent(request.parentId(), id);
        int sortOrder = request.sortOrder() != null ? request.sortOrder() : category.getSortOrder();
        category.update(
                request.name().trim(), request.parentId(), request.restricted(), request.boostable(), sortOrder);
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

    /**
     * Sắp xếp lại toàn bộ danh mục theo danh sách id và ghi nhật ký CATEGORY_REORDER (FR-115). Thứ tự mới là vị trí
     * trong danh sách, bắt đầu từ 1. Danh sách phải gồm đủ mọi danh mục, mỗi danh mục đúng một lần.
     *
     * @throws BusinessException CATALOG_INVALID_SORT_ORDER khi danh sách trùng id, thiếu danh mục hoặc có id lạ
     */
    @Transactional
    public List<AdminCategoryResponse> reorder(AdminCategoryReorderRequest request, Long adminId) {
        List<Long> categoryIds = request.categoryIds();
        List<ItemCategory> allCategories = repository.findAllByOrderBySortOrderAscIdAsc();
        requireSameCategories(categoryIds, allCategories);

        Map<Long, ItemCategory> categoryById = new HashMap<>();
        for (ItemCategory category : allCategories) {
            categoryById.put(category.getId(), category);
        }

        int position = 1;
        for (Long categoryId : categoryIds) {
            categoryById.get(categoryId).updateSortOrder(position);
            position++;
        }
        repository.saveAllAndFlush(allCategories);

        // Sắp xếp tác động cả danh sách nên không có targetId; lưu thứ tự mới vào data.
        activityLogService.log(
                ActivityType.CATEGORY_REORDER.name(),
                adminId,
                LOG_TARGET_TYPE,
                null,
                Map.<String, Object>of("categoryIds", categoryIds),
                null);
        return listAll();
    }

    // Danh mục mới nằm cuối danh sách: lớn hơn thứ tự lớn nhất hiện có một đơn vị (chưa có danh mục nào thì là 1).
    private int nextSortOrder() {
        return repository
                .findFirstByOrderBySortOrderDesc()
                .map(category -> category.getSortOrder() + 1)
                .orElse(1);
    }

    // Danh sách gửi lên phải trùng đúng tập id đang có: không trùng, không thiếu, không thừa.
    private void requireSameCategories(List<Long> categoryIds, List<ItemCategory> allCategories) {
        Set<Long> existingIds = new HashSet<>();
        for (ItemCategory category : allCategories) {
            existingIds.add(category.getId());
        }
        Set<Long> requestedIds = new HashSet<>(categoryIds);
        boolean hasDuplicate = requestedIds.size() != categoryIds.size();
        if (hasDuplicate || !requestedIds.equals(existingIds)) {
            throw new BusinessException(CatalogErrorCode.CATALOG_INVALID_SORT_ORDER);
        }
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
