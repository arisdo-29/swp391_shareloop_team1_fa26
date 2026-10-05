package com.shareloop.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body của POST /api/v1/auth/register. Phone nhận 0xxxxxxxxx hoặc +84xxxxxxxxx (di động VN). */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 64) String password,
        @NotBlank @Size(max = 100) String fullName,

        @NotBlank @Pattern(regexp = "^(0|\\+84)[35789]\\d{8}$", message = "Số điện thoại không đúng định dạng.")
        String phone) {}
