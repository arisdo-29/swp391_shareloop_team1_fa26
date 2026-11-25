package com.shareloop.item.event;

/**
 * Phát ra khi một bài rời trạng thái "APPROVED và đang hiển thị": bị giữ chỗ ở giao dịch, bị xoá, bị ẩn, bị sửa (về
 * PENDING_REVIEW) hoặc hết hạn. Module request lắng nghe để huỷ các yêu cầu PENDING đang dùng bài này làm món đem đổi
 * (YC-04, YC-07), và khi bài bị sửa thì báo người đang xin bài.
 *
 * <p>Không phát khi bài quay lại APPROVED (release, markApproved).
 *
 * @param itemId id bài vừa không còn dùng được
 * @param reason lý do bài không còn dùng được
 */
public record ItemUnavailableEvent(long itemId, Reason reason) {

    /** Lý do bài không còn dùng được. */
    public enum Reason {
        RESERVED,
        REMOVED,
        // Ẩn do báo cáo (tự ẩn khi đủ ngưỡng hoặc kết luận vi phạm).
        HIDDEN,
        HIDDEN_BY_ADMIN,
        // Ẩn vì chủ bài bị khoá tài khoản hoặc hết sao.
        HIDDEN_BY_ACCOUNT,
        // Bản sửa của bài đã duyệt bị Admin từ chối: bài bị gỡ cho tới khi chủ bài sửa lại.
        EDIT_REJECTED,
        EDITED,
        EXPIRED
    }
}
