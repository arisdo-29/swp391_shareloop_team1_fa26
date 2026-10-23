package com.shareloop.user.controller;

import com.shareloop.common.api.PageResponse;
import com.shareloop.common.security.CurrentUserId;
import com.shareloop.user.dto.AdminAccountRole;
import com.shareloop.user.dto.AdminAccountState;
import com.shareloop.user.dto.AdminUserSummary;
import com.shareloop.user.dto.LockUserRequest;
import com.shareloop.user.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller dành cho Admin để khoá và mở khoá tài khoản người dùng (FR-107, Issue #116).
 * Được bảo vệ tập trung bởi SecurityConfig (/api/v1/admin/** có role ADMIN).
 */
@Tag(name = "admin-user-controller", description = "Admin khoá và mở khoá tài khoản người dùng")
@RestController
@RequestMapping("/api/v1/admin/users")
@Validated
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /**
     * Danh sách tài khoản có tìm kiếm, lọc và phân trang.
     */
    @Operation(
            summary = "Danh sách tài khoản",
            description =
                    "Chỉ Admin. Tìm theo họ tên, email hoặc số điện thoại, lọc theo trạng thái và vai trò, tài khoản mới nhất trước.")
    @ApiResponse(responseCode = "200", description = "Thành công")
    @ApiResponse(responseCode = "400", description = "Tham số lọc hoặc phân trang không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "403", description = "Không phải Admin")
    @GetMapping
    public PageResponse<AdminUserSummary> list(
            @Parameter(description = "Từ khoá khớp một phần họ tên, email hoặc số điện thoại")
                    @RequestParam(required = false)
                    String keyword,
            @Parameter(description = "Trạng thái: ACTIVE, LOCKED, PENDING_VERIFICATION hoặc BANNED")
                    @RequestParam(required = false)
                    AdminAccountState status,
            @Parameter(description = "Vai trò: ADMIN hoặc USER") @RequestParam(required = false) AdminAccountRole role,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return adminUserService.list(keyword, status, role, page, size);
    }

    /**
     * Khoá tài khoản người dùng.
     */
    @Operation(
            summary = "Khoá tài khoản",
            description = "Chỉ Admin. Bắt buộc có lý do, hành động được ghi vào nhật ký.")
    @ApiResponse(responseCode = "200", description = "Khoá thành công")
    @ApiResponse(responseCode = "400", description = "Thiếu lý do hoặc tự khoá chính mình")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "403", description = "Không phải Admin")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy người dùng")
    @ApiResponse(responseCode = "409", description = "Tài khoản đã bị khoá từ trước")
    @PostMapping("/{userId}/lock")
    public void lock(
            @Parameter(description = "ID người dùng cần khoá") @PathVariable Long userId,
            @Valid @RequestBody LockUserRequest req,
            @Parameter(hidden = true) @CurrentUserId Long adminId) {
        adminUserService.lock(adminId, userId, req.reason());
    }

    /**
     * Mở khoá tài khoản người dùng.
     */
    @Operation(
            summary = "Mở khoá tài khoản",
            description = "Chỉ Admin. Bắt buộc có lý do, hành động được ghi vào nhật ký.")
    @ApiResponse(responseCode = "200", description = "Mở khoá thành công")
    @ApiResponse(responseCode = "400", description = "Thiếu lý do")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "403", description = "Không phải Admin")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy người dùng")
    @ApiResponse(responseCode = "409", description = "Tài khoản đang hoạt động, không cần mở khoá")
    @PostMapping("/{userId}/unlock")
    public void unlock(
            @Parameter(description = "ID người dùng cần mở khoá") @PathVariable Long userId,
            @Valid @RequestBody LockUserRequest req,
            @Parameter(hidden = true) @CurrentUserId Long adminId) {
        adminUserService.unlock(adminId, userId, req.reason());
    }
}
