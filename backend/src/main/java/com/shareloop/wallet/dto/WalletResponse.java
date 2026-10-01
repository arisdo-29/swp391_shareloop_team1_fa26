package com.shareloop.wallet.dto;

/**
 * Ví Credit của một người dùng.
 *
 * @param creditBalance tổng Credit đang có ({@code users.credit_balance})
 * @param heldCredit Credit đang bị giữ chờ duyệt ({@code users.held_credit})
 * @param available Credit dùng được = creditBalance - heldCredit
 * @param hasToppedUp đã từng nạp thành công (điều kiện mở hạn mức AI, C18)
 */
public record WalletResponse(long creditBalance, long heldCredit, long available, boolean hasToppedUp) {}
