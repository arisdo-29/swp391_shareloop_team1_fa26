package com.shareloop.setting.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Thông tin cấu hình hệ thống")
public record ConfigResponse(
        @Schema(description = "Key định danh cấu hình", example = "post_fee")
        String key,

        @Schema(description = "Giá trị hiện hành của cấu hình", example = "5")
        String value,

        @Schema(description = "Giá trị mặc định ban đầu", example = "5")
        String defaultValue,

        @Schema(description = "Tên tiếng Việt để hiển thị", example = "Phí đăng bài")
        String label,

        @Schema(
                description = "Mô tả tiếng Việt: giá trị này dùng để làm gì",
                example = "Số Credit được giữ khi đăng bài và chỉ bị trừ khi Admin duyệt.")
        String description,

        @Schema(
                description = "Mã nhóm: FEES, POSTING, AI, MODERATION, ACCOUNT, TRANSACTION hoặc REPUTATION",
                example = "FEES")
        String group,

        @Schema(description = "Tên nhóm tiếng Việt để hiển thị", example = "Phí và nạp tiền")
        String groupLabel) {}
