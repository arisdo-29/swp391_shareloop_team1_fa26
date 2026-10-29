package com.shareloop.catalog.service;

import com.shareloop.catalog.CatalogErrorCode;
import com.shareloop.catalog.dto.CategoryResponse;
import com.shareloop.catalog.mapper.CategoryMapper;
import com.shareloop.catalog.repository.ItemCategoryRepository;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.service.ItemService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true) // service chỉ đọc: mở transaction read-only
public class CategoryService {

    private final ItemCategoryRepository repository;
    private final CategoryMapper mapper;
    private final ItemService itemService;

    public CategoryService(ItemCategoryRepository repository, CategoryMapper mapper, @Lazy ItemService itemService) {
        this.repository = repository;
        this.mapper = mapper;
        this.itemService = itemService;
    }

    /** Danh mục đang dùng, theo thứ tự hiển thị. */
    public List<CategoryResponse> listActive() {
        Map<Long, Long> counts = itemService.countPublicItemsByCategory();
        return repository.findByIsActiveTrueOrderBySortOrderAscIdAsc().stream()
                .map(category -> mapper.toResponse(category, counts.getOrDefault(category.getId(), 0L)))
                .toList();
    }

    /**
     * Tên danh mục theo id cho trang Admin. Khác getById: danh mục đang ẩn vẫn có tên, vì bài cũ có thể còn thuộc
     * danh mục đã ẩn; chỉ danh mục đã xoá mềm mới trả rỗng.
     */
    public Optional<String> findName(long id) {
        return repository.findById(id).map(category -> category.getName());
    }

    /** Danh mục ẩn hoặc đã xoá mềm coi như không tồn tại. */
    public CategoryResponse getById(long id) {
        var category = repository
                .findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new BusinessException(CatalogErrorCode.CATALOG_CATEGORY_NOT_FOUND));
        return mapper.toResponse(
                category, itemService.countPublicItemsByCategory().getOrDefault(id, 0L));
    }
}
