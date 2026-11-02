package com.shareloop.item.dto;

/** Tab danh sách bài ở hồ sơ công khai (GET /api/v1/users/{id}/items). */
public enum UserItemTab {
    /** Bài đang hiển thị công khai: APPROVED, không ẩn, còn hạn. */
    ACTIVE,
    /** Bài đã giao dịch thành công: TRADED, không ẩn. */
    DONE
}
