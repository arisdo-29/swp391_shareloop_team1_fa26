package com.shareloop.catalog.service;

import com.shareloop.catalog.CatalogErrorCode;
import com.shareloop.catalog.dto.AdminAttributeRequest;
import com.shareloop.catalog.dto.AdminAttributeResponse;
import com.shareloop.catalog.entity.AttributeDataType;
import com.shareloop.catalog.entity.ItemAttribute;
import com.shareloop.catalog.repository.ItemAttributeRepository;
import com.shareloop.catalog.repository.ItemCategoryRepository;
import com.shareloop.common.exception.BusinessException;
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
    private final ItemAttributeRepository attributeRepository;
    private final ItemCategoryRepository categoryRepository;

    public AdminCategoryAttributeService(
            ItemAttributeRepository attributeRepository, ItemCategoryRepository categoryRepository) {
        this.attributeRepository = attributeRepository;
        this.categoryRepository = categoryRepository;
    }

    public List<AdminAttributeResponse> listAll(long categoryId) {
        requireCategory(categoryId);
        return attributeRepository.findByCategoryIdOrderBySortOrderAscIdAsc(categoryId).stream()
                .map(AdminCategoryAttributeService::toResponse)
                .toList();
    }

    @Transactional
    public AdminAttributeResponse create(long categoryId, AdminAttributeRequest request) {
        requireActiveCategory(categoryId);
        List<String> options = validateOptions(request);
        ItemAttribute attribute = new ItemAttribute(
                categoryId,
                request.name().trim(),
                request.dataType(),
                options,
                request.required(),
                request.sortOrder());
        return toResponse(attributeRepository.saveAndFlush(attribute));
    }

    @Transactional
    public AdminAttributeResponse update(long categoryId, long attributeId, AdminAttributeRequest request) {
        requireCategory(categoryId);
        ItemAttribute attribute = requireAttribute(categoryId, attributeId);
        List<String> options = validateOptions(request);
        if (isOperatingStatus(attribute)
                && (!OPERATING_STATUS.equals(request.name().trim())
                        || request.dataType() != AttributeDataType.SELECT
                        || !request.required()
                        || !attribute.getOptions().equals(options))) {
            throw new BusinessException(CatalogErrorCode.CATALOG_REQUIRED_ATTRIBUTE);
        }
        attribute.update(request.name().trim(), request.dataType(), options, request.required(), request.sortOrder());
        return toResponse(attributeRepository.saveAndFlush(attribute));
    }

    @Transactional
    public AdminAttributeResponse setActive(long categoryId, long attributeId, boolean active) {
        requireCategory(categoryId);
        ItemAttribute attribute = requireAttribute(categoryId, attributeId);
        if (!active && isOperatingStatus(attribute)) {
            throw new BusinessException(CatalogErrorCode.CATALOG_REQUIRED_ATTRIBUTE);
        }
        if (active) {
            requireActiveCategory(categoryId);
            attribute.activate();
        } else {
            attribute.deactivate();
        }
        return toResponse(attributeRepository.saveAndFlush(attribute));
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
                .orElseThrow(() -> new BusinessException(CatalogErrorCode.CATALOG_ATTRIBUTE_NOT_FOUND));
        if (!Objects.equals(attribute.getCategoryId(), categoryId)) {
            throw new BusinessException(CatalogErrorCode.CATALOG_ATTRIBUTE_NOT_FOUND);
        }
        return attribute;
    }

    private BusinessException invalidOptions() {
        return new BusinessException(
                CatalogErrorCode.CATALOG_INVALID_ATTRIBUTE_OPTIONS,
                CatalogErrorCode.CATALOG_INVALID_ATTRIBUTE_OPTIONS.defaultMessage(),
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
