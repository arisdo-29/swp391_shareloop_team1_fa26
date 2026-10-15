package com.shareloop.auth.event;

import com.shareloop.user.entity.OtpPurpose;

/**
 * Sự kiện "vừa cấp OTP mới". Mang mã thô để gửi email; mã này không được lưu ở đâu ngoài bản băm trong DB. purpose
 * quyết định tiêu đề và nội dung email.
 */
public record OtpIssuedEvent(String email, String code, int ttlMinutes, OtpPurpose purpose) {}
