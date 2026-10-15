package com.shareloop.wallet.dto;

import com.shareloop.wallet.entity.CreditTxType;

/**
 * Tổng Credit của một loại giao dịch trong báo cáo tài chính.
 *
 * @param type loại giao dịch
 * @param amount tổng số Credit, luôn không âm (với nhóm trừ ra đã đổi dấu)
 * @param entryCount số dòng credit_ledger được cộng vào tổng
 */
public record CreditTypeTotal(CreditTxType type, long amount, long entryCount) {}
