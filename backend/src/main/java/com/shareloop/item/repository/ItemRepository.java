package com.shareloop.item.repository;

import com.shareloop.item.entity.Item;
import com.shareloop.item.entity.ItemCondition;
import com.shareloop.item.entity.ItemStatus;
import com.shareloop.item.entity.OfferType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemRepository extends JpaRepository<Item, Long> {

    /**
     * Điều kiện chung của trang Tìm đồ: bài APPROVED, không ẩn, chưa xoá, còn hạn hiển thị, và khớp các bộ lọc tuỳ chọn
     * (danh mục, khu vực, tình trạng, hình thức, từ khoá không dấu theo tên bài). Bộ lọc null thì bỏ qua. Viết một lần
     * để các query đếm, lấy bài đẩy và lấy bài thường luôn lọc giống hệt nhau.
     */
    String PUBLIC_BROWSE_FILTER = " i.status = 'APPROVED' AND i.is_active AND NOT i.is_deleted "
            + "AND (i.expire_at IS NULL OR i.expire_at > :now) "
            + "AND (CAST(:categoryId AS bigint) IS NULL OR i.category_id = :categoryId) "
            + "AND (CAST(:areaId AS bigint) IS NULL OR i.area_id = :areaId) "
            + "AND (CAST(:condition AS varchar) IS NULL OR i.condition = :condition) "
            + "AND (CAST(:offerType AS varchar) IS NULL OR i.offer_type = :offerType) "
            + "AND (CASE WHEN f_unaccent(lower(:q)) ~ '^[a-z0-9 ]+$' THEN "
            + "(' ' || regexp_replace(f_unaccent(lower(i.title)), '[^a-z0-9]+', ' ', 'g') || ' ') LIKE "
            + "'% ' || trim(regexp_replace(f_unaccent(lower(:q)), '[^a-z0-9]+', ' ', 'g')) || ' %' "
            + "ELSE f_unaccent(lower(i.title)) LIKE "
            + "'%' || replace(replace(replace(f_unaccent(lower(:q)), '!', '!!'), '%', '!%'), '_', '!_') || '%' ESCAPE '!' END) ";

    /**
     * Id giả truyền vào {@link #findPublicBrowseItemsExcluding} khi không cần loại bài nào: PostgreSQL báo lỗi cú pháp
     * với "NOT IN ()" rỗng, còn id -1 không bao giờ trùng bài thật (id sinh từ 1) nên không loại nhầm.
     */
    long NO_ITEM_ID = -1L;

    @Query(
            value = "SELECT count(*) FROM items i WHERE" + PUBLIC_BROWSE_FILTER
                    + "AND (:mode = 0 OR (:mode = 1 AND i.boosted_until > :now) "
                    + "OR (:mode = 2 AND (i.boosted_until IS NULL OR i.boosted_until <= :now)))",
            nativeQuery = true)
    long countPublicBrowseItems(
            Long categoryId, Long areaId, String condition, String offerType, String q, int mode, Instant now);

    @Query(
            value = "SELECT i.category_id, count(*) FROM items i "
                    + "WHERE i.status = 'APPROVED' AND i.is_active AND NOT i.is_deleted "
                    + "AND (i.expire_at IS NULL OR i.expire_at > :now) GROUP BY i.category_id",
            nativeQuery = true)
    List<Object[]> countPublicItemsByCategory(Instant now);

    @Query(
            value = "SELECT i.* FROM items i WHERE i.status = 'APPROVED' AND i.is_active AND NOT i.is_deleted "
                    + "AND (i.expire_at IS NULL OR i.expire_at > :now) "
                    + "AND (CAST(:categoryId AS bigint) IS NULL OR i.category_id = :categoryId) "
                    + "AND (CAST(:areaId AS bigint) IS NULL OR i.area_id = :areaId) "
                    + "AND (CAST(:condition AS varchar) IS NULL OR i.condition = :condition) "
                    + "AND (CAST(:offerType AS varchar) IS NULL OR i.offer_type = :offerType) "
                    + "AND (CASE WHEN f_unaccent(lower(:q)) ~ '^[a-z0-9 ]+$' THEN "
                    + "(' ' || regexp_replace(f_unaccent(lower(i.title)), '[^a-z0-9]+', ' ', 'g') || ' ') LIKE "
                    + "'% ' || trim(regexp_replace(f_unaccent(lower(:q)), '[^a-z0-9]+', ' ', 'g')) || ' %' "
                    + "ELSE f_unaccent(lower(i.title)) LIKE "
                    + "'%' || replace(replace(replace(f_unaccent(lower(:q)), '!', '!!'), '%', '!%'), '_', '!_') || '%' ESCAPE '!' END) "
                    + "AND (:mode = 0 OR (:mode = 1 AND i.boosted_until > :now) "
                    + "OR (:mode = 2 AND (i.boosted_until IS NULL OR i.boosted_until <= :now))) "
                    + "ORDER BY CASE WHEN :sort = 'expiring' THEN i.expire_at END ASC NULLS LAST, "
                    + "CASE WHEN :sort = 'newest' AND :mode = 1 THEN i.last_boosted_at END DESC NULLS LAST, "
                    + "i.created_at DESC, i.id DESC "
                    + "LIMIT :limit OFFSET :offset",
            nativeQuery = true)
    List<Item> findPublicBrowseItems(
            Long categoryId,
            Long areaId,
            String condition,
            String offerType,
            String q,
            int mode,
            Instant now,
            String sort,
            int limit,
            long offset);

    /**
     * Vòng xoay bài đẩy của trang Tìm đồ: id các bài đang đẩy (boosted_until > now) khớp bộ lọc, sắp theo id tăng dần.
     * Thứ tự cố định theo id (không theo lúc mua) để vòng xoay chia lượt đều, người đẩy sau không đè người đẩy trước.
     */
    @Query(
            value = "SELECT i.id FROM items i WHERE" + PUBLIC_BROWSE_FILTER
                    + "AND i.boosted_until > :now ORDER BY i.id ASC",
            nativeQuery = true)
    List<Long> findPublicBoostedIds(
            Long categoryId, Long areaId, String condition, String offerType, String q, Instant now);

    /**
     * Phần danh sách thường của trang Tìm đồ: mọi bài khớp bộ lọc (kể cả bài đang đẩy) trừ các id trong
     * {@code excludedIds}, sắp theo {@code sort} ("expiring": sắp hết hạn trước; còn lại: mới đăng trước). Bài đẩy
     * không được xếp lên đầu trang vẫn nằm ở đây theo thứ tự bình thường nên không biến mất khỏi kết quả.
     *
     * <p>{@code excludedIds} không được rỗng; không loại bài nào thì truyền {@code List.of(NO_ITEM_ID)}.
     */
    @Query(
            value = "SELECT i.* FROM items i WHERE" + PUBLIC_BROWSE_FILTER
                    + "AND i.id NOT IN (:excludedIds) "
                    + "ORDER BY CASE WHEN :sort = 'expiring' THEN i.expire_at END ASC NULLS LAST, "
                    + "i.created_at DESC, i.id DESC "
                    + "LIMIT :limit OFFSET :offset",
            nativeQuery = true)
    List<Item> findPublicBrowseItemsExcluding(
            Long categoryId,
            Long areaId,
            String condition,
            String offerType,
            String q,
            Instant now,
            Collection<Long> excludedIds,
            String sort,
            int limit,
            long offset);

    List<Item> findByDonorIdOrderByCreatedAtDesc(Long donorId);

    /**
     * Bài đang hiển thị công khai của một người (tab "Đang hoạt động" ở hồ sơ): cùng điều kiện với trang Tìm đồ
     * (APPROVED, không ẩn, chưa xoá, còn hạn), mới nhất trước. Native vì expire_at do module listingfee map, entity Item
     * không có cột này.
     */
    @Query(
            value = "SELECT i.* FROM items i WHERE i.donor_id = :donorId AND i.status = 'APPROVED' "
                    + "AND i.is_active AND NOT i.is_deleted AND (i.expire_at IS NULL OR i.expire_at > :now) "
                    + "ORDER BY i.created_at DESC, i.id DESC",
            countQuery = "SELECT count(*) FROM items i WHERE i.donor_id = :donorId AND i.status = 'APPROVED' "
                    + "AND i.is_active AND NOT i.is_deleted AND (i.expire_at IS NULL OR i.expire_at > :now)",
            nativeQuery = true)
    Page<Item> findPublicActiveByDonor(@Param("donorId") Long donorId, @Param("now") Instant now, Pageable pageable);

    /** Bài theo chủ bài và trạng thái, không bị ẩn, có phân trang (tab "Đã giao dịch" ở hồ sơ dùng TRADED). */
    Page<Item> findByDonorIdAndStatusAndIsActiveTrue(Long donorId, ItemStatus status, Pageable pageable);

    List<Item> findByDonorIdAndIsActiveTrue(Long donorId);

    List<Item> findByDonorIdAndStatusAndIsActiveTrue(Long donorId, ItemStatus status);

    long countByDonorIdAndStatusAndIsActiveTrue(Long donorId, ItemStatus status);

    long countByDonorIdAndStatusInAndIsActiveTrue(Long donorId, List<ItemStatus> statuses);

    long countByDonorIdAndOfferTypeAndStatusInAndIsActiveTrue(
            Long donorId, OfferType offerType, List<ItemStatus> statuses);

    @Query(
            value = "SELECT count(*) FROM items i WHERE i.donor_id = :donorId AND i.offer_type = :offerType "
                    + "AND i.is_active AND NOT i.is_deleted "
                    + "AND (CAST(:excludedItemId AS bigint) IS NULL OR i.id <> :excludedItemId) "
                    + "AND (i.status = 'PENDING_REVIEW' OR (i.status = 'APPROVED' "
                    + "AND (i.expire_at IS NULL OR i.expire_at > :now)))",
            nativeQuery = true)
    long countActivePostingItems(Long donorId, String offerType, Long excludedItemId, Instant now);

    @Query("select i from Item i where i.status = :status and i.isActive = true "
            + "and (:categoryId is null or i.categoryId = :categoryId) "
            + "and (:areaId is null or i.areaId = :areaId) "
            + "and (:condition is null or i.condition = :condition) "
            + "and (:offerType is null or i.offerType = :offerType)")
    Page<Item> findVisibleByFilters(
            @Param("status") ItemStatus status,
            @Param("categoryId") Long categoryId,
            @Param("areaId") Long areaId,
            @Param("condition") ItemCondition condition,
            @Param("offerType") OfferType offerType,
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
    Page<Item> searchVisibleByFilters(
            @Param("categoryId") Long categoryId,
            @Param("areaId") Long areaId,
            @Param("condition") String condition,
            @Param("offerType") String offerType,
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
