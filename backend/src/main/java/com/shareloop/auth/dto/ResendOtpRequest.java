package com.shareloop.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Body của POST /api/v1/auth/resend-otp. */
public record ResendOtpRequest(@NotBlank @Email String email) {}
