package com.shareloop.admin.controller;

import com.shareloop.admin.dto.AnomalyReportResponse;
import com.shareloop.admin.service.AdminAnomalyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller cảnh báo bất thường cho Admin (AD-04, UC-52, Issue #148).
 * Chỉ Admin mới có quyền truy cập (/api/v1/admin/** được bảo vệ bởi SecurityConfig).
 */
@Tag(
        name = "admin-anomaly-controller",
        description = "Cảnh báo bất thường: cặp giao dịch lặp và nhiều tài khoản cùng IP")
@RestController
@RequestMapping("/api/v1/admin/anomalies")
public class AdminAnomalyController {

    private final AdminAnomalyService adminAnomalyService;

    public AdminAnomalyController(AdminAnomalyService adminAnomalyService) {
        this.adminAnomalyService = adminAnomalyService;
    }

    /**
     * Danh sách cảnh báo bất thường, tính trực tiếp lúc gọi.
     */
    @Operation(
            summary = "Cảnh báo bất thường",
            description = "Chỉ Admin. Cặp người dùng có hơn N giao dịch hoàn tất với nhau trong cửa sổ ngày cấu hình "
                    + "(mặc định hơn 5 giao dịch trong 90 ngày) và các nhóm IP có từ M tài khoản đăng ký trong cửa sổ ngày cấu hình "
                    + "(mặc định từ 3 tài khoản trong 30 ngày). Tính trực tiếp lúc gọi, không lưu bảng.")
    @ApiResponse(responseCode = "200", description = "Thành công; danh sách rỗng khi không có cảnh báo nào")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "403", description = "Không phải Admin")
    @GetMapping
    public AnomalyReportResponse anomalies() {
        return adminAnomalyService.getAnomalies();
    }
}
