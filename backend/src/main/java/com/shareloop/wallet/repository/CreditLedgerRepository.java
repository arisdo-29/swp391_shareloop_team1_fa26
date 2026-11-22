package com.shareloop.wallet.repository;

import com.shareloop.wallet.entity.CreditLedgerEntry;
import com.shareloop.wallet.entity.CreditTxType;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Sổ cái chỉ thêm dòng mới (save) và đọc lịch sử; không có hàm sửa, xoá. */
public interface CreditLedgerRepository extends JpaRepository<CreditLedgerEntry, Long> {

    // Điều kiện lọc chung của trang Lịch sử giao dịch Credit. allUsers = true bỏ qua userId. keywordPattern null là
    // không tìm; ngược lại khớp người (userIds, luôn có ít nhất một phần tử), ghi chú hoặc mã đơn nạp.
    String ADMIN_FILTER = " where (:type is null or l.type = :type)"
            + " and (:allUsers = true or l.userId = :userId)"
            + " and (:keywordPattern is null or l.userId in :userIds or lower(l.note) like :keywordPattern"
            + "   or l.paymentOrderId in (select o.id from PaymentOrder o where lower(o.orderCode) like :keywordPattern))"
            + " and l.createdAt >= :from and l.createdAt < :to";

    /** Sổ cái toàn hệ thống cho Admin, thứ tự theo pageable (mới nhất trước). */
    @Query("select l from CreditLedgerEntry l" + ADMIN_FILTER)
    Page<CreditLedgerEntry> findForAdmin(
            @Param("type") CreditTxType type,
            @Param("allUsers") boolean allUsers,
            @Param("userId") long userId,
            @Param("keywordPattern") String keywordPattern,
            @Param("userIds") java.util.Collection<Long> userIds,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    /** Tổng Credit cộng vào (dòng dương) trong cùng bộ lọc với {@link #findForAdmin}. */
    @Query("select coalesce(sum(l.amount), 0) from CreditLedgerEntry l" + ADMIN_FILTER + " and l.amount > 0")
    long sumInForAdmin(
            @Param("type") CreditTxType type,
            @Param("allUsers") boolean allUsers,
            @Param("userId") long userId,
            @Param("keywordPattern") String keywordPattern,
            @Param("userIds") java.util.Collection<Long> userIds,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /** Tổng Credit trừ ra (dòng âm, giữ dấu âm) trong cùng bộ lọc với {@link #findForAdmin}. */
    @Query("select coalesce(sum(l.amount), 0) from CreditLedgerEntry l" + ADMIN_FILTER + " and l.amount < 0")
    long sumOutForAdmin(
            @Param("type") CreditTxType type,
            @Param("allUsers") boolean allUsers,
            @Param("userId") long userId,
            @Param("keywordPattern") String keywordPattern,
            @Param("userIds") java.util.Collection<Long> userIds,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /**
     * Lịch sử ví của một người trong khoảng [from, to). Thứ tự sắp xếp truyền qua Pageable. Index
     * ix_ledger_user (user_id, created_at DESC) phục vụ đúng truy vấn này.
     */
    Page<CreditLedgerEntry> findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long userId, Instant from, Instant to, Pageable pageable);

    /** Đếm mọi dòng của một người trong khoảng [from, to); cùng điều kiện với hàm lấy trang ở trên. */
    long countByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(Long userId, Instant from, Instant to);

    /** Đếm dòng theo một type (dùng cho tab Nạp Credit: TOP_UP) của một người trong khoảng [from, to). */
    long countByUserIdAndTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long userId, CreditTxType type, Instant from, Instant to);

    /**
     * Đếm dòng chi tiêu của một người trong khoảng [from, to): khoản trừ Credit (amount âm) nhưng không tính Admin
     * điều chỉnh. Hoàn phí AI là dòng AI_SEARCH_FEE có amount dương nên tự nằm ngoài.
     */
    @Query("select count(l) from CreditLedgerEntry l where l.userId = :userId and l.amount < 0"
            + " and l.type <> :excludedType and l.createdAt >= :from and l.createdAt < :to")
    long countSpend(
            @Param("userId") Long userId,
            @Param("excludedType") CreditTxType excludedType,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /** Các dòng sổ cái gắn với một đơn nạp (thường là một dòng TOP_UP), dùng cho chi tiết đơn của Admin. */
    List<CreditLedgerEntry> findByPaymentOrderIdOrderByIdAsc(Long paymentOrderId);

    /** Đã có dòng với đúng note này chưa; dùng để chặn hoàn phí AI hai lần cho cùng một dòng gốc. */
    boolean existsByUserIdAndTypeAndNote(Long userId, CreditTxType type, String note);

    /**
     * Tổng Credit cộng vào (amount dương) của mọi người dùng trong khoảng [from, to), gom theo type. DB tự cộng
     * (SUM, GROUP BY), Java chỉ nhận vài dòng kết quả thay vì toàn bộ sổ cái.
     */
    @Query("select l.type as type, sum(l.amount) as total, count(l) as entryCount from CreditLedgerEntry l"
            + " where l.amount > 0 and l.createdAt >= :from and l.createdAt < :to"
            + " group by l.type order by l.type")
    List<CreditTypeTotalView> sumCreditInByType(@Param("from") Instant from, @Param("to") Instant to);

    /** Tổng Credit trừ ra (amount âm, giữ nguyên dấu âm) của mọi người dùng trong khoảng [from, to), gom theo type. */
    @Query("select l.type as type, sum(l.amount) as total, count(l) as entryCount from CreditLedgerEntry l"
            + " where l.amount < 0 and l.createdAt >= :from and l.createdAt < :to"
            + " group by l.type order by l.type")
    List<CreditTypeTotalView> sumCreditOutByType(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select coalesce(sum(l.amount), 0) from CreditLedgerEntry l
            join PaymentOrder o on o.id = l.paymentOrderId and o.userId = l.userId
            where l.type = com.shareloop.wallet.entity.CreditTxType.TOP_UP
              and o.status = com.shareloop.wallet.entity.PaymentOrderStatus.SUCCESS
              and o.paidAt >= :from and o.paidAt < :to
            """)
    long sumTopUpsByPaymentDate(Instant from, Instant to);

    @Query("""
            select count(l) as entryCount, coalesce(sum(l.amount), 0) as total
            from CreditLedgerEntry l
            where l.type = com.shareloop.wallet.entity.CreditTxType.TOP_UP
              and l.paymentOrderId is null and l.createdAt >= :from and l.createdAt < :to
            """)
    CreditTypeTotalView summarizeUnlinkedTopUps(Instant from, Instant to);

    @Query("""
            select l from CreditLedgerEntry l
            where l.type = com.shareloop.wallet.entity.CreditTxType.TOP_UP
              and l.paymentOrderId is null and l.createdAt >= :from and l.createdAt < :to
            order by l.createdAt desc, l.id desc
            """)
    List<CreditLedgerEntry> findUnlinkedTopUps(Instant from, Instant to, Pageable pageable);
}
