package com.shareloop.item.repository;

import com.shareloop.item.entity.Item;
import com.shareloop.item.entity.ItemStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemRepository extends JpaRepository<Item, Long> {

    List<Item> findByDonorIdOrderByCreatedAtDesc(Long donorId);

    long countByDonorIdAndStatusAndIsActiveTrue(Long donorId, ItemStatus status);

    @Query("select i from Item i where i.status = :status and i.isActive = true "
            + "and (:categoryId is null or i.categoryId = :categoryId) "
            + "and (:areaId is null or i.areaId = :areaId)")
    Page<Item> findVisibleByFilters(
            @Param("status") ItemStatus status,
            @Param("categoryId") Long categoryId,
            @Param("areaId") Long areaId,
            Pageable pageable);
}
