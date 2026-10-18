package com.shareloop.user;

import com.shareloop.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** Mã lỗi của module user; tiền tố USER_ và là một phần của contract API. */
public enum UserErrorCode implements ErrorCode {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng."),
    USER_ALREADY_LOCKED(HttpStatus.CONFLICT, "Tài khoản đã bị khoá."),
    USER_NOT_LOCKED(HttpStatus.CONFLICT, "Tài khoản đang hoạt động, không cần mở khoá."),
    USER_AREA_INVALID(HttpStatus.BAD_REQUEST, "Khu vực không tồn tại hoặc đã ngừng hoạt động."),
    USER_PHONE_ALREADY_USED(HttpStatus.CONFLICT, "Số điện thoại đã được sử dụng bởi tài khoản khác."),
    USER_PHONE_SAME_AS_CURRENT(HttpStatus.BAD_REQUEST, "Số điện thoại mới trùng với số điện thoại hiện tại."),
    USER_PHONE_CHANGE_COOLDOWN(HttpStatus.BAD_REQUEST, "Chỉ được đổi số điện thoại tối đa 1 lần trong 30 ngày."),
    USER_PHONE_CHANGE_ACTIVE_DELIVERY(
            HttpStatus.CONFLICT, "Không thể đổi số điện thoại khi đang có giao dịch chưa hoàn tất.");

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
