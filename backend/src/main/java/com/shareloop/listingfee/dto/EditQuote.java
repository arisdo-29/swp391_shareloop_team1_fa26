package com.shareloop.listingfee.dto;

/**
 * Báo giá lượt sửa bài (F07 bước 2, C14, C14b).
 *
 * @param free lượt sửa này miễn phí hay có phí
 * @param amount số Credit sẽ giữ; 0 khi miễn phí; khi có phí lấy từ ConfigService (edit_fee)
 * @param reason lý do của kết quả trên
 */
public record EditQuote(boolean free, int amount, Reason reason) {

    public enum Reason {
        /** Bài chưa từng được duyệt (approved_at null): miễn phí. */
        NOT_YET_APPROVED,
        /** Đã duyệt nhưng chưa dùng lượt miễn phí (free_edit_used = false): miễn phí một lần (C14). */
        FIRST_FREE_EDIT,
        /** Đã dùng lượt miễn phí sau duyệt: tính phí sửa (C14b). */
        PAID_EDIT
    }
}
