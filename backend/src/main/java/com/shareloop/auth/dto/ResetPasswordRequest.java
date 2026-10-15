package com.shareloop.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Body của POST /api/v1/auth/reset-password. Mật khẩu mới theo đúng luật của đăng ký (@ValidPassword). */
public record ResetPasswordRequest(
        @NotBlank @Email String email,

        @NotBlank @Pattern(regexp = "^\\d{6}$", message = "Mã OTP gồm 6 chữ số.")
        String otp,

        @ValidPassword String newPassword) {}
