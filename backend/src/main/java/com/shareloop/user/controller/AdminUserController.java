package com.shareloop.user.controller;

import com.shareloop.common.security.CurrentUserId;
import com.shareloop.user.dto.LockUserRequest;
import com.shareloop.user.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
