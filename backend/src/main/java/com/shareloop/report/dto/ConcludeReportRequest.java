package com.shareloop.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Yêu cầu kết luận khiếu nại của Admin")
public record ConcludeReportRequest(
        @NotNull(message = "Kết luận là bắt buộc (UPHOLD hoặc DISMISS).")
        @Schema(
                description = "Kết luận: UPHOLD (có cơ sở, trừ 1 sao) hoặc DISMISS (không có cơ sở)",
                example = "UPHOLD")
        ReportDecision decision,

        @Size(max = 500, message = "Ghi chú không được vượt quá 500 ký tự.")
        @Schema(description = "Ghi chú hoặc lý do kết luận của Admin", example = "Xác minh hàng cấm theo quy định")
        String note) {}
