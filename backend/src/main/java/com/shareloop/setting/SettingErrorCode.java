package com.shareloop.setting;

import com.shareloop.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum SettingErrorCode implements ErrorCode {
    CONFIG_KEY_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy cấu hình."),
    CONFIG_VALUE_INVALID(HttpStatus.BAD_REQUEST, "Giá trị cấu hình không hợp lệ.");

    private final HttpStatus status;
    private final String defaultMessage;

    SettingErrorCode(HttpStatus status, String defaultMessage) {
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
