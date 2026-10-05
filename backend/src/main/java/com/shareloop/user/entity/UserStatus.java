package com.shareloop.user.entity;

/** Trạng thái tài khoản (cột users.status). Khoá tài khoản dùng is_active = false, không đổi status. */
public enum UserStatus {
    PENDING_VERIFICATION,
    ACTIVE,
    BANNED
}
