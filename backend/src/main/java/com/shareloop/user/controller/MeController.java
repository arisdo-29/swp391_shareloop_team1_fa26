package com.shareloop.user.controller;

import com.shareloop.auth.dto.AuthUserResponse;
import com.shareloop.common.security.CurrentUserId;
import com.shareloop.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
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
    @Operation(summary = "Xem thông tin tài khoản của tôi")
    @GetMapping
    public AuthUserResponse getMe(@Parameter(hidden = true) @CurrentUserId long userId) {
        return userService.getMe(userId);
    }
}
