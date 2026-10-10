package com.shareloop.wallet.repository;

import com.shareloop.wallet.entity.CreditLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

/** Sổ cái chỉ thêm dòng mới (save); chưa cần truy vấn riêng. Sẽ thêm derived query khi có API lịch sử giao dịch. */
public interface CreditLedgerRepository extends JpaRepository<CreditLedgerEntry, Long> {}
