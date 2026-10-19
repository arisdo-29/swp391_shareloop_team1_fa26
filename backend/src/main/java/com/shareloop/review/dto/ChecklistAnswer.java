package com.shareloop.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Đáp án cho một mục checklist duyệt bài")
public record ChecklistAnswer(
        @NotNull(message = "Thiếu id mục checklist.")
        @Schema(description = "ID mục checklist lấy từ GET /admin/reviews/pending", example = "11")
        Long id,

        @NotNull(message = "Thiếu đáp án cho mục checklist.")
        @Schema(description = "true nếu bài đạt điều kiện của mục này", example = "true")
        Boolean passed) {}
