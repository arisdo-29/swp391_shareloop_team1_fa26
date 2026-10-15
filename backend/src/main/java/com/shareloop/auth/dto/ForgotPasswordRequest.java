package com.shareloop.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Body của POST /api/v1/auth/forgot-password. */
public record ForgotPasswordRequest(@NotBlank @Email String email) {}
