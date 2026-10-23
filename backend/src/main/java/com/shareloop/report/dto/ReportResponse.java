package com.shareloop.report.dto;

import com.shareloop.report.entity.ReportReason;
import com.shareloop.report.entity.ReportStage;
import com.shareloop.report.entity.ReportStatus;
import com.shareloop.report.entity.TargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Thông tin báo cáo")
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
        Instant createdAt,

        @Schema(
                description = "Họ tên người gửi báo cáo. Chỉ có trong API của Admin, API của người dùng để null",
                example = "Nguyễn Văn A")
        String reporterName,

        @Schema(
                description =
                        "Tên đối tượng bị báo cáo: tiêu đề bài đăng, họ tên người dùng hoặc tiêu đề bài của giao dịch. Chỉ có trong API của Admin",
                example = "Áo khoác gió nam")
        String targetName) {

    // Dạng 11 tham số (chưa có tên) cho code đang gọi; reporterName và targetName để null.
    public ReportResponse(
            Long id,
            Long reporterId,
            TargetType targetType,
            Long itemId,
            Long targetUserId,
            Long requestId,
            ReportReason reason,
            String description,
            ReportStage stage,
            ReportStatus status,
            Instant createdAt) {
        this(
                id,
                reporterId,
                targetType,
                itemId,
                targetUserId,
                requestId,
                reason,
                description,
                stage,
                status,
                createdAt,
                null,
                null);
    }
}
