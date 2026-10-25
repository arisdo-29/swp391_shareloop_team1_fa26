package com.shareloop.audit.controller;

import com.shareloop.audit.dto.ActivityLogDto;
import com.shareloop.audit.dto.ActivityTypeDto;
import com.shareloop.audit.service.ActivityLogQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
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

    private final ActivityLogQueryService service;

    public AdminActivityLogController(ActivityLogQueryService service) {
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
            // Giữ đồng bộ với enum ActivityType (annotation chỉ nhận hằng số nên phải liệt kê tay).
            @Parameter(
                            description = "Loại hành động",
                            schema =
                                    @Schema(
                                            allowableValues = {
                                                "REVIEW_APPROVE",
                                                "REVIEW_REJECT",
                                                "CONFIG_UPDATE",
                                                "USER_LOCK",
                                                "USER_UNLOCK",
                                                "REPORT_DISMISSED",
                                                "REPORT_UPHELD",
                                                "POINTS",
                                                "STARS",
                                                "AI_USAGE",
                                                "LEXICON_CREATE",
                                                "LEXICON_UPDATE",
                                                "LEXICON_TOGGLE",
                                                "CHECKLIST_CREATE",
                                                "CHECKLIST_UPDATE",
                                                "CHECKLIST_TOGGLE",
                                                "CHECKLIST_REORDER",
                                                "CATEGORY_CREATE",
                                                "CATEGORY_UPDATE",
                                                "CATEGORY_TOGGLE",
                                                "CATEGORY_REORDER",
                                                "CATEGORY_ATTRIBUTE_CREATE",
                                                "CATEGORY_ATTRIBUTE_UPDATE",
                                                "CATEGORY_ATTRIBUTE_TOGGLE",
                                                "AREA_CREATE",
                                                "AREA_UPDATE",
                                                "AREA_TOGGLE",
                                                "AUTH_GRANT",
                                                "AUTH_REJECT",
                                                "AUTH_REVOKE",
                                                "GRACE_STAR",
                                                "STAR_RESTORED"
                                            }))
                    @RequestParam(required = false)
                    String type,
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

    /**
     * Danh sách loại hành động kèm nhãn tiếng Việt, dùng cho bộ lọc trên FE.
     */
    @Operation(
            summary = "Danh sách loại hành động",
            description = "Chỉ Admin. Trả về [{type, label}] theo ActivityType.")
    @ApiResponse(responseCode = "200", description = "Thành công")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "403", description = "Không phải Admin")
    @GetMapping("/types")
    public List<ActivityTypeDto> listTypes() {
        return service.listTypes();
    }
}
