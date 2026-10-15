package com.shareloop.listingfee.dto;

/**
 * Báo giá lượt sửa bài (F07 bước 2, C14, C14b). {@code PostFeeService.holdEditFee} giữ đúng {@code amount} của báo
 * giá này, nên số FE thấy và số bị giữ luôn khớp nhau.
 *
 * @param free lượt sửa này miễn phí hay có phí ({@code amount == 0})
 * @param amount số Credit sẽ giữ; 0 khi miễn phí; khi có phí lấy từ ConfigService (post_fee hoặc edit_fee)
 * @param reason lý do của kết quả trên
 */
public record EditQuote(boolean free, int amount, Reason reason) {

    public enum Reason {
        /** Bài chưa từng được duyệt và phí đăng đang được giữ (bài chờ duyệt lần đầu): sửa miễn phí. */
        NOT_YET_APPROVED,
        /** Bài chưa từng được duyệt và phí đăng đã được nhả (bị từ chối): gửi lại phải giữ lại phí đăng (post_fee). */
        REPOST_FEE,
        /** Bài đã duyệt và đang có một bản sửa chờ duyệt: sửa đè, không thêm phí, không thêm lượt (UC-14 2a). */
        PENDING_REVIEW_OVERWRITE,
        /** Đã duyệt nhưng chưa dùng lượt miễn phí (free_edit_used = false): miễn phí một lần (C14). */
        FIRST_FREE_EDIT,
        /** Đã dùng lượt miễn phí sau duyệt: tính phí sửa (C14b). */
        PAID_EDIT
    }
}
