package com.shareloop.setting.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Yêu cầu cập nhật cấu hình hệ thống bằng key trong body")
public record AdminUpdateConfigRequest(
        @NotBlank(message = "Key cấu hình là bắt buộc.")
        @Schema(description = "Key định danh của cấu hình", example = "chat_ai_threshold")
        String key,

        @NotBlank(message = "Giá trị cấu hình là bắt buộc.")
        @Schema(description = "Giá trị mới của cấu hình", example = "0.8")
        String value,

        @Schema(description = "Lý do cập nhật để ghi log", example = "Tăng ngưỡng AI kiểm tra tin nhắn")
        String reason) {}
