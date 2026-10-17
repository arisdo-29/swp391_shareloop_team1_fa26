package com.shareloop.user;

import com.shareloop.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** Mã lỗi của module user; tiền tố USER_ và là một phần của contract API. */
public enum UserErrorCode implements ErrorCode {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng."),
    USER_ALREADY_LOCKED(HttpStatus.CONFLICT, "Tài khoản đã bị khoá."),
    USER_NOT_LOCKED(HttpStatus.CONFLICT, "Tài khoản đang hoạt động, không cần mở khoá."),
    USER_AREA_INVALID(HttpStatus.BAD_REQUEST, "Khu vực không tồn tại hoặc đã ngừng hoạt động."),
    USER_CANNOT_LOCK_SELF(HttpStatus.BAD_REQUEST, "Không thể tự khoá tài khoản của chính mình.");

    private final HttpStatus status;
    private final String defaultMessage;

    UserErrorCode(HttpStatus status, String defaultMessage) {
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
