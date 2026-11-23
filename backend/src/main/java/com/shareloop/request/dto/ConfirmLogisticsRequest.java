package com.shareloop.request.dto;

import jakarta.validation.constraints.Size;

/**
 * DTO xác nhận lịch hẹn giao nhận và cam kết trách nhiệm (GN-02, GN-03).
 *
 * @param shippingAddress địa chỉ nhận hàng của chính người gọi khi Giao hàng, bỏ trống nếu đã nhập ở bước đề xuất
 */
public record ConfirmLogisticsRequest(
        Boolean agreeToWaiver,

        @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự.")
        String shippingAddress) {

    // Dạng 1 tham số (chưa có địa chỉ) để code và test đang gọi vẫn compile.
    public ConfirmLogisticsRequest(Boolean agreeToWaiver) {
        this(agreeToWaiver, null);
    }
}
