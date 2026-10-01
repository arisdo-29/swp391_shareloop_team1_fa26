package com.shareloop.catalog.repository;

import com.shareloop.catalog.entity.ItemCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemCategoryRepository extends JpaRepository<ItemCategory, Long> {

    // Derived query: tên hàm là câu truy vấn. Dòng is_deleted = true đã bị @SQLRestriction loại.
    List<ItemCategory> findByIsActiveTrueOrderBySortOrderAscIdAsc();

    Optional<ItemCategory> findByIdAndIsActiveTrue(Long id);
}
