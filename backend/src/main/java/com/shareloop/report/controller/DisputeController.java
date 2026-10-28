package com.shareloop.report.controller;

import com.shareloop.common.security.CurrentUserId;
import com.shareloop.report.dto.CreateDisputeRequest;
import com.shareloop.report.dto.DisputeResponse;
import com.shareloop.report.dto.DisputeResponseRequest;
import com.shareloop.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller xử lý khiếu nại giao dịch sau hoàn thành và giải trình (FR-89, FR-90, BR-T08, Issue #112).
 */
@Tag(name = "dispute-controller", description = "Khiếu nại giao dịch sau khi hoàn thành và tiếp nhận giải trình")
@RestController
@RequestMapping("/api/v1")
@Validated
public class DisputeController {

    private final ReportService reportService;

    public DisputeController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * Một trong hai bên của giao dịch gửi khiếu nại trong vòng 24 giờ sau khi giao dịch Completed (U08).
     */
    @Operation(
            summary = "Khiếu nại giao dịch đã hoàn thành",
            description =
                    "Một trong hai bên (người cho hoặc người nhận) của giao dịch đã Completed có thể gửi khiếu nại "
                            + "trong vòng 24 giờ kể từ thời điểm hoàn tất. Bên bị khiếu nại sẽ có 48 giờ để giải trình kèm bằng chứng.")
    @ApiResponse(responseCode = "201", description = "Ghi nhận khiếu nại thành công, mở cửa sổ 48 giờ giải trình")
    @ApiResponse(responseCode = "400", description = "Dữ liệu yêu cầu không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "403", description = "Người gọi không phải là một bên trong giao dịch này")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy yêu cầu giao dịch")
    @ApiResponse(
            responseCode = "409",
            description = "Giao dịch chưa hoàn thành hoặc người dùng đã khiếu nại giao dịch này trước đó")
    @ApiResponse(responseCode = "422", description = "Đã quá thời hạn 24 giờ sau Completed để gửi khiếu nại")
    @PostMapping("/requests/{requestId}/disputes")
    @ResponseStatus(HttpStatus.CREATED)
    public DisputeResponse open(
            @Parameter(description = "ID của yêu cầu giao dịch", example = "100") @PathVariable Long requestId,
            @Valid @RequestBody CreateDisputeRequest request,
            @Parameter(hidden = true) @CurrentUserId Long userId) {
        return reportService.openDispute(userId, requestId, request);
    }

    /**
     * Bên bị khiếu nại gửi giải trình kèm bằng chứng trong vòng 48 giờ kể từ lúc khiếu nại được mở (U08).
     */
    @Operation(
            summary = "Giải trình khiếu nại giao dịch",
            description =
                    "Chỉ bên bị khiếu nại mới có quyền gửi giải trình, trong thời hạn 48 giờ kể từ lúc khiếu nại được mở. "
                            + "Có thể đính kèm danh sách ID media làm bằng chứng đối chất.")
    @ApiResponse(responseCode = "200", description = "Ghi nhận giải trình thành công")
    @ApiResponse(responseCode = "400", description = "Nội dung giải trình trống hoặc vượt quá 1000 ký tự")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "403", description = "Người gọi không phải là bên bị khiếu nại")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy khiếu nại giao dịch")
    @ApiResponse(responseCode = "409", description = "Đã gửi bản giải trình trước đó hoặc khiếu nại đã được kết luận")
    @ApiResponse(responseCode = "422", description = "Đã quá thời hạn 48 giờ để gửi giải trình")
    @PostMapping("/disputes/{disputeId}/response")
    @ResponseStatus(HttpStatus.OK)
    public void respond(
            @Parameter(description = "ID của bản ghi khiếu nại", example = "1") @PathVariable Long disputeId,
            @Valid @RequestBody DisputeResponseRequest request,
            @Parameter(hidden = true) @CurrentUserId Long userId) {
        reportService.respondDispute(userId, disputeId, request);
    }
}
