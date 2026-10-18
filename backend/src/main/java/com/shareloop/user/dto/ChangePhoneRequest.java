package com.shareloop.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Yêu cầu xác nhận OTP và đổi số điện thoại (FR-07, BR-U10).
 *
 * @param newPhone số điện thoại mới, bắt buộc 10 chữ số
 * @param otp mã OTP 6 chữ số đã gửi về email
 */
@Schema(description = "Yêu cầu xác thực OTP và đổi số điện thoại")
public record ChangePhoneRequest(
        @Schema(description = "Số điện thoại mới", example = "0901234567")
        @NotBlank(message = "Số điện thoại không được để trống")
        @Pattern(regexp = "^(0|\\+84)\\d{9}$", message = "Số điện thoại không đúng định dạng (10 chữ số)")
        String newPhone,

        @Schema(description = "Mã OTP 6 chữ số gửi qua email", example = "123456")
        @NotBlank(message = "Mã OTP không được để trống")
        @Pattern(regexp = "^\\d{6}$", message = "Mã OTP phải gồm 6 chữ số")
        String otp) {}
