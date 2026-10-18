package com.shareloop.audit.controller;

import com.shareloop.audit.dto.ActivityLogDto;
import com.shareloop.audit.service.ActivityLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller xem nhật ký hành động của Admin (FR-115, Issue #117).
 */
@Tag(name = "admin-audit-controller", description = "Nhật ký hành động của Admin")
@RestController
@RequestMapping("/api/v1/admin/activity-logs")
@Validated
public class AdminActivityLogController {

    private final ActivityLogService service;

    public AdminActivityLogController(ActivityLogService service) {
        this.service = service;
    }

    /**
     * Xem danh sách nhật ký hành động có lọc và phân trang.
     */
    @Operation(
            summary = "Xem nhật ký hành động",
            description =
                    "Chỉ Admin. Lọc theo người thực hiện, loại hành động, khoảng ngày. Phân trang, mới nhất trước.")
    @ApiResponse(responseCode = "200", description = "Thành công")
    @ApiResponse(responseCode = "400", description = "Khoảng ngày không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "403", description = "Không phải Admin")
    @GetMapping
    public Page<ActivityLogDto> list(
            @Parameter(description = "ID người thực hiện") @RequestParam(required = false) Long actorId,
            @Parameter(description = "Loại hành động") @RequestParam(required = false) String type,
            @Parameter(description = "Từ ngày (yyyy-MM-dd)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @Parameter(description = "Đến ngày (yyyy-MM-dd)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.search(actorId, type, from, to, page, size);
    }
}
