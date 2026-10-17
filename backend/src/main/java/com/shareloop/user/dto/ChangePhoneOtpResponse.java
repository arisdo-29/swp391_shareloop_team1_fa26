package com.shareloop.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Thông tin phản hồi sau khi gửi OTP đổi số điện thoại thành công.
 *
 * @param email email nhận mã OTP
 * @param otpExpiresInSeconds thời gian mã OTP hết hạn (giây)
 * @param resendCooldownSeconds thời gian phải chờ trước khi được gửi lại mã (giây)
 */
@Schema(description = "Kết quả gửi mã OTP đổi số điện thoại")
public record ChangePhoneOtpResponse(
        @Schema(description = "Email nhận mã OTP", example = "user@example.com")
        String email,

        @Schema(description = "Thời gian OTP còn hiệu lực tính bằng giây", example = "300")
        int otpExpiresInSeconds,

        @Schema(description = "Thời gian chờ tối thiểu trước khi gửi lại tính bằng giây", example = "60")
        int resendCooldownSeconds) {}
