package com.shareloop.wallet.dto;

import com.shareloop.wallet.entity.CreditTxType;
import java.time.Instant;

/**
 * Một dòng lịch sử ví (một dòng credit_ledger).
 *
 * @param id id dòng sổ cái
 * @param type loại giao dịch
 * @param amount số Credit: dương là cộng, âm là trừ
 * @param balanceAfter số dư ngay sau giao dịch
 * @param itemId bài liên quan (với các loại phí bài đăng), có thể null
 * @param paymentOrderId đơn nạp liên quan (với TOP_UP), có thể null
 * @param note ghi chú (với ADMIN_ADJUST), có thể null
 * @param createdAt thời điểm giao dịch (UTC)
 */
public record WalletTransactionResponse(
        Long id,
        CreditTxType type,
        int amount,
        long balanceAfter,
        Long itemId,
        Long paymentOrderId,
        String note,
        Instant createdAt) {}
