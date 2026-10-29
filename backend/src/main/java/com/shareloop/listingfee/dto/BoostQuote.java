package com.shareloop.listingfee.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Báo giá một lần đẩy bài (GET /items/{id}/boost-quote). Chỉ đọc: không trừ tiền, không đổi dữ liệu. Số liệu tính
 * bằng đúng các hàm mà {@code BoostService.boost} dùng, nên số FE thấy và số bị trừ khi đẩy luôn khớp nhau.
 *
 * @param fee số Credit sẽ bị trừ nếu đẩy ngay (boost_fee)
 * @param newBoostedUntil thời điểm hết đẩy mới nếu đẩy ngay
 * @param allowed có được đẩy ngay bây giờ không
 * @param reason lý do không được đẩy (tiếng Việt, hiển thị thẳng cho người dùng); null khi allowed = true
 */
@Schema(description = "Báo giá một lần đẩy bài: phí, mốc hết đẩy mới và có được đẩy không")
public record BoostQuote(
        @Schema(description = "Số Credit sẽ bị trừ khi đẩy", example = "5")
        int fee,

        @Schema(description = "Thời điểm hết đẩy mới nếu đẩy ngay, ISO-8601 UTC", example = "2026-11-02T08:00:00Z")
        Instant newBoostedUntil,

        @Schema(description = "Có được đẩy ngay bây giờ không", example = "true")
        boolean allowed,

        @Schema(description = "Lý do không được đẩy; null khi được phép", example = "null", nullable = true)
        String reason) {}
