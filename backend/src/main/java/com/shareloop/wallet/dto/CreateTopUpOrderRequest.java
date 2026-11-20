package com.shareloop.wallet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Yêu cầu tạo đơn nạp (POST /api/v1/wallet/topup-orders). Ở đây chỉ chặn thiếu và số không dương; mức tối thiểu và
 * bội số đọc từ ConfigService nên PaymentService mới kiểm được.
 *
 * @param amountVnd số tiền nạp (VNĐ)
 * @param replacePending true: còn đơn đang chờ thì hủy đơn đó rồi tạo đơn mới trong cùng transaction ("Hủy đơn cũ &
 *     tạo đơn mới"); bỏ trống hoặc false: còn đơn đang chờ thì trả 409 WALLET_TOPUP_PENDING_EXISTS
 */
public record CreateTopUpOrderRequest(
        @NotNull(message = "Số tiền nạp không được để trống") @Positive(message = "Số tiền nạp phải lớn hơn 0")
        Long amountVnd,

        Boolean replacePending) {

    /** Giữ cách gọi cũ chỉ với số tiền (test, module khác). */
    public CreateTopUpOrderRequest(Long amountVnd) {
        this(amountVnd, null);
    }
}
