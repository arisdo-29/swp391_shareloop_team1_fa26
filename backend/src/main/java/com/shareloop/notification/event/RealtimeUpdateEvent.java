package com.shareloop.notification.event;

/** Tín hiệu làm mới dữ liệu; không chứa nội dung riêng tư. null userId nghĩa là thay đổi danh mục công khai. */
public record RealtimeUpdateEvent(Long userId, String kind, Long resourceId) {}
