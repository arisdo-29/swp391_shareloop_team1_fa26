package com.shareloop.user.event;

import java.util.List;

/**
 * Phát ra ngay sau khi mở khoá tài khoản.
 *
 * @param userId tài khoản vừa được mở khoá
 * @param hiddenItemIds id các bài đã bị ẩn lúc khoá (đọc từ activity log USER_LOCK), để hiện lại đúng những bài này
 */
public record UserUnlockedEvent(long userId, List<Long> hiddenItemIds) {}
