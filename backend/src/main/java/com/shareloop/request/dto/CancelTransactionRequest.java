package com.shareloop.request.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO huỷ giao dịch sau khi chủ bài đã chọn người nhận.
 *
 * @param reason lý do huỷ, 5 đến 500 ký tự; gửi cho bên còn lại trong thông báo
 */
public record CancelTransactionRequest(
        @NotBlank(message = "Lý do huỷ không được để trống")
        @Size(min = 5, max = 500, message = "Lý do huỷ phải từ 5 đến 500 ký tự")
        String reason) {}
