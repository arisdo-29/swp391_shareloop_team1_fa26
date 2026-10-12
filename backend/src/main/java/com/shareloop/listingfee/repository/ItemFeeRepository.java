package com.shareloop.listingfee.repository;

import com.shareloop.listingfee.entity.ItemFee;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemFeeRepository extends JpaRepository<ItemFee, Long> {

    /**
     * Đọc cột phí của bài và khoá dòng items đến hết transaction (SELECT ... FOR UPDATE). Chỉ PostFeeService gọi,
     * và luôn gọi SAU khi CreditService đã khoá dòng users, để mọi luồng khoá theo cùng thứ tự users → items.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from ItemFee f where f.id = :id")
    Optional<ItemFee> lockById(@Param("id") Long id);

    /**
     * Đọc nhẹ chủ bài và cột phí, không khoá, không tạo entity managed; rỗng khi không có bài hoặc bài đã xoá mềm.
     * Dùng native query vì donor_id đã do entity Item (module item) map, ItemFee không được map lại cột này nên JPQL
     * không với tới được. Alias để trong ngoặc kép vì PostgreSQL tự hạ chữ thường alias không quote.
     */
    @Query(
            value = "SELECT donor_id AS \"donorId\", fee_state AS \"feeState\", pending_fee AS \"pendingFee\","
                    + " pending_fee_type AS \"pendingFeeType\""
                    + " FROM items WHERE id = :itemId AND is_deleted = false",
            nativeQuery = true)
    Optional<ItemFeeSnapshot> findFeeSnapshotById(@Param("itemId") Long itemId);
}
