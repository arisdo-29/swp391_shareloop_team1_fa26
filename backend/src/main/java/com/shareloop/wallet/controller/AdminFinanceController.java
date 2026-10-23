package com.shareloop.wallet.controller;

import com.shareloop.common.api.PageResponse;
import com.shareloop.wallet.dto.AdminFinanceUserSummary;
import com.shareloop.wallet.dto.FinanceSummaryResponse;
import com.shareloop.wallet.dto.WalletTransactionResponse;
import com.shareloop.wallet.mapper.FinanceReportCsvWriter;
import com.shareloop.wallet.service.AdminFinanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller báo cáo tài chính, xuất CSV và tra cứu lịch sử tài chính cho Admin (FR-109, FR-110, FR-111, FR-112).
 * Chỉ Admin mới có quyền truy cập (/api/v1/admin/** được bảo vệ bởi SecurityConfig).
 */
@Tag(name = "admin-finance-controller", description = "Báo cáo tài chính, xuất CSV và tra cứu lịch sử giao dịch")
@RestController
@RequestMapping("/api/v1/admin/finance")
@Validated
public class AdminFinanceController {

    private static final MediaType TEXT_CSV_UTF8 = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final AdminFinanceService adminFinanceService;
    private final FinanceReportCsvWriter financeReportCsvWriter;

    public AdminFinanceController(
            AdminFinanceService adminFinanceService, FinanceReportCsvWriter financeReportCsvWriter) {
        this.adminFinanceService = adminFinanceService;
        this.financeReportCsvWriter = financeReportCsvWriter;
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
     * Xuất báo cáo tài chính ra file CSV, cùng số liệu với {@code /report} (FR-111).
     *
     * <p>Không khai báo {@code produces} để lỗi nghiệp vụ (vd: INVALID_DATE_RANGE) vẫn trả JSON như mọi API khác.
     */
    @Operation(
            summary = "Xuất báo cáo tài chính CSV",
            description = "Chỉ Admin. Cùng số liệu với /report, trả file CSV UTF-8 có BOM để Excel "
                    + "hiển thị đúng tiếng Việt.")
    @ApiResponse(responseCode = "200", description = "File CSV", content = @Content(mediaType = "text/csv"))
    @ApiResponse(responseCode = "400", description = "Khoảng ngày không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "403", description = "Không phải Admin")
    @GetMapping("/report.csv")
    public ResponseEntity<byte[]> reportCsv(
            @Parameter(description = "Từ ngày (yyyy-MM-dd)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @Parameter(description = "Đến ngày (yyyy-MM-dd)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to) {
        FinanceSummaryResponse report = adminFinanceService.getReport(from, to);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(financeReportCsvWriter.fileName(from, to))
                .build();
        return ResponseEntity.ok()
                .contentType(TEXT_CSV_UTF8)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(financeReportCsvWriter.write(report, from, to));
    }

    /**
     * Danh sách người dùng kèm số liệu Credit cơ bản để tra cứu.
     */
    @Operation(
            summary = "Danh sách người dùng kèm Credit",
            description =
                    "Chỉ Admin. Tìm theo họ tên, email hoặc số điện thoại, người dùng mới nhất trước. Mỗi dòng có Credit khả dụng, đang giữ, tổng và tổng đã nạp. Không gồm tài khoản Admin.")
    @ApiResponse(responseCode = "200", description = "Thành công")
    @ApiResponse(responseCode = "400", description = "Tham số phân trang sai")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "403", description = "Không phải Admin")
    @GetMapping("/users")
    public PageResponse<AdminFinanceUserSummary> users(
            @Parameter(description = "Từ khoá khớp một phần họ tên, email hoặc số điện thoại")
                    @RequestParam(required = false)
                    String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return adminFinanceService.listUsers(keyword, page, size);
    }

    /**
     * Thông tin và số liệu Credit của một người dùng.
     */
    @Operation(
            summary = "Ví của một người dùng",
            description = "Chỉ Admin. Họ tên, email, số điện thoại và số liệu Credit của một người dùng.")
    @ApiResponse(responseCode = "200", description = "Thành công")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "403", description = "Không phải Admin")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy người dùng")
    @GetMapping("/users/{userId}/wallet")
    public AdminFinanceUserSummary userWallet(@Parameter(description = "ID người dùng") @PathVariable long userId) {
        return adminFinanceService.getUserFinance(userId);
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
