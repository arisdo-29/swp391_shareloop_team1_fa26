package com.shareloop.wallet.controller;

import com.shareloop.common.api.PageResponse;
import com.shareloop.wallet.dto.FinanceSummaryResponse;
import com.shareloop.wallet.dto.WalletTransactionResponse;
import com.shareloop.wallet.service.AdminFinanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller báo cáo tài chính và tra cứu lịch sử tài chính cho Admin (FR-109, FR-110, FR-112).
 * Chỉ Admin mới có quyền truy cập (/api/v1/admin/** được bảo vệ bởi SecurityConfig).
 */
@Tag(name = "admin-finance-controller", description = "Báo cáo tài chính và tra cứu lịch sử giao dịch")
@RestController
@RequestMapping("/api/v1/admin/finance")
@Validated
public class AdminFinanceController {

    private final AdminFinanceService adminFinanceService;

    public AdminFinanceController(AdminFinanceService adminFinanceService) {
        this.adminFinanceService = adminFinanceService;
    }

    /**
     * Báo cáo tài chính: Tiền mặt thu, Credit cộng vào, Credit trừ ra, đối chiếu với TopUp.
     */
    @Operation(
            summary = "Báo cáo tài chính",
            description = "Chỉ Admin. Tiền mặt thu, Credit cộng vào, Credit trừ ra, "
                    + "đối chiếu với TopUp. Dữ liệu lấy qua CreditService.")
    @ApiResponse(responseCode = "200", description = "Thành công")
    @ApiResponse(responseCode = "400", description = "Khoảng ngày không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "403", description = "Không phải Admin")
    @GetMapping("/report")
    public FinanceSummaryResponse report(
            @Parameter(description = "Từ ngày (yyyy-MM-dd)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @Parameter(description = "Đến ngày (yyyy-MM-dd)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to) {
        return adminFinanceService.getReport(from, to);
    }

    /**
     * Tra cứu lịch sử ví Credit của một người dùng.
     */
    @Operation(summary = "Lịch sử tài chính của một người dùng", description = "Chỉ Admin. Phân trang, mới nhất trước.")
    @ApiResponse(responseCode = "200", description = "Thành công")
    @ApiResponse(responseCode = "400", description = "Khoảng ngày không hợp lệ hoặc tham số phân trang sai")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "403", description = "Không phải Admin")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy người dùng")
    @GetMapping("/users/{userId}/history")
    public PageResponse<WalletTransactionResponse> userHistory(
            @Parameter(description = "ID người dùng") @PathVariable Long userId,
            @Parameter(description = "Từ ngày (yyyy-MM-dd)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @Parameter(description = "Đến ngày (yyyy-MM-dd)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return adminFinanceService.getUserHistory(userId, from, to, page, size);
    }
}
