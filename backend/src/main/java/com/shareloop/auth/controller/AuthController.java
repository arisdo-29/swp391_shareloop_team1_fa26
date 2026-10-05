package com.shareloop.auth.controller;

import com.shareloop.auth.dto.AuthResponse;
import com.shareloop.auth.dto.LoginRequest;
import com.shareloop.auth.dto.OtpSentResponse;
import com.shareloop.auth.dto.RegisterRequest;
import com.shareloop.auth.dto.ResendOtpRequest;
import com.shareloop.auth.dto.VerifyOtpRequest;
import com.shareloop.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
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

    // Lấy IP ở controller để service không phụ thuộc servlet (dễ test hơn).
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public OtpSentResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        return authService.register(request, httpRequest.getRemoteAddr());
    }

    @PostMapping("/verify-otp")
    public AuthResponse verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return authService.verifyOtp(request);
    }

    @PostMapping("/resend-otp")
    public OtpSentResponse resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        return authService.resendOtp(request);
    }

    // JWT không lưu trạng thái ở server nên không có gì để thu hồi; client tự xoá token.
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {}
}
