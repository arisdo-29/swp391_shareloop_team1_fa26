package com.shareloop.setting.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Yêu cầu xem trước một thay đổi cấu hình, không ghi gì vào cơ sở dữ liệu")
public record PreviewConfigRequest(
        @NotBlank(message = "Key cấu hình là bắt buộc.")
        @Schema(description = "Key định danh của cấu hình", example = "boost_days")
        String key,

        @NotBlank(message = "Giá trị cấu hình là bắt buộc.")
        @Schema(description = "Giá trị mới dự định đặt", example = "4")
        String value) {}
