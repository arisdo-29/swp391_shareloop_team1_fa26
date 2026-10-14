package com.shareloop.wallet.repository;

import com.shareloop.wallet.entity.CreditLedgerEntry;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Sổ cái chỉ thêm dòng mới (save) và đọc lịch sử; không có hàm sửa, xoá. */
public interface CreditLedgerRepository extends JpaRepository<CreditLedgerEntry, Long> {

    /**
     * Lịch sử ví của một người trong khoảng [from, to). Thứ tự sắp xếp truyền qua Pageable. Index
     * ix_ledger_user (user_id, created_at DESC) phục vụ đúng truy vấn này.
     */
    Page<CreditLedgerEntry> findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long userId, Instant from, Instant to, Pageable pageable);
}
