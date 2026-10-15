package com.shareloop.catalog.service;

import com.shareloop.catalog.CatalogErrorCode;
import com.shareloop.catalog.dto.AdminCategoryRequest;
import com.shareloop.catalog.dto.AdminCategoryResponse;
import com.shareloop.catalog.entity.ItemCategory;
import com.shareloop.catalog.repository.ItemCategoryRepository;
import com.shareloop.common.exception.BusinessException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminCategoryService {
    private final ItemCategoryRepository repository;

    public AdminCategoryService(ItemCategoryRepository repository) {
        this.repository = repository;
    }

    public List<AdminCategoryResponse> listAll() {
        return repository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(AdminCategoryService::toResponse)
                .toList();
    }

    @Transactional
    public AdminCategoryResponse create(AdminCategoryRequest request) {
        validateParent(request.parentId(), null);
        ItemCategory category = new ItemCategory(
                request.name().trim(),
                request.parentId(),
                request.restricted(),
                request.boostable(),
                request.sortOrder());
        return toResponse(repository.saveAndFlush(category));
    }

    @Transactional
    public AdminCategoryResponse update(long id, AdminCategoryRequest request) {
        ItemCategory category = requireCategory(id);
        validateParent(request.parentId(), id);
        category.update(
                request.name().trim(),
                request.parentId(),
                request.restricted(),
                request.boostable(),
                request.sortOrder());
        return toResponse(repository.saveAndFlush(category));
    }

    @Transactional
    public AdminCategoryResponse setActive(long id, boolean active) {
        ItemCategory category = requireCategory(id);
        if (active) {
            validateParent(category.getParentId(), id);
            category.activate();
        } else {
            category.deactivate();
            hideActiveChildren(id);
        }
        return toResponse(repository.saveAndFlush(category));
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
