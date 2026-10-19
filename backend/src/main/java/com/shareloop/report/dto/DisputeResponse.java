package com.shareloop.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Thông tin khiếu nại giao dịch được ghi nhận")
public record DisputeResponse(
        @Schema(description = "ID bản ghi khiếu nại", example = "1")
        Long id,

        @Schema(description = "Hạn chót giải trình của bên bị khiếu nại", example = "2026-10-21T13:30:00Z")
        Instant responseDueAt) {}
