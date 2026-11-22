package com.shareloop.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Admin xử lý một đơn cần đối soát.
 *
 * @param action GRANT_CREDIT: xác nhận đã thu tiền và cộng Credit; REFUNDED_OUTSIDE: đã hoàn tiền ngoài hệ thống
 * @param note lý do hoặc ghi chú, 5 đến 500 ký tự
 */
public record ReconcileTopUpRequest(
        @NotNull Action action,
        @NotBlank @Size(min = 5, max = 500) String note) {

    /** Hai cách xử lý đơn cần đối soát. */
    public enum Action {
        GRANT_CREDIT,
        REFUNDED_OUTSIDE
    }
}
