package com.shareloop.user.dto;

/**
 * Trạng thái hiển thị của tài khoản trong danh sách Admin. Cột users.status chỉ có 3 giá trị còn khoá tài khoản là
 * is_active = false (ERD 7.11), nên LOCKED được suy ra. Khi một tài khoản thuộc nhiều nhóm thì ưu tiên theo thứ tự:
 * BANNED, rồi LOCKED, rồi PENDING_VERIFICATION, cuối cùng là ACTIVE.
 */
public enum AdminAccountState {
    ACTIVE,
    LOCKED,
    PENDING_VERIFICATION,
    BANNED
}
