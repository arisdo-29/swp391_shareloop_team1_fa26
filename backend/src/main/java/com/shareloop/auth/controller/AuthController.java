package com.shareloop.auth.controller;

import com.shareloop.auth.dto.AuthResponse;
import com.shareloop.auth.dto.ChangePasswordRequest;
import com.shareloop.auth.dto.ForgotPasswordRequest;
import com.shareloop.auth.dto.LoginRequest;
import com.shareloop.auth.dto.OtpSentResponse;
import com.shareloop.auth.dto.RegisterRequest;
import com.shareloop.auth.dto.ResendOtpRequest;
import com.shareloop.auth.dto.ResetPasswordRequest;
import com.shareloop.auth.dto.VerifyOtpRequest;
import com.shareloop.auth.service.AuthService;
import com.shareloop.common.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Controller mỏng: nhận request, gọi service, trả DTO thẳng. /api/v1/auth/** là công khai (SecurityConfig). */
// @Tag: Swagger UI gom các endpoint cùng tag vào một nhóm; @Operation(summary) hiện bên phải đường dẫn.
@Tag(name = "Auth", description = "Đăng ký, đăng nhập, OTP, đổi và quên mật khẩu")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // @Valid: Spring kiểm các ràng buộc trên LoginRequest; sai thì GlobalExceptionHandler trả VALIDATION_FAILED.
    @Operation(summary = "Đăng nhập bằng email và mật khẩu, trả JWT")
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    // Lấy IP ở controller để service không phụ thuộc servlet (dễ test hơn).
    @Operation(summary = "Đăng ký tài khoản, gửi OTP về email")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public OtpSentResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        return authService.register(request, httpRequest.getRemoteAddr());
    }

    @Operation(summary = "Xác nhận OTP đăng ký, kích hoạt tài khoản và trả JWT")
    @PostMapping("/verify-otp")
    public AuthResponse verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return authService.verifyOtp(request);
    }

    @Operation(summary = "Gửi lại OTP đăng ký")
    @PostMapping("/resend-otp")
    public OtpSentResponse resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        return authService.resendOtp(request);
    }

    // /api/v1/auth/** công khai trong SecurityConfig, nhưng @CurrentUserId tự trả 401 khi không có token.
    // userId đặt trước @RequestBody để thiếu token báo 401 trước khi kiểm body (400).
    @Operation(summary = "Đổi mật khẩu khi đã đăng nhập (cần token)")
    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            @Parameter(hidden = true) @CurrentUserId long userId, @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(userId, request);
    }

    // Luôn 200 cùng nội dung dù email có tài khoản hay không (không lộ email đã đăng ký).
    @Operation(summary = "Quên mật khẩu: gửi OTP đặt lại mật khẩu về email")
    @PostMapping("/forgot-password")
    public OtpSentResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return authService.forgotPassword(request);
    }

    @Operation(summary = "Đặt lại mật khẩu bằng OTP đã gửi qua email")
    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
    }

    // JWT không lưu trạng thái ở server nên không có gì để thu hồi; client tự xoá token.
    @Operation(summary = "Đăng xuất (server không lưu phiên, client tự xoá token)")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {}
}
