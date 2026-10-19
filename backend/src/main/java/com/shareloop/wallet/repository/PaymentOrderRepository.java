package com.shareloop.wallet.repository;

import com.shareloop.wallet.entity.PaymentOrder;
import com.shareloop.wallet.entity.PaymentOrderStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    /** Đọc nhẹ id, chủ đơn, trạng thái và số Credit theo mã đơn; không khoá, không tạo entity managed. */
    @Query(
            "select o.id as id, o.userId as userId, o.status as status, o.credits as credits, o.provider as provider from PaymentOrder o"
                    + " where o.orderCode = :orderCode")
    Optional<PaymentOrderSnapshot> findSnapshotByOrderCode(@Param("orderCode") String orderCode);

    @Query("select o.userId from PaymentOrder o where o.id = :id")
    Optional<Long> findUserIdById(@Param("id") Long id);

    /**
     * Đọc đơn và khoá dòng payment_orders đến hết transaction (SELECT ... FOR UPDATE). Luồng nạp và callback
     * luôn gọi SAU khi đã khoá dòng users, để mọi luồng khoá theo cùng thứ tự users → payment_orders.
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

    /**
     * Đổi hàng loạt các đơn đang {@code fromStatus} tạo trước {@code cutoff} sang {@code toStatus}, trả số dòng đã đổi.
     * Chỉ PaymentExpiryService gọi (PENDING → EXPIRED). Cột updated_at do trigger của DB tự cập nhật.
     *
     * <p>Chạy đồng thời với CreditService.topUp vẫn an toàn: UPDATE và SELECT ... FOR UPDATE cùng khoá dòng đơn, bên
     * sau chờ bên trước commit rồi PostgreSQL kiểm lại điều kiện status trên dữ liệu mới, nên đơn vừa SUCCESS không bị
     * chuyển EXPIRED và đơn vừa EXPIRED không được cộng tiền.
     */
    // @Modifying: báo Spring Data đây là câu UPDATE/DELETE (không phải SELECT) và trả về số dòng bị ảnh hưởng. Câu này
    // chạy thẳng xuống DB, bỏ qua các entity đang nằm trong persistence context, nên người gọi không nên đọc lại đơn
    // trong cùng transaction.
    @Modifying
    @Query("update PaymentOrder o set o.status = :toStatus where o.status = :fromStatus and o.createdAt < :cutoff")
    int updateStatusByStatusAndCreatedAtBefore(
            @Param("fromStatus") PaymentOrderStatus fromStatus,
            @Param("toStatus") PaymentOrderStatus toStatus,
            @Param("cutoff") Instant cutoff);
}
