package com.shareloop.listingfee.repository;

import com.shareloop.listingfee.entity.ItemFee;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemFeeRepository extends JpaRepository<ItemFee, Long> {

    /**
     * Đọc cột phí của bài và khoá dòng items đến hết transaction (SELECT ... FOR UPDATE). PostFeeService và
     * RenewService luôn gọi SAU khi CreditService đã khoá dòng users, để mọi luồng khoá theo cùng thứ tự users →
     * items. ItemExpiryService chỉ khoá dòng items, không khoá users, nên không tạo vòng chờ với các luồng trên.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from ItemFee f where f.id = :id")
    Optional<ItemFee> lockById(@Param("id") Long id);

    /**
     * Đọc nhẹ chủ bài và cột phí, không khoá, không tạo entity managed; rỗng khi không có bài hoặc bài đã xoá mềm.
     * Dùng native query vì donor_id đã do entity Item (module item) map, ItemFee không được map lại cột này nên JPQL
     * không với tới được. Alias để trong ngoặc kép vì PostgreSQL tự hạ chữ thường alias không quote.
     *
     * <p>approved_at chỉ cần biết có hay không nên trả sẵn boolean (everApproved); cột này thuộc module item/review
     * nên ItemFee cũng không map. status, category_id, is_active cũng thuộc module item, chỉ đọc để kiểm điều kiện
     * gia hạn và đẩy bài; offer_type cũng vậy, chỉ đọc để biết bài có mất phí đăng không (chỉ SWAP mất phí).
     */
    @Query(
            value = "SELECT donor_id AS \"donorId\", fee_state AS \"feeState\", pending_fee AS \"pendingFee\","
                    + " pending_fee_type AS \"pendingFeeType\", (approved_at IS NOT NULL) AS \"everApproved\","
                    + " free_edit_used AS \"freeEditUsed\", status AS \"status\","
                    + " category_id AS \"categoryId\", is_active AS \"active\", offer_type AS \"offerType\""
                    + " FROM items WHERE id = :itemId AND is_deleted = false",
            nativeQuery = true)
    Optional<ItemFeeSnapshot> findFeeSnapshotById(@Param("itemId") Long itemId);

    /**
     * Id các bài APPROVED đã quá expire_at (job hết hạn bài, FR-29, BR-D03), không khoá. Native query vì cột status
     * do entity Item (module item) map, ItemFee không map lại được. Phân trang kiểu keyset (id > afterId, tăng dần):
     * người gọi truyền id cuối của lô trước, nên một bài xử lý lỗi không bị lấy lại mãi trong cùng một lượt.
     */
    @Query(
            value = "SELECT id FROM items"
                    + " WHERE status = 'APPROVED' AND expire_at < :now AND is_deleted = false AND id > :afterId"
                    + " ORDER BY id LIMIT :limit",
            nativeQuery = true)
    List<Long> findExpiredApprovedItemIds(
            @Param("now") Instant now, @Param("afterId") long afterId, @Param("limit") int limit);

    /**
     * Hạn hiển thị và hạn đẩy của nhiều bài trong một lần đọc, không khoá. Dùng khi module item trả chi tiết hoặc
     * danh sách bài: một query cho cả trang thay vì một query mỗi bài. Không lọc is_deleted vì người gọi đã lọc bài
     * khi đọc danh sách. Bài không có trong kết quả thì người gọi coi như cả hai cột null.
     */
    @Query(
            value = "SELECT id AS \"id\", expire_at AS \"expireAt\", boosted_until AS \"boostedUntil\""
                    + " FROM items WHERE id IN (:ids)",
            nativeQuery = true)
    List<ItemDisplayWindowRow> findDisplayWindowRows(@Param("ids") Collection<Long> ids);
}
