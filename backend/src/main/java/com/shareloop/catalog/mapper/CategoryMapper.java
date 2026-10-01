package com.shareloop.catalog.mapper;

import com.shareloop.catalog.dto.CategoryResponse;
import com.shareloop.catalog.entity.ItemCategory;
import org.springframework.stereotype.Component;

/** Entity sang DTO, viết tay (không dùng MapStruct). */
@Component
public class CategoryMapper {

    public CategoryResponse toResponse(ItemCategory category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getParentId(),
                category.isRestricted(),
                category.isBoostable(),
                category.getSortOrder());
    }
}
