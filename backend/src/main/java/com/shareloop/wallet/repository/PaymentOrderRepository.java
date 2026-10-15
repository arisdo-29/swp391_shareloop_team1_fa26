package com.shareloop.wallet.repository;

import com.shareloop.wallet.entity.PaymentOrder;
import com.shareloop.wallet.entity.PaymentOrderStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
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

    /**
     * Tổng hợp đơn nạp theo trạng thái, lọc theo thời điểm thanh toán paid_at trong [from, to). Lọc theo paid_at (lúc
     * tiền thật về) để đơn rơi cùng khoảng với dòng ledger TOP_UP ghi trong cùng transaction. coalesce đổi SUM của
     * tập rỗng (null) thành 0.
     */
    @Query("select count(o) as orderCount, coalesce(sum(o.amountVnd), 0) as amountVnd,"
            + " coalesce(sum(o.credits), 0) as credits from PaymentOrder o"
            + " where o.status = :status and o.paidAt >= :from and o.paidAt < :to")
    CashTotalsView sumByStatusAndPaidAt(
            @Param("status") PaymentOrderStatus status, @Param("from") Instant from, @Param("to") Instant to);
}
