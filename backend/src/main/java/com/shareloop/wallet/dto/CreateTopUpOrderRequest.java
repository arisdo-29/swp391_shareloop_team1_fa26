package com.shareloop.wallet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Yêu cầu tạo đơn nạp (POST /api/v1/wallet/topup-orders). Ở đây chỉ chặn thiếu và số không dương; mức tối thiểu và
 * bội số đọc từ ConfigService nên PaymentService mới kiểm được.
 *
 * @param amountVnd số tiền nạp (VNĐ)
 */
public record CreateTopUpOrderRequest(
        @NotNull(message = "Số tiền nạp không được để trống") @Positive(message = "Số tiền nạp phải lớn hơn 0")
        Long amountVnd) {}
