package com.shareloop.auth.dto;

/** Trả về từ /register và /resend-otp: FE dùng hai số giây để đếm ngược hạn mã và nút "Gửi lại". */
public record OtpSentResponse(String email, int otpExpiresInSeconds, int resendCooldownSeconds) {}
