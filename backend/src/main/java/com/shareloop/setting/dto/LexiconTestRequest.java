package com.shareloop.setting.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Yêu cầu thử nghiệm quét từ khóa trên văn bản")
public record LexiconTestRequest(
        @NotBlank(message = "Text không được để trống.")
        @Schema(description = "Đoạn văn bản cần thử nghiệm quét từ khóa", example = "Bán súng nước đồ chơi giá rẻ")
        String text) {}
