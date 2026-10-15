package com.shareloop.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Body của POST /api/v1/auth/change-password. Mật khẩu mới theo đúng luật của đăng ký (@ValidPassword). */
public record ChangePasswordRequest(
        @NotBlank String currentPassword, @ValidPassword String newPassword) {}
