package com.shareloop.report.controller;

import com.shareloop.common.security.CurrentUserId;
import com.shareloop.report.dto.AdminReportDetail;
import com.shareloop.report.dto.AdminReportPage;
import com.shareloop.report.dto.ConcludeReportRequest;
import com.shareloop.report.dto.ReportConclusionResponse;
import com.shareloop.report.entity.ReportStatus;
import com.shareloop.report.entity.TargetType;
import com.shareloop.report.service.AdminReportQueryService;
import com.shareloop.report.service.ReportEvidenceService;
import com.shareloop.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final AdminReportQueryService queryService;
    private final ReportEvidenceService evidenceService;

    public AdminReportController(
            ReportService reportService, AdminReportQueryService queryService, ReportEvidenceService evidenceService) {
        this.reportService = reportService;
        this.queryService = queryService;
        this.evidenceService = evidenceService;
    }

    @Operation(
            summary = "Xem danh sách báo cáo và khiếu nại",
            description =
                    "Chỉ Admin. Hỗ trợ phân trang và lọc đồng thời theo trạng thái, loại đối tượng; hồ sơ mới nhất hiển thị trước.")
    @GetMapping
    public AdminReportPage list(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) TargetType targetType,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return queryService.list(status, targetType, page, size);
    }

    @Operation(
            summary = "Xem chi tiết hồ sơ báo cáo hoặc khiếu nại",
            description =
                    "Chỉ Admin. Trả về thông tin các bên, nội dung báo cáo, giải trình, bằng chứng, thời hạn phản hồi và kết luận nếu có.")
    @GetMapping("/{reportId}")
    public AdminReportDetail detail(@PathVariable @Min(1) long reportId) {
        return queryService.detail(reportId);
    }

    @Operation(
            summary = "Xem ảnh bằng chứng của hồ sơ",
            description =
                    "Chỉ Admin. Ảnh phải thuộc danh sách bằng chứng hoặc bằng chứng giải trình của đúng hồ sơ. Trả về nội dung ảnh riêng tư và không cho lưu bộ nhớ đệm.")
    @GetMapping("/{reportId}/evidence/{mediaId}")
    public ResponseEntity<byte[]> evidence(@PathVariable @Min(1) long reportId, @PathVariable @Min(1) long mediaId) {
        var content = evidenceService.read(reportId, mediaId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(content.mimeType()))
                .body(content.bytes());
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
