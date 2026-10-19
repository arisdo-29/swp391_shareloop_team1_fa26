package com.shareloop.report.dto;

import com.shareloop.report.entity.ReportReason;
import com.shareloop.report.entity.ReportStatus;
import com.shareloop.report.entity.TargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Kết quả kết luận khiếu nại của Admin")
public record ReportConclusionResponse(
        @Schema(description = "ID bản ghi báo cáo", example = "1")
        Long id,

        @Schema(description = "Loại đối tượng báo cáo (ITEM, USER, REQUEST)", example = "ITEM")
        TargetType targetType,

        @Schema(description = "ID bài đăng (nếu đối tượng là ITEM)", example = "42")
        Long itemId,

        @Schema(description = "ID người dùng bị báo cáo (nếu đối tượng là USER)", example = "9")
        Long targetUserId,

        @Schema(description = "ID yêu cầu giao dịch (nếu đối tượng là REQUEST)", example = "100")
        Long requestId,

        @Schema(description = "Lý do báo cáo", example = "PROHIBITED_ITEM")
        ReportReason reason,

        @Schema(description = "Trạng thái sau kết luận (RESOLVED, DISMISSED)", example = "RESOLVED")
        ReportStatus status,

        @Schema(description = "ID Admin kết luận báo cáo", example = "2")
        Long resolvedBy,

        @Schema(description = "Ghi chú kết luận của Admin", example = "Xác minh hàng cấm theo quy định")
        String resolution,

        @Schema(description = "Số sao uy tín đã trừ", example = "1")
        short starsDeducted,

        @Schema(description = "Thời điểm kết luận báo cáo", example = "2026-10-18T13:00:00Z")
        Instant resolvedAt) {}
