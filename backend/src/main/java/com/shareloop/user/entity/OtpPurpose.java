package com.shareloop.user.entity;

/** Mục đích của mã OTP đang lưu trong users.otp_purpose; tên enum khớp CHECK của cột. */
public enum OtpPurpose {
    REGISTER,
    RESET_PASSWORD,
    CHANGE_PHONE
}
