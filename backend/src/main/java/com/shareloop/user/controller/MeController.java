package com.shareloop.user.controller;

import com.shareloop.common.security.CurrentUserId;
import com.shareloop.user.dto.MeResponse;
import com.shareloop.user.dto.UpdateProfileRequest;
import com.shareloop.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Thông tin người đang đăng nhập. Cần token: SecurityConfig yêu cầu xác thực cho mọi đường dẫn không công khai. */
@Tag(name = "Me", description = "Tài khoản của người đang đăng nhập")
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final UserService userService;

    public MeController(UserService userService) {
        this.userService = userService;
    }

    // @CurrentUserId: resolver đọc id (claim sub) từ JWT, không cần truyền id trên URL.
    // @Parameter(hidden = true): ẩn userId khỏi Swagger UI vì id lấy từ token, không phải tham số client nhập.
    @Operation(summary = "Xem hồ sơ của tôi")
    @GetMapping
    public MeResponse getMe(@Parameter(hidden = true) @CurrentUserId long userId) {
        return userService.getMe(userId);
    }

    // Chỉ sửa hồ sơ của chính mình (BR-U03): id lấy từ token nên không thể sửa người khác.
    @Operation(summary = "Cập nhật hồ sơ của tôi (họ tên, khu vực mặc định, ảnh đại diện)")
    @PutMapping
    public MeResponse updateMe(
            @Parameter(hidden = true) @CurrentUserId long userId, @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(userId, request);
    }
}
