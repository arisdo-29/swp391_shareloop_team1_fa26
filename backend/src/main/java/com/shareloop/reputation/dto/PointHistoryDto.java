package com.shareloop.reputation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Một lần thay đổi điểm uy tín")
public record PointHistoryDto(
        @Schema(description = "Điểm thay đổi", example = "10")
        int delta,

        @Schema(description = "Lý do thay đổi", example = "REQUEST_COMPLETED")
        String reason,

        @Schema(description = "Thời điểm ghi nhận", example = "2026-10-18T10:00:00Z")
        Instant createdAt) {}
