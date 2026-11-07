package com.shareloop.report.controller;

import com.shareloop.common.security.CurrentUserId;
import com.shareloop.report.dto.CreateReportRequest;
import com.shareloop.report.dto.MyReportSummary;
import com.shareloop.report.dto.ReportResponse;
import com.shareloop.report.dto.RespondReportRequest;
import com.shareloop.report.dto.RespondentReportDetail;
import com.shareloop.report.service.MyReportQueryService;
import com.shareloop.report.service.ReportEvidenceService;
import com.shareloop.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final MyReportQueryService myReportQueryService;
    private final ReportEvidenceService reportEvidenceService;

    public ReportController(
            ReportService reportService,
            MyReportQueryService myReportQueryService,
            ReportEvidenceService reportEvidenceService) {
        this.reportService = reportService;
        this.myReportQueryService = myReportQueryService;
        this.reportEvidenceService = reportEvidenceService;
    }

    @Operation(
            summary = "Xem báo cáo và khiếu nại tôi đã gửi",
            description =
                    "Yêu cầu đăng nhập. Trả mọi báo cáo bài đăng, người dùng và khiếu nại giao dịch người dùng đã "
                            + "gửi, mới nhất lên trước, kèm trạng thái xử lý và ghi chú kết luận của Admin.")
    @ApiResponse(responseCode = "200", description = "Danh sách hồ sơ đã gửi")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @GetMapping("/mine")
    public List<MyReportSummary> listMine(@Parameter(hidden = true) @CurrentUserId Long currentUserId) {
        return myReportQueryService.listSent(currentUserId);
    }

    @Operation(
            summary = "Xem hồ sơ cần tôi giải trình",
            description = "Yêu cầu đăng nhập. Trả các báo cáo và khiếu nại nhắm vào người dùng đã có hạn giải trình, "
                    + "mới nhất lên trước. Không kèm người gửi và mô tả để giữ kín bên báo cáo.")
    @ApiResponse(responseCode = "200", description = "Danh sách hồ sơ cần giải trình")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @GetMapping("/against-me")
    public List<MyReportSummary> listAgainstMe(@Parameter(hidden = true) @CurrentUserId Long currentUserId) {
        return myReportQueryService.listAgainstMe(currentUserId);
    }

    @Operation(
            summary = "Xem chi tiết hồ sơ để giải trình",
            description = "Yêu cầu đăng nhập và là bên bị phản ánh của hồ sơ đã có hạn giải trình. Trả lý do, mô tả, "
                    + "ảnh của bên báo cáo, hạn giải trình và giải trình đã gửi; không kèm người gửi.")
    @ApiResponse(responseCode = "200", description = "Chi tiết hồ sơ")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(
            responseCode = "404",
            description = "Không có hồ sơ, không phải bên bị phản ánh hoặc hồ sơ chưa có hạn giải trình")
    @GetMapping("/{reportId}/respondent-view")
    public RespondentReportDetail respondentView(
            @Parameter(hidden = true) @CurrentUserId Long currentUserId, @PathVariable @Min(1) long reportId) {
        return myReportQueryService.detailForRespondent(currentUserId, reportId);
    }

    @Operation(
            summary = "Xem ảnh của hồ sơ cần giải trình",
            description = "Cùng quyền với respondent-view. Ảnh phải thuộc hồ sơ; trả nội dung ảnh riêng tư và không "
                    + "cho lưu bộ nhớ đệm.")
    @GetMapping("/{reportId}/evidence/{mediaId}")
    public ResponseEntity<byte[]> respondentEvidence(
            @Parameter(hidden = true) @CurrentUserId Long currentUserId,
            @PathVariable @Min(1) long reportId,
            @PathVariable @Min(1) long mediaId) {
        var content = reportEvidenceService.readForRespondent(currentUserId, reportId, mediaId);
        // no-store: ảnh bằng chứng riêng tư, không để trình duyệt hay proxy giữ lại bản sao.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(content.mimeType()))
                .body(content.bytes());
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
