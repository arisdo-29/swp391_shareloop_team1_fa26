package com.shareloop.setting.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Yêu cầu cập nhật giá trị cấu hình")
public record UpdateConfigRequest(
        @NotBlank @Schema(description = "Giá trị mới của cấu hình", example = "8")
        String value,

        @NotBlank @Schema(description = "Lý do cập nhật để ghi log", example = "Điều chỉnh mức phí theo chính sách mới")
        String reason) {}
