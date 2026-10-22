package com.shareloop.reputation.controller;

import com.shareloop.common.security.CurrentUserId;
import com.shareloop.reputation.dto.GraceStarRequest;
import com.shareloop.reputation.dto.GraceStarResponse;
import com.shareloop.reputation.service.ReputationService;
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
 * Controller dành cho Admin cấp sao ân hạn (FR-95, UC-51, Issue #150).
 * Được bảo vệ tập trung bởi SecurityConfig (/api/v1/admin/** có role ADMIN).
 */
@Tag(name = "admin-reputation-controller", description = "Admin cấp sao ân hạn cho người dùng")
@RestController
@RequestMapping("/api/v1/admin/users")
@Validated
public class AdminReputationController {

    private final ReputationService reputationService;

    public AdminReputationController(ReputationService reputationService) {
        this.reputationService = reputationService;
    }

    /**
     * Cấp 1 sao ân hạn cho người dùng đang có 0 sao.
     */
    @Operation(
            summary = "Cấp sao ân hạn",
            description = "Chỉ Admin. Người dùng phải đang có 0 sao và chưa từng được cấp; mỗi tài khoản chỉ một lần. "
                    + "Bắt buộc có lý do, hành động được ghi vào nhật ký.")
    @ApiResponse(responseCode = "200", description = "Cấp thành công, người dùng có 1 sao")
    @ApiResponse(responseCode = "400", description = "Thiếu lý do hoặc lý do quá 500 ký tự")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "403", description = "Không phải Admin")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy người dùng")
    @ApiResponse(responseCode = "409", description = "Đã được cấp sao ân hạn trước đó hoặc người dùng vẫn còn sao")
    @PostMapping("/{userId}/grace-star")
    public GraceStarResponse grantGraceStar(
            @Parameter(description = "ID người dùng được cấp sao ân hạn") @PathVariable Long userId,
            @Valid @RequestBody GraceStarRequest req,
            @Parameter(hidden = true) @CurrentUserId Long adminId) {
        return reputationService.grantGraceStar(adminId, userId, req.reason());
    }
}
