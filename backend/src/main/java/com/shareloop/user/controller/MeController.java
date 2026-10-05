package com.shareloop.user.controller;

import com.shareloop.auth.dto.AuthUserResponse;
import com.shareloop.common.security.CurrentUserId;
import com.shareloop.user.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Thông tin người đang đăng nhập. Cần token: SecurityConfig yêu cầu xác thực cho mọi đường dẫn không công khai. */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final UserService userService;

    public MeController(UserService userService) {
        this.userService = userService;
    }

    // @CurrentUserId: resolver đọc id (claim sub) từ JWT, không cần truyền id trên URL.
    @GetMapping
    public AuthUserResponse getMe(@CurrentUserId long userId) {
        return userService.getMe(userId);
    }
}
