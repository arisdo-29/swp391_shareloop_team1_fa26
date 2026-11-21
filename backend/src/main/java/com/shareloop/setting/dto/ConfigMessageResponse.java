package com.shareloop.setting.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Một lỗi hoặc cảnh báo của bộ luật cấu hình")
public record ConfigMessageResponse(
        @Schema(description = "Nội dung tiếng Việt để hiển thị cho Admin")
        String message,

        @Schema(description = "Các key liên quan để tô đúng ô nhập", example = "[\"boost_days\", \"boost_max_days\"]")
        List<String> keys,

        @Schema(description = "Mã lỗi, null với cảnh báo", example = "SETTING_CONFIG_RULE_VIOLATION")
        String errorCode) {}
