package com.shareloop.wallet.dto;

import com.shareloop.wallet.entity.CreditTxType;
import java.time.Instant;

/**
 * Một dòng sổ cái Credit trong trang Lịch sử giao dịch Credit của Admin.
 *
 * @param amount số Credit có dấu, dương là cộng, âm là trừ
 * @param balanceAfter số dư của người đó ngay sau dòng này
 * @param itemId bài liên quan, null với giao dịch không gắn bài
 * @param itemTitle tiêu đề bài liên quan, null khi không có hoặc bài đã mất
 * @param orderCode mã đơn nạp (với TOP_UP), null khi không có
 * @param createdByName họ tên Admin thực hiện (với ADMIN_ADJUST), null khi không có
 */
public record AdminLedgerRow(
        long id,
        Instant createdAt,
        long userId,
        String userName,
        String userEmail,
        CreditTxType type,
        int amount,
        long balanceAfter,
        Long itemId,
        String itemTitle,
        String orderCode,
        String note,
        String createdByName) {}
