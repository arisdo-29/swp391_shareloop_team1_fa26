package com.shareloop.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Thông tin bài đăng trong hàng chờ duyệt")
public record ReviewQueueItem(
        @Schema(description = "ID bài đăng trong hàng chờ", example = "42")
        Long id,

        @Schema(description = "ID người đăng bài", example = "9")
        Long donorId,

        @Schema(description = "Tiêu đề bài đăng", example = "Áo khoác gió nam")
        String title,

        @Schema(description = "Mức độ rủi ro do AI đánh giá (LOW, MEDIUM, HIGH)", example = "LOW")
        String aiRisk,

        @Schema(description = "Cờ kiểm duyệt (REVIEW nếu nghi vấn vi phạm)", example = "REVIEW")
        String moderationFlag,

        @Schema(description = "Lý do bị gắn cờ kiểm duyệt", example = "Có thể có thông tin liên hệ")
        String flagReason,

        @Schema(description = "Số sao uy tín của người đăng (1-5)", example = "3")
        short donorTrustStars,

        @Schema(description = "Thời điểm bài đăng được tạo", example = "2026-10-14T10:00:00Z")
        Instant createdAt) {}
