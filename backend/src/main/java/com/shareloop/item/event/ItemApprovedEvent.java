package com.shareloop.item.event;

/**
 * Phát ra khi Admin duyệt một bài đăng (PENDING_REVIEW sang APPROVED) qua ItemService.markApproved. Module report lắng
 * nghe để hiện lại bài từng bị ẩn vì báo cáo vi phạm được chấp nhận, sau khi chủ bài đã sửa và được duyệt lại.
 *
 * @param itemId id của bài đăng vừa được duyệt
 */
public record ItemApprovedEvent(long itemId) {}
