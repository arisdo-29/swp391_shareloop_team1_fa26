package com.shareloop.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Một loại hành động dùng cho bộ lọc nhật ký")
public record ActivityTypeDto(
        @Schema(description = "Mã loại hành động", example = "REVIEW_APPROVE")
        String type,

        @Schema(description = "Nhãn tiếng Việt", example = "Duyệt bài")
        String label) {}
