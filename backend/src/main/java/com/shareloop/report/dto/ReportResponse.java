package com.shareloop.report.dto;

import com.shareloop.report.entity.ReportReason;
import com.shareloop.report.entity.ReportStage;
import com.shareloop.report.entity.ReportStatus;
import com.shareloop.report.entity.TargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Thông tin kết quả gửi báo cáo")
public record ReportResponse(
        @Schema(description = "ID bản ghi báo cáo", example = "1")
        Long id,

        @Schema(description = "ID người gửi báo cáo", example = "10")
        Long reporterId,

        @Schema(description = "Loại đối tượng báo cáo (ITEM, USER, REQUEST)", example = "ITEM")
        TargetType targetType,

        @Schema(description = "ID bài đăng", example = "42") Long itemId,

        @Schema(description = "ID người dùng bị báo cáo", example = "9")
        Long targetUserId,

        @Schema(description = "ID yêu cầu", example = "100") Long requestId,

        @Schema(description = "Lý do báo cáo", example = "PROHIBITED_ITEM")
        ReportReason reason,

        @Schema(description = "Mô tả chi tiết vi phạm", example = "Bài đăng chứa hàng cấm không được phép chia sẻ")
        String description,

        @Schema(description = "Giai đoạn báo cáo (BEFORE, AFTER)", example = "BEFORE")
        ReportStage stage,

        @Schema(
                description = "Trạng thái xử lý báo cáo (OPEN, AWAITING_RESPONSE, RESOLVED, DISMISSED)",
                example = "OPEN")
        ReportStatus status,

        @Schema(description = "Thời điểm gửi báo cáo", example = "2026-10-17T12:00:00Z")
        Instant createdAt) {}
