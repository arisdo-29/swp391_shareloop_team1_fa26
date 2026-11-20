package com.shareloop.wallet.repository;

import com.shareloop.wallet.entity.PaymentOrder;
import com.shareloop.wallet.entity.PaymentOrderStatus;
import com.shareloop.wallet.entity.PaymentProvider;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    /** Danh sách đơn nạp cho Admin; provider hoặc status null nghĩa là không lọc theo tiêu chí đó. */
    @Query("select o from PaymentOrder o where (:provider is null or o.provider = :provider)"
            + " and (:status is null or o.status = :status) and (:submittedOnly = false or o.submittedAt is not null)")
    org.springframework.data.domain.Page<PaymentOrder> findOrders(
            @Param("provider") PaymentProvider provider,
            @Param("status") PaymentOrderStatus status,
            @Param("submittedOnly") boolean submittedOnly,
            org.springframework.data.domain.Pageable pageable);

    /** Đọc nhẹ id, chủ đơn, trạng thái và số Credit theo mã đơn; không khoá, không tạo entity managed. */
    @Query(
            "select o.id as id, o.userId as userId, o.status as status, o.credits as credits, o.provider as provider from PaymentOrder o"
                    + " where o.orderCode = :orderCode")
    Optional<PaymentOrderSnapshot> findSnapshotByOrderCode(@Param("orderCode") String orderCode);

    // Đọc đơn theo mã để người dùng xem đơn của mình (B29); không khoá.
    Optional<PaymentOrder> findByOrderCode(String orderCode);

    @Query("select o.userId from PaymentOrder o where o.id = :id")
    Optional<Long> findUserIdById(@Param("id") Long id);

    /**
     * Chuyển một đơn PENDING đã quá hạn sang EXPIRED khi người dùng xem đơn, không chờ PaymentExpiryJob. Người gọi đã
     * kiểm mốc đóng hẳn (PaymentDeadline.closesAt); câu UPDATE có điều kiện status = PENDING nên IPN vừa cộng tiền xong
     * (SUCCESS) hay đơn đã hủy thì không bị đổi.
     *
     * @return 1 nếu vừa chuyển EXPIRED, 0 nếu đơn không còn PENDING
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PaymentOrder o set o.status = com.shareloop.wallet.entity.PaymentOrderStatus.EXPIRED"
            + " where o.id = :id and o.status = com.shareloop.wallet.entity.PaymentOrderStatus.PENDING")
    int expireIfPending(@Param("id") Long id);

    /**
     * Người dùng tự hủy đơn nạp: chỉ đổi khi đơn còn PENDING và đúng chủ đơn, trong một câu UPDATE có điều kiện nên gọi
     * lại nhiều lần cũng chỉ đổi một lần (idempotent). IPN đang giữ khoá dòng thì câu này chờ; IPN cộng tiền xong (đơn
     * SUCCESS) thì câu này không đổi gì. Ghi lý do vào review_note (cột ghi chú xử lý đơn, không còn dùng cho duyệt tay).
     *
     * @return 1 nếu vừa hủy, 0 nếu đơn không còn PENDING, không có hoặc của người khác
     */
    // clearAutomatically: bỏ entity cũ khỏi persistence context để lần đọc sau trong cùng transaction thấy trạng thái
    // mới.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PaymentOrder o set o.status = com.shareloop.wallet.entity.PaymentOrderStatus.FAILED,"
            + " o.reviewNote = :reason where o.orderCode = :orderCode and o.userId = :userId"
            + " and o.status = com.shareloop.wallet.entity.PaymentOrderStatus.PENDING")
    int cancelPending(
            @Param("orderCode") String orderCode, @Param("userId") long userId, @Param("reason") String reason);

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
     * Tổng Credit đã nạp (đơn có trạng thái {@code status}, thường là SUCCESS) của từng người dùng trong danh sách.
     * Người không có đơn nào sẽ không xuất hiện trong kết quả, nghĩa là tổng bằng 0.
     */
    @Query("select o.userId as userId, sum(o.credits) as credits from PaymentOrder o"
            + " where o.status = :status and o.userId in :userIds group by o.userId")
    List<UserTopUpTotalView> sumCreditsByUser(
            @Param("status") PaymentOrderStatus status, @Param("userIds") Collection<Long> userIds);

    /**
     * Thời điểm thanh toán và số tiền của từng đơn đã thanh toán trong [from, to), cũ nhất trước. Việc gom theo ngày
     * làm ở Java vì "ngày" phải tính theo giờ Việt Nam, còn paid_at lưu theo UTC.
     */
    @Query("select o.paidAt as paidAt, o.amountVnd as amountVnd from PaymentOrder o"
            + " where o.status = :status and o.paidAt >= :from and o.paidAt < :to order by o.paidAt")
    List<PaidOrderView> findPaidOrders(
            @Param("status") PaymentOrderStatus status, @Param("from") Instant from, @Param("to") Instant to);

    /**
     * Danh sách các đơn nạp thành công trong khoảng [from, to), sắp xếp theo thời điểm thanh toán cũ nhất trước (L02).
     */
    @Query("select o from PaymentOrder o where o.status = :status and o.paidAt >= :from and o.paidAt < :to"
            + " order by o.paidAt asc, o.id asc")
    List<PaymentOrder> findSuccessfulOrders(
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
    @Query("update PaymentOrder o set o.status = :toStatus where o.status = :fromStatus and o.createdAt < :cutoff"
            + " and (o.provider <> com.shareloop.wallet.entity.PaymentProvider.MANUAL or o.submittedAt is null)")
    int updateStatusByStatusAndCreatedAtBefore(
            @Param("fromStatus") PaymentOrderStatus fromStatus,
            @Param("toStatus") PaymentOrderStatus toStatus,
            @Param("cutoff") Instant cutoff);
}
