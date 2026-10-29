package com.shareloop.listingfee.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Báo giá một lần gia hạn bài (GET /items/{id}/renew-quote). Chỉ đọc: không trừ tiền, không đổi dữ liệu. Phí và hạn
 * mới tính bằng đúng các hàm mà {@code RenewService.renew} dùng.
 *
 * @param fee số Credit sẽ bị trừ nếu gia hạn ngay; 0 khi miễn phí
 * @param newExpireAt hạn hiển thị mới nếu gia hạn ngay
 * @param allowed có được gia hạn ngay bây giờ không
 * @param reason lý do không được gia hạn (tiếng Việt, hiển thị thẳng cho người dùng); null khi allowed = true
 */
@Schema(description = "Báo giá một lần gia hạn bài: phí, hạn mới và có được gia hạn không")
public record RenewQuote(
        @Schema(description = "Số Credit sẽ bị trừ khi gia hạn; 0 khi miễn phí", example = "5")
        int fee,

        @Schema(description = "Hạn hiển thị mới nếu gia hạn ngay, ISO-8601 UTC", example = "2026-11-28T08:00:00Z")
        Instant newExpireAt,

        @Schema(description = "Có được gia hạn ngay bây giờ không", example = "true")
        boolean allowed,

        @Schema(description = "Lý do không được gia hạn; null khi được phép", example = "null", nullable = true)
        String reason) {}
