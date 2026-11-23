package com.shareloop.request.dto;

import com.shareloop.request.entity.CancelRequestCode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO đề nghị huỷ giao dịch đã chốt lịch trước giờ hẹn (GN-06).
 *
 * @param code mã lý do: BUSY, NO_LONGER_NEEDED hoặc OTHER
 * @param reason chữ người dùng nhập, bắt buộc từ 5 ký tự khi chọn OTHER; tối đa 500 ký tự
 */
public record CancelRequestRequest(
        @NotNull(message = "Vui lòng chọn lý do huỷ") CancelRequestCode code,
        @Size(max = 500, message = "Lý do tối đa 500 ký tự") String reason) {}
