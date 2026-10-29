package com.shareloop.wallet.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Số dòng lịch sử ví theo từng tab, tính trên TOÀN BỘ khoảng thời gian đang lọc (không phụ thuộc trang đang xem).
 *
 * @param all mọi dòng trong khoảng
 * @param topUp dòng nạp Credit (type TOP_UP)
 * @param spend dòng chi tiêu: các khoản trừ Credit (amount âm) trừ Admin điều chỉnh; khớp tab "Chi tiêu" của FE
 */
@Schema(description = "Số dòng lịch sử ví theo tab, tính trên cả khoảng thời gian đang lọc")
public record WalletHistoryCounts(
        @Schema(description = "Mọi dòng trong khoảng", example = "42")
        long all,

        @Schema(description = "Dòng nạp Credit", example = "6")
        long topUp,

        @Schema(description = "Dòng chi tiêu (khoản trừ, không tính Admin điều chỉnh)", example = "35")
        long spend) {}
