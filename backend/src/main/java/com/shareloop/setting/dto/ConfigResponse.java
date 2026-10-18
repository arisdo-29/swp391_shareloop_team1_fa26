package com.shareloop.setting.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Thông tin cấu hình hệ thống")
public record ConfigResponse(
        @Schema(description = "Key định danh cấu hình", example = "post_fee")
        String key,

        @Schema(description = "Giá trị hiện hành của cấu hình", example = "5")
        String value,

        @Schema(description = "Giá trị mặc định ban đầu", example = "5")
        String defaultValue) {}
