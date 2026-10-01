package com.shareloop.wallet.entity;

/**
 * Loại giao dịch của {@code credit_ledger.type} (V1__init.sql, ERD mục 7.6). Đúng 7 giá trị của CHECK trong DB.
 *
 * <p>Chỉ giao dịch làm đổi {@code credit_balance} mới ghi ledger. Giữ chỗ ({@code hold}) và nhả ({@code release})
 * chỉ đổi {@code users.held_credit} nên không có loại riêng.
 */
public enum CreditTxType {
    /** Nạp Credit qua VNPay (F04); amount dương, luôn kèm payment_order_id. */
    TOP_UP,
    /** Phí đăng bài, trừ khi Admin duyệt lần đầu (C01, C02). */
    POST_FEE,
    /** Phí sửa bài từ lần sửa có phí (C14b). */
    EDIT_FEE,
    /** Phí gia hạn thêm 30 ngày (C16), trừ ngay. */
    RENEW_FEE,
    /** Phí đẩy bài (C15), trừ ngay. */
    BOOST_FEE,
    /** Phí AI trả lời khi hết lượt miễn phí (C17), trừ ngay. */
    AI_SEARCH_FEE,
    /** Admin điều chỉnh tay (hoàn phí, sửa sai); bắt buộc có note và created_by. */
    ADMIN_ADJUST
}
