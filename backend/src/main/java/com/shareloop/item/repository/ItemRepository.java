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

    List<Item> findByDonorIdAndIsActiveTrue(Long donorId);

    long countByDonorIdAndStatusAndIsActiveTrue(Long donorId, ItemStatus status);

    long countByDonorIdAndStatusInAndIsActiveTrue(Long donorId, List<ItemStatus> statuses);

    @Query("select i from Item i where i.status = :status and i.isActive = true "
            + "and (:categoryId is null or i.categoryId = :categoryId) "
            + "and (:areaId is null or i.areaId = :areaId)")
    Page<Item> findVisibleByFilters(
            @Param("status") ItemStatus status,
            @Param("categoryId") Long categoryId,
            @Param("areaId") Long areaId,
            Pageable pageable);

    @Query(
            value = "SELECT i.* FROM items i WHERE i.status = 'APPROVED' AND i.is_active AND NOT i.is_deleted "
                    + "AND (CAST(:categoryId AS bigint) IS NULL OR i.category_id = :categoryId) "
                    + "AND (CAST(:areaId AS bigint) IS NULL OR i.area_id = :areaId) "
                    + "AND f_unaccent(lower(i.title || ' ' || i.description)) LIKE "
                    + "'%' || replace(replace(replace(f_unaccent(lower(:q)), '!', '!!'), '%', '!%'), '_', '!_') "
                    + "|| '%' ESCAPE '!' ORDER BY i.created_at DESC, i.id DESC",
            countQuery =
                    "SELECT count(*) FROM items i WHERE i.status = 'APPROVED' AND i.is_active AND NOT i.is_deleted "
                            + "AND (CAST(:categoryId AS bigint) IS NULL OR i.category_id = :categoryId) "
                            + "AND (CAST(:areaId AS bigint) IS NULL OR i.area_id = :areaId) "
                            + "AND f_unaccent(lower(i.title || ' ' || i.description)) LIKE "
                            + "'%' || replace(replace(replace(f_unaccent(lower(:q)), '!', '!!'), '%', '!%'), '_', '!_') "
                            + "|| '%' ESCAPE '!'",
            nativeQuery = true)
    Page<Item> searchVisibleByFilters(
            @Param("categoryId") Long categoryId,
            @Param("areaId") Long areaId,
            @Param("q") String q,
            Pageable pageable);

    @Query(
            value = "SELECT i.* FROM items i WHERE i.status = 'APPROVED' AND i.is_active AND NOT i.is_deleted "
                    + "AND (CAST(:categoryId AS bigint) IS NULL OR i.category_id = :categoryId) "
                    + "AND (CAST(:areaId AS bigint) IS NULL OR i.area_id = :areaId) "
                    + "AND (CAST(:condition AS varchar) IS NULL OR i.condition = :condition) "
                    + "AND (CAST(:offerType AS varchar) IS NULL OR i.offer_type = :offerType) "
                    + "AND f_unaccent(lower(i.title || ' ' || i.description)) LIKE "
                    + "'%' || replace(replace(replace(f_unaccent(lower(:q)), '!', '!!'), '%', '!%'), '_', '!_') "
                    + "|| '%' ESCAPE '!' ORDER BY i.created_at DESC, i.id DESC",
            countQuery =
                    "SELECT count(*) FROM items i WHERE i.status = 'APPROVED' AND i.is_active AND NOT i.is_deleted "
                            + "AND (CAST(:categoryId AS bigint) IS NULL OR i.category_id = :categoryId) "
                            + "AND (CAST(:areaId AS bigint) IS NULL OR i.area_id = :areaId) "
                            + "AND (CAST(:condition AS varchar) IS NULL OR i.condition = :condition) "
                            + "AND (CAST(:offerType AS varchar) IS NULL OR i.offer_type = :offerType) "
                            + "AND f_unaccent(lower(i.title || ' ' || i.description)) LIKE "
                            + "'%' || replace(replace(replace(f_unaccent(lower(:q)), '!', '!!'), '%', '!%'), '_', '!_') "
                            + "|| '%' ESCAPE '!'",
            nativeQuery = true)
    Page<Item> searchApprovedForAi(
            @Param("categoryId") Long categoryId,
            @Param("areaId") Long areaId,
            @Param("condition") String condition,
            @Param("offerType") String offerType,
            @Param("q") String q,
            Pageable pageable);
}
