package com.shareloop.report.controller;

import com.shareloop.common.security.CurrentUserId;
import com.shareloop.report.dto.ConcludeReportRequest;
import com.shareloop.report.dto.ReportConclusionResponse;
import com.shareloop.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller xử lý kết luận khiếu nại, báo cáo vi phạm dành cho Admin (FR-91, FR-93, BR-A07, Issue #113).
 */
@Tag(name = "Admin Report", description = "Quản lý và giải quyết khiếu nại, báo cáo vi phạm")
@RestController
@RequestMapping("/api/v1/admin/reports")
@Validated
public class AdminReportController {

    private final ReportService reportService;

    public AdminReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * Admin kết luận một báo cáo vi phạm (UPHOLD hoặc DISMISS).
     */
    @Operation(
            summary = "Kết luận khiếu nại, báo cáo vi phạm",
            description = "Chỉ Admin. Đưa ra kết luận có cơ sở (UPHOLD) hoặc bác bỏ (DISMISS) đối với báo cáo vi phạm. "
                    + "Nếu UPHOLD: trừ 1 sao uy tín người vi phạm; nếu lý do nghiêm trọng (hàng cấm, hàng giả): "
                    + "hệ thống tự động ẩn tất cả các bài đăng đang hoạt động của người vi phạm để rà soát. "
                    + "Nếu DISMISS: đóng báo cáo, không áp dụng chế tài. Báo cáo đã kết luận không thể thực hiện lại.")
    @ApiResponse(responseCode = "200", description = "Kết luận khiếu nại thành công")
    @ApiResponse(responseCode = "400", description = "Dữ liệu yêu cầu không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "403", description = "Không có quyền Admin")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy báo cáo vi phạm")
    @ApiResponse(responseCode = "409", description = "Báo cáo này đã được kết luận trước đó")
    @PostMapping("/{reportId}/conclude")
    public ReportConclusionResponse conclude(
            @Parameter(hidden = true) @CurrentUserId Long adminId,
            @Parameter(description = "ID báo cáo cần kết luận", example = "1") @PathVariable Long reportId,
            @Valid @RequestBody ConcludeReportRequest request) {
        return reportService.conclude(adminId, reportId, request);
    }
}
