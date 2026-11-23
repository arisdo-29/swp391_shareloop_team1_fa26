package com.shareloop.report.dto;

import com.shareloop.request.dto.ItemDisputeOutcome;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "Yêu cầu kết luận khiếu nại của Admin")
public record ConcludeReportRequest(
        @NotNull(message = "Kết luận là bắt buộc (UPHOLD hoặc DISMISS).")
        @Schema(
                description = "Kết luận: UPHOLD (có cơ sở, trừ 1 sao) hoặc DISMISS (không có cơ sở)",
                example = "UPHOLD")
        ReportDecision decision,

        @Size(max = 500, message = "Ghi chú không được vượt quá 500 ký tự.")
        @Schema(description = "Ghi chú hoặc lý do kết luận của Admin", example = "Xác minh hàng cấm theo quy định")
        String note,

        @Schema(
                description =
                        "Chỉ dùng khi bác bỏ: báo cáo sai có chủ ý thì trừ 1 sao người báo. Mặc định false. Báo cáo Từ chối giao nhận bỏ qua trường này vì đã tự trừ sao",
                example = "false")
        Boolean deliberateFalseReport,

        @Valid
        @Schema(
                description =
                        "Khi chấp thuận khiếu nại sự cố giao nhận đang DISPUTED thì bắt buộc có đủ mọi món của giao dịch, mỗi món một lựa chọn REOPEN, HIDE hoặc TRANSFERRED")
        List<ItemDisputeOutcome> itemOutcomes) {

    // Dạng 3 tham số (chưa có itemOutcomes) cho code đang gọi.
    public ConcludeReportRequest(ReportDecision decision, String note, Boolean deliberateFalseReport) {
        this(decision, note, deliberateFalseReport, null);
    }

    // Dạng 2 tham số (chưa có deliberateFalseReport) cho code đang gọi; mặc định không phạt.
    public ConcludeReportRequest(ReportDecision decision, String note) {
        this(decision, note, false, null);
    }
}
