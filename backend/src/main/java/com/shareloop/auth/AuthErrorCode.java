package com.shareloop.auth;

import com.shareloop.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** Mã lỗi của module auth; tiền tố AUTH_ và là một phần của contract (docs/api/auth.md). */
public enum AuthErrorCode implements ErrorCode {
    AUTH_EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "Email đã được đăng ký."),
    AUTH_PHONE_ALREADY_USED(HttpStatus.CONFLICT, "Số điện thoại đã được sử dụng."),
    AUTH_PHONE_INVALID(HttpStatus.BAD_REQUEST, "Số điện thoại không đúng định dạng."),
    AUTH_EMAIL_DOMAIN_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "Tên miền email không được phép."),
    AUTH_OTP_INVALID(HttpStatus.BAD_REQUEST, "Mã OTP không đúng."),
    AUTH_OTP_EXPIRED(HttpStatus.BAD_REQUEST, "Mã OTP đã hết hạn."),
    AUTH_OTP_TOO_MANY_ATTEMPTS(HttpStatus.UNPROCESSABLE_ENTITY, "Bạn đã nhập sai quá nhiều lần, vui lòng gửi lại mã."),
    AUTH_OTP_RESEND_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "Vui lòng chờ trước khi gửi lại mã."),
    AUTH_EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "Email chưa được xác thực."),
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng."),
    AUTH_ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "Tài khoản đã bị khoá.");

    private final HttpStatus status;
    private final String defaultMessage;

    AuthErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
