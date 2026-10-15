package com.shareloop.catalog.service;

import com.shareloop.catalog.CatalogErrorCode;
import com.shareloop.catalog.dto.AttributeResponse;
import com.shareloop.catalog.entity.ItemAttribute;
import com.shareloop.catalog.repository.ItemAttributeRepository;
import com.shareloop.catalog.repository.ItemCategoryRepository;
import com.shareloop.common.exception.BusinessException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Exposes active required attributes to item without sharing catalog repositories. */
@Service
public class CategoryAttributeService {
    private final JdbcClient jdbcClient;
    private final ItemAttributeRepository attributeRepository;
    private final ItemCategoryRepository categoryRepository;

    public CategoryAttributeService(
            JdbcClient jdbcClient,
            ItemAttributeRepository attributeRepository,
            ItemCategoryRepository categoryRepository) {
        this.jdbcClient = jdbcClient;
        this.attributeRepository = attributeRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<AttributeResponse> listPublic(long categoryId) {
        categoryRepository
                .findByIdAndIsActiveTrue(categoryId)
                .orElseThrow(() -> new BusinessException(CatalogErrorCode.CATALOG_CATEGORY_NOT_FOUND));
        return attributeRepository.findByCategoryIdAndIsActiveTrueOrderBySortOrderAscIdAsc(categoryId).stream()
                .map(CategoryAttributeService::toResponse)
                .toList();
    }

    private static AttributeResponse toResponse(ItemAttribute attribute) {
        return new AttributeResponse(
                attribute.getId(),
                attribute.getCategoryId(),
                attribute.getName(),
                attribute.getDataType(),
                attribute.getOptions(),
                attribute.isRequired(),
                attribute.getSortOrder());
    }

    @Transactional(readOnly = true)
    public Optional<Long> operatingStatusAttributeId(long categoryId) {
        return jdbcClient
                .sql("SELECT id FROM item_attributes WHERE category_id = :categoryId "
                        + "AND name = 'Trạng thái hoạt động' AND is_required AND is_active AND NOT is_deleted")
                .param("categoryId", categoryId)
                .query(Long.class)
                .optional();
    }
}
