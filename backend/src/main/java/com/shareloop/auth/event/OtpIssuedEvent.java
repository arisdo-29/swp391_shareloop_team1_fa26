package com.shareloop.auth.event;

/** Sự kiện "vừa cấp OTP mới". Mang mã thô để gửi email; mã này không được lưu ở đâu ngoài bản băm trong DB. */
public record OtpIssuedEvent(String email, String code, int ttlMinutes) {}
