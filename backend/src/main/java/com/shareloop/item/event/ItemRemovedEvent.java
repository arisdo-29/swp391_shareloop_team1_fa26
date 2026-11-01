package com.shareloop.item.event;

/**
 * Phát ra khi một bài đăng bị gỡ (xoá mềm) qua ItemService.remove.
 * Module request lắng nghe để huỷ các yêu cầu PENDING của bài đăng (D-13, BD-11).
 *
 * @param itemId id của bài đăng vừa bị gỡ
 * @param donorId id của chủ bài đăng
 */
public record ItemRemovedEvent(long itemId, long donorId) {
    public ItemRemovedEvent(long itemId) {
        this(itemId, 0L);
    }
}
