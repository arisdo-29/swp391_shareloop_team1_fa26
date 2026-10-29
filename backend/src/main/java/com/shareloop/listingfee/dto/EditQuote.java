package com.shareloop.listingfee.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Báo giá lượt sửa bài (F07 bước 2, C14, C14b). {@code PostFeeService.holdEditFee} giữ đúng {@code amount} của báo
 * giá này, nên số FE thấy và số bị giữ luôn khớp nhau.
 *
 * @param free lượt sửa này miễn phí hay có phí ({@code amount == 0})
 * @param amount số Credit sẽ giữ; 0 khi miễn phí; khi có phí lấy từ ConfigService (post_fee hoặc edit_fee)
 * @param reason lý do của kết quả trên
 */
@Schema(description = "Báo giá lượt sửa bài: miễn phí hay phải giữ bao nhiêu Credit, và vì sao")
public record EditQuote(
        @Schema(description = "Lượt sửa này miễn phí (true khi amount = 0)", example = "false")
        boolean free,

        @Schema(
                description = "Số Credit sẽ giữ khi gửi bản sửa; 0 khi miễn phí, có phí thì là post_fee hoặc edit_fee",
                example = "5")
        int amount,

        @Schema(description = "Lý do của báo giá, xem mô tả enum Reason")
        Reason reason) {

    // Swagger không đọc Javadoc nên mô tả từng giá trị được ghi lại trong @Schema để FE thấy được.
    @Schema(
            description = "Lý do báo giá sửa bài:\n"
                    + "* NOT_YET_APPROVED - Bài chưa từng được duyệt và phí đăng đang được giữ"
                    + " (bài chờ duyệt lần đầu): sửa miễn phí.\n"
                    + "* REPOST_FEE - Bài chưa từng được duyệt và phí đăng đã được nhả (bị từ chối):"
                    + " gửi lại phải giữ lại phí đăng (post_fee).\n"
                    + "* PENDING_REVIEW_OVERWRITE - Bài đã duyệt và đang có một bản sửa chờ duyệt:"
                    + " sửa đè, không thêm phí, không thêm lượt.\n"
                    + "* FIRST_FREE_EDIT - Bài đã duyệt nhưng chưa dùng lượt sửa miễn phí: miễn phí một lần.\n"
                    + "* PAID_EDIT - Đã dùng lượt sửa miễn phí sau duyệt: tính phí sửa (edit_fee).",
            example = "PAID_EDIT")
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
        PAID_EDIT,
        /** Đổi hình thức bài đã trả phí đăng sang hình thức khác, không thu lại phí đăng. */
        OFFER_TYPE_CHANGE_FREE,
        /** Đổi bài chưa trả phí đăng sang Trao đổi, giữ phí đăng theo ngưỡng hiện tại. */
        OFFER_TYPE_CHANGE_POST_FEE
    }
}
