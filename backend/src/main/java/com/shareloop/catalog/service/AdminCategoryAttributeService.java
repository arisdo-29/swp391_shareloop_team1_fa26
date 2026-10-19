package com.shareloop.catalog.service;

import com.shareloop.audit.ActivityType;
import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.catalog.CatalogErrorCode;
import com.shareloop.catalog.CategoryAttributeErrorCode;
import com.shareloop.catalog.dto.AdminAttributeRequest;
import com.shareloop.catalog.dto.AdminAttributeResponse;
import com.shareloop.catalog.entity.AttributeDataType;
import com.shareloop.catalog.entity.ItemAttribute;
import com.shareloop.catalog.repository.ItemAttributeRepository;
import com.shareloop.catalog.repository.ItemCategoryRepository;
import com.shareloop.common.exception.BusinessException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminCategoryAttributeService {
    private static final String OPERATING_STATUS = "Trạng thái hoạt động";
    private static final String LOG_TARGET_TYPE = "CATEGORY_ATTRIBUTE";
    private final ItemAttributeRepository attributeRepository;
    private final ItemCategoryRepository categoryRepository;
    private final ActivityLogService activityLogService;

    public AdminCategoryAttributeService(
            ItemAttributeRepository attributeRepository,
            ItemCategoryRepository categoryRepository,
            ActivityLogService activityLogService) {
        this.attributeRepository = attributeRepository;
        this.categoryRepository = categoryRepository;
        this.activityLogService = activityLogService;
    }

    public List<AdminAttributeResponse> listAll(long categoryId) {
        requireCategory(categoryId);
        return attributeRepository.findByCategoryIdOrderBySortOrderAscIdAsc(categoryId).stream()
                .map(AdminCategoryAttributeService::toResponse)
                .toList();
    }

    /**
     * Thêm thuộc tính cho danh mục đang hoạt động và ghi nhật ký CATEGORY_ATTRIBUTE_CREATE (FR-115).
     *
     * @throws BusinessException CATALOG_CATEGORY_NOT_FOUND, CATALOG_INVALID_ATTRIBUTE_OPTIONS
     */
    @Transactional
    public AdminAttributeResponse create(long categoryId, AdminAttributeRequest request, Long adminId) {
        requireActiveCategory(categoryId);
        List<String> options = validateOptions(request);
        ItemAttribute attribute = new ItemAttribute(
                categoryId,
                request.name().trim(),
                request.dataType(),
                options,
                request.required(),
                request.sortOrder());
        ItemAttribute saved = attributeRepository.saveAndFlush(attribute);
        logAction(ActivityType.CATEGORY_ATTRIBUTE_CREATE, adminId, saved);
        return toResponse(saved);
    }

    /**
     * Sửa thuộc tính và ghi nhật ký CATEGORY_ATTRIBUTE_UPDATE (FR-115). Thuộc tính "Trạng thái hoạt động" bắt buộc
     * không được đổi tên, kiểu hay lựa chọn.
     *
     * @throws BusinessException CATALOG_CATEGORY_NOT_FOUND, CATALOG_ATTRIBUTE_NOT_FOUND, CATALOG_REQUIRED_ATTRIBUTE,
     *     CATALOG_INVALID_ATTRIBUTE_OPTIONS
     */
    @Transactional
    public AdminAttributeResponse update(
            long categoryId, long attributeId, AdminAttributeRequest request, Long adminId) {
        requireCategory(categoryId);
        ItemAttribute attribute = requireAttribute(categoryId, attributeId);
        List<String> options = validateOptions(request);
        if (isOperatingStatus(attribute)
                && (!OPERATING_STATUS.equals(request.name().trim())
                        || request.dataType() != AttributeDataType.SELECT
                        || !request.required()
                        || !attribute.getOptions().equals(options))) {
            throw new BusinessException(CategoryAttributeErrorCode.CATALOG_REQUIRED_ATTRIBUTE);
        }
        attribute.update(request.name().trim(), request.dataType(), options, request.required(), request.sortOrder());
        ItemAttribute saved = attributeRepository.saveAndFlush(attribute);
        logAction(ActivityType.CATEGORY_ATTRIBUTE_UPDATE, adminId, saved);
        return toResponse(saved);
    }

    /**
     * Bật/tắt thuộc tính và ghi nhật ký CATEGORY_ATTRIBUTE_TOGGLE (FR-115). Không được tắt thuộc tính bắt buộc.
     *
     * @throws BusinessException CATALOG_CATEGORY_NOT_FOUND, CATALOG_ATTRIBUTE_NOT_FOUND, CATALOG_REQUIRED_ATTRIBUTE
     */
    @Transactional
    public AdminAttributeResponse setActive(long categoryId, long attributeId, boolean active, Long adminId) {
        requireCategory(categoryId);
        ItemAttribute attribute = requireAttribute(categoryId, attributeId);
        if (!active && isOperatingStatus(attribute)) {
            throw new BusinessException(CategoryAttributeErrorCode.CATALOG_REQUIRED_ATTRIBUTE);
        }
        if (active) {
            requireActiveCategory(categoryId);
            attribute.activate();
        } else {
            attribute.deactivate();
        }
        ItemAttribute saved = attributeRepository.saveAndFlush(attribute);
        logAction(ActivityType.CATEGORY_ATTRIBUTE_TOGGLE, adminId, saved);
        return toResponse(saved);
    }

    private void logAction(ActivityType type, Long adminId, ItemAttribute attribute) {
        Map<String, Object> data = new HashMap<>();
        data.put("categoryId", attribute.getCategoryId());
        data.put("name", attribute.getName());
        data.put("active", attribute.isActive());
        activityLogService.log(type.name(), adminId, LOG_TARGET_TYPE, attribute.getId(), data, null);
    }

    private List<String> validateOptions(AdminAttributeRequest request) {
        List<String> options = request.options() == null
                ? List.of()
                : request.options().stream().map(String::trim).toList();
        if (request.dataType() == AttributeDataType.SELECT) {
            if (options.isEmpty()
                    || options.stream().anyMatch(String::isBlank)
                    || new HashSet<>(options).size() != options.size()) {
                throw invalidOptions();
            }
            return options;
        }
        if (!options.isEmpty()) {
            throw invalidOptions();
        }
        return List.of();
    }

    private boolean isOperatingStatus(ItemAttribute attribute) {
        return OPERATING_STATUS.equals(attribute.getName())
                && attribute.getDataType() == AttributeDataType.SELECT
                && attribute.isRequired();
    }

    private void requireCategory(long categoryId) {
        categoryRepository
                .findById(categoryId)
                .orElseThrow(() -> new BusinessException(CatalogErrorCode.CATALOG_CATEGORY_NOT_FOUND));
    }

    private void requireActiveCategory(long categoryId) {
        categoryRepository
                .findByIdAndIsActiveTrue(categoryId)
                .orElseThrow(() -> new BusinessException(CatalogErrorCode.CATALOG_CATEGORY_NOT_FOUND));
    }

    private ItemAttribute requireAttribute(long categoryId, long attributeId) {
        ItemAttribute attribute = attributeRepository
                .findById(attributeId)
                .orElseThrow(() -> new BusinessException(CategoryAttributeErrorCode.CATALOG_ATTRIBUTE_NOT_FOUND));
        if (!Objects.equals(attribute.getCategoryId(), categoryId)) {
            throw new BusinessException(CategoryAttributeErrorCode.CATALOG_ATTRIBUTE_NOT_FOUND);
        }
        return attribute;
    }

    private BusinessException invalidOptions() {
        return new BusinessException(
                CategoryAttributeErrorCode.CATALOG_INVALID_ATTRIBUTE_OPTIONS,
                CategoryAttributeErrorCode.CATALOG_INVALID_ATTRIBUTE_OPTIONS.defaultMessage(),
                "options",
                Map.of());
    }

    private static AdminAttributeResponse toResponse(ItemAttribute attribute) {
        return new AdminAttributeResponse(
                attribute.getId(),
                attribute.getCategoryId(),
                attribute.getName(),
                attribute.getDataType(),
                attribute.getOptions(),
                attribute.isRequired(),
                attribute.getSortOrder(),
                attribute.isActive());
    }
}
