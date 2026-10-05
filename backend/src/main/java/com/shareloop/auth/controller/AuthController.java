package com.shareloop.auth.controller;

import com.shareloop.auth.dto.AuthResponse;
import com.shareloop.auth.dto.LoginRequest;
import com.shareloop.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Controller mỏng: nhận request, gọi service, trả DTO thẳng. /api/v1/auth/** là công khai (SecurityConfig). */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // @Valid: Spring kiểm các ràng buộc trên LoginRequest; sai thì GlobalExceptionHandler trả VALIDATION_FAILED.
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    // JWT không lưu trạng thái ở server nên không có gì để thu hồi; client tự xoá token.
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {}
}
