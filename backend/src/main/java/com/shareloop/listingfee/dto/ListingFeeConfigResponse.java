package com.shareloop.listingfee.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Bảng phí và thời hạn hiện hành để FE hiển thị trước khi người dùng đăng, sửa, gia hạn hay đẩy bài. */
@Schema(description = "Mức phí và thời hạn bài đăng hiện hành (Admin đổi được trong cấu hình hệ thống)")
public record ListingFeeConfigResponse(
        @Schema(description = "Phí đăng bài SWAP (Credit), giữ khi gửi bài và trừ khi được duyệt", example = "5")
        int postFeeSwap,

        @Schema(description = "Phí đăng bài GIVE (Credit): luôn 0 vì bài cho tặng được đăng miễn phí", example = "0")
        int postFeeGive,

        @Schema(description = "Phí mỗi lượt sửa sau khi đã dùng lượt sửa miễn phí (Credit)", example = "5")
        int editFee,

        @Schema(description = "Phí gia hạn bài (Credit)", example = "5")
        int renewFee,

        @Schema(description = "Số ngày sau khi hết hạn vẫn còn được gia hạn bài", example = "7")
        int renewExpiredWindowDays,

        @Schema(description = "Phí mỗi lần đẩy bài (Credit)", example = "5")
        int boostFee,

        @Schema(description = "Số ngày được đẩy thêm sau mỗi lần đẩy bài", example = "3")
        int boostDays,

        @Schema(description = "Hạn đẩy bài không được vượt quá bây giờ cộng số ngày này", example = "14")
        int boostMaxDays,

        @Schema(description = "Số ngày hiển thị của bài sau lần duyệt đầu", example = "30")
        int postDisplayDays,

        @Schema(description = "Số ngày hiển thị khi chủ bài từ hạng Vàng trở lên", example = "45")
        int postDisplayDaysGold) {}
