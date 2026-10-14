package com.shareloop.wallet.repository;

import com.shareloop.wallet.entity.PaymentOrder;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    /** Đọc nhẹ id, chủ đơn, trạng thái và số Credit theo mã đơn; không khoá, không tạo entity managed. */
    @Query("select o.id as id, o.userId as userId, o.status as status, o.credits as credits from PaymentOrder o"
            + " where o.orderCode = :orderCode")
    Optional<PaymentOrderSnapshot> findSnapshotByOrderCode(@Param("orderCode") String orderCode);

    /**
     * Đọc đơn và khoá dòng payment_orders đến hết transaction (SELECT ... FOR UPDATE). Chỉ CreditService.topUp gọi,
     * và luôn gọi SAU khi đã khoá dòng users, để mọi luồng khoá theo cùng thứ tự users → payment_orders.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PaymentOrder o where o.id = :id")
    Optional<PaymentOrder> lockById(@Param("id") Long id);
}
