package com.shareloop.user.event;

/**
 * Phát ra ngay sau khi tài khoản bị khoá. Module request huỷ giao dịch đang mở, module review nhả phí đang giữ; user
 * không gọi thẳng hai module đó để tránh phụ thuộc chéo.
 *
 * @param adminId quản trị viên thực hiện khoá
 * @param userId tài khoản bị khoá
 * @param cleanup nơi các listener cộng dồn số giao dịch đã huỷ và số Credit đã nhả
 */
public record UserLockedEvent(Long adminId, long userId, UserLockCleanup cleanup) {}
