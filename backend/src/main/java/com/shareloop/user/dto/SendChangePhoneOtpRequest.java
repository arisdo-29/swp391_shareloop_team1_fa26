package com.shareloop.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Yêu cầu gửi mã OTP về email để đổi số điện thoại (FR-07, BR-U10).
 *
 * @param newPhone số điện thoại mới, bắt buộc 10 chữ số bắt đầu bằng 0 (hoặc +84)
 */
@Schema(description = "Yêu cầu cấp mã OTP qua email để đổi số điện thoại")
public record SendChangePhoneOtpRequest(
        @Schema(description = "Số điện thoại mới", example = "0901234567")
        @NotBlank(message = "Số điện thoại không được để trống")
        @Pattern(regexp = "^(0|\\+84)\\d{9}$", message = "Số điện thoại không đúng định dạng (10 chữ số)")
        String newPhone) {}
