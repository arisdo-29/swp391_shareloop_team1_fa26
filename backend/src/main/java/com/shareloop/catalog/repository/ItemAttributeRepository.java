package com.shareloop.catalog.repository;

import com.shareloop.catalog.entity.ItemAttribute;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemAttributeRepository extends JpaRepository<ItemAttribute, Long> {
    List<ItemAttribute> findByCategoryIdOrderBySortOrderAscIdAsc(Long categoryId);

    List<ItemAttribute> findByCategoryIdAndIsActiveTrueOrderBySortOrderAscIdAsc(Long categoryId);
}
