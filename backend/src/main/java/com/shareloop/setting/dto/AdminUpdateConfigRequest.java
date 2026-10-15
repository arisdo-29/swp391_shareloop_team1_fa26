package com.shareloop.setting.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminUpdateConfigRequest(
        @NotBlank(message = "Key cấu hình là bắt buộc.") String key,
        @NotBlank(message = "Giá trị cấu hình là bắt buộc.") String value,
        String reason) {}
