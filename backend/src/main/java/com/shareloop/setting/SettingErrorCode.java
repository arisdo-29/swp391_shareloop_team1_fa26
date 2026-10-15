package com.shareloop.setting;

import com.shareloop.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum SettingErrorCode implements ErrorCode {
    SETTING_CONFIG_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy cấu hình."),
    SETTING_CONFIG_INVALID_TYPE(HttpStatus.BAD_REQUEST, "Giá trị cấu hình không đúng kiểu."),
    SETTING_CONFIG_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "Giá trị cấu hình nằm ngoài khoảng cho phép.");

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
