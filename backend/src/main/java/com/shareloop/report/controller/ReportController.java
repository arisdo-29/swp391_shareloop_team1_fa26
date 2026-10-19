package com.shareloop.report.controller;

import com.shareloop.common.security.CurrentUserId;
import com.shareloop.report.dto.CreateReportRequest;
import com.shareloop.report.dto.ReportResponse;
import com.shareloop.report.dto.RespondReportRequest;
import com.shareloop.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller tiếp nhận báo cáo vi phạm (FR-88, BR-A03).
 */
@Tag(name = "Report", description = "Quản lý báo cáo vi phạm đối với bài đăng và người dùng")
@RestController
@RequestMapping("/api/v1/reports")
@Validated
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * Báo cáo một bài đăng hoặc người dùng vi phạm.
     */
    @Operation(
            summary = "Tạo báo cáo vi phạm",
            description = "Gửi báo cáo vi phạm cho bài đăng (ITEM) hoặc người dùng (USER). "
                    + "Yêu cầu đăng nhập. Không được tự báo cáo chính mình hoặc bài của chính mình. "
                    + "Mỗi người chỉ được báo cáo một đối tượng một lần. "
                    + "Khi một bài đăng đạt từ 3 báo cáo (BR-A03), hệ thống sẽ tự động ẩn bài.")
    @ApiResponse(responseCode = "201", description = "Gửi báo cáo thành công")
    @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ (thiếu id đối tượng, lý do vi phạm...)")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy bài đăng hoặc người dùng mục tiêu")
    @ApiResponse(
            responseCode = "409",
            description = "Tự báo cáo chính mình hoặc đã từng báo cáo đối tượng này trước đó")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse createReport(
            @Parameter(hidden = true) @CurrentUserId Long currentUserId,
            @Valid @RequestBody CreateReportRequest request) {
        return reportService.createReport(currentUserId, request);
    }

    @Operation(
            summary = "Gửi giải trình cho báo cáo",
            description =
                    "Yêu cầu đăng nhập và là bên bị báo cáo. Gửi nội dung giải trình kèm ảnh bằng chứng khi hồ sơ đang chờ phản hồi và còn hạn; mỗi hồ sơ chỉ được giải trình một lần. Thành công trả 204.")
    @PostMapping("/{reportId}/response")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void respond(
            @CurrentUserId Long userId,
            @PathVariable @Min(1) long reportId,
            @Valid @RequestBody RespondReportRequest request) {
        reportService.respond(userId, reportId, request);
    }
}
