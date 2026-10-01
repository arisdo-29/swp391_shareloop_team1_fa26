package com.shareloop.catalog.service;

import com.shareloop.catalog.CatalogErrorCode;
import com.shareloop.catalog.dto.CategoryResponse;
import com.shareloop.catalog.mapper.CategoryMapper;
import com.shareloop.catalog.repository.ItemCategoryRepository;
import com.shareloop.common.exception.BusinessException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true) // service chỉ đọc: mở transaction read-only
public class CategoryService {

    private final ItemCategoryRepository repository;
    private final CategoryMapper mapper;

    public CategoryService(ItemCategoryRepository repository, CategoryMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /** Danh mục đang dùng, theo thứ tự hiển thị. */
    public List<CategoryResponse> listActive() {
        return repository.findByIsActiveTrueOrderBySortOrderAscIdAsc().stream()
                .map(mapper::toResponse)
                .toList();
    }

    /** Danh mục ẩn hoặc đã xoá mềm coi như không tồn tại. */
    public CategoryResponse getById(long id) {
        return repository
                .findByIdAndIsActiveTrue(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new BusinessException(CatalogErrorCode.CATALOG_CATEGORY_NOT_FOUND));
    }
}
