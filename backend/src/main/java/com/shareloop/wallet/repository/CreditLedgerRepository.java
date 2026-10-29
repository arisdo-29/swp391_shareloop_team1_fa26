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
}
