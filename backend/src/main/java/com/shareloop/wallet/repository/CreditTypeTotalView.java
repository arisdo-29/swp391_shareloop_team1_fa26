package com.shareloop.wallet.repository;

import com.shareloop.wallet.entity.CreditTxType;

/**
 * Một dòng kết quả gom nhóm credit_ledger theo type: tổng amount và số dòng. Interface projection: Spring Data tự tạo
 * đối tượng từ các cột có alias trùng tên getter (type, total, entryCount), không tải entity lên.
 */
public interface CreditTypeTotalView {

    CreditTxType getType();

    // Giữ nguyên dấu như trong sổ cái: nhóm trừ tiền sẽ là số âm.
    long getTotal();

    long getEntryCount();
}
