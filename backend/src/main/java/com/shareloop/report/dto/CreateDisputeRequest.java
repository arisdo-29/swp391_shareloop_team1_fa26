package com.shareloop.report.dto;

import com.shareloop.report.entity.ReportReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "Yêu cầu khiếu nại giao dịch đã hoàn thành")
public record CreateDisputeRequest(
        @NotNull(message = "Lý do khiếu nại là bắt buộc.")
        @Schema(description = "Mã lý do khiếu nại", example = "NOT_AS_DESCRIBED")
        ReportReason reason,

        @Size(max = 500, message = "Mô tả không được vượt quá 500 ký tự.")
        @Schema(
                description = "Mô tả chi tiết lý do khiếu nại",
                example = "Áo bị rách ở phần nách áo, không giống như ảnh mô tả")
        String note,

        @Schema(description = "Danh sách ID media làm bằng chứng minh hoạ")
        List<Long> evidenceMediaIds) {}
