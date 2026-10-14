package com.shareloop.wallet.dto;

import com.shareloop.wallet.entity.PaymentOrderStatus;

/**
 * Kết quả xác nhận một đơn nạp.
 *
 * @param orderCode mã đơn
 * @param status trạng thái đơn sau khi xác nhận (SUCCESS)
 * @param credits số Credit của đơn
 * @param wallet ví sau khi cộng (hoặc ví hiện tại nếu đơn đã được cộng từ trước)
 */
public record TopUpConfirmResponse(String orderCode, PaymentOrderStatus status, int credits, WalletResponse wallet) {}
