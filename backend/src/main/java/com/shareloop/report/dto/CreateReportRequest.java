package com.shareloop.report.dto;

import com.shareloop.report.entity.ReportReason;
import com.shareloop.report.entity.TargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "Yêu cầu báo cáo vi phạm")
public record CreateReportRequest(
        @NotNull(message = "Loại đối tượng báo cáo là bắt buộc.")
        @Schema(
                description = "Loại đối tượng: ITEM (bài đăng), USER (người dùng), REQUEST (yêu cầu/giao dịch)",
                example = "ITEM")
        TargetType targetType,

        @Schema(description = "ID đối tượng báo cáo (dùng chung cho ITEM hoặc USER)", example = "42")
        Long targetId,

        @Schema(description = "ID bài đăng (khi targetType là ITEM)", example = "42")
        Long itemId,

        @Schema(description = "ID người dùng (khi targetType là USER)", example = "9")
        Long targetUserId,

        @Schema(description = "ID giao dịch (khi targetType là REQUEST)", example = "100")
        Long requestId,

        @NotNull(message = "Lý do báo cáo là bắt buộc.")
        @Schema(description = "Lý do báo cáo", example = "PROHIBITED_ITEM")
        ReportReason reason,

        @NotBlank(message = "Mô tả chi tiết lý do báo cáo là bắt buộc.")
        @Size(max = 2000, message = "Mô tả không được vượt quá 2000 ký tự.")
        @Schema(description = "Mô tả chi tiết vi phạm", example = "Bài đăng chứa hàng cấm không được phép chia sẻ")
        String description,

        List<@NotNull @Positive Long> evidenceMediaIds) {

    public CreateReportRequest(
            TargetType targetType,
            Long targetId,
            Long itemId,
            Long targetUserId,
            Long requestId,
            ReportReason reason,
            String description) {
        this(targetType, targetId, itemId, targetUserId, requestId, reason, description, List.of());
    }

    public Long resolvedItemId() {
        if (itemId != null) {
            return itemId;
        }
        return targetType == TargetType.ITEM ? targetId : null;
    }

    public Long resolvedTargetUserId() {
        if (targetUserId != null) {
            return targetUserId;
        }
        return targetType == TargetType.USER ? targetId : null;
    }

    public Long resolvedRequestId() {
        if (requestId != null) {
            return requestId;
        }
        return targetType == TargetType.REQUEST ? targetId : null;
    }
}
