package com.shareloop.auth.event;

import java.time.Instant;

/** Sự kiện "vừa đổi mật khẩu thành công" (TK-10). Chỉ mang email và thời điểm đổi, không có mật khẩu hay mã băm. */
public record PasswordChangedEvent(String email, Instant changedAt) {}
