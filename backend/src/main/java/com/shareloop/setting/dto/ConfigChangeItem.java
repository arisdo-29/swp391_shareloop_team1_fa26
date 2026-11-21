package com.shareloop.setting.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Một thay đổi trong lô cập nhật cấu hình")
public record ConfigChangeItem(
        @NotBlank(message = "Key cấu hình là bắt buộc.")
        @Schema(description = "Key định danh của cấu hình", example = "boost_days")
        String key,

        @NotBlank(message = "Giá trị cấu hình là bắt buộc.") @Schema(description = "Giá trị mới", example = "4")
        String value) {}
