package com.shareloop.catalog;

import com.shareloop.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum CategoryAttributeErrorCode implements ErrorCode {
    CATALOG_ATTRIBUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy thuộc tính."),
    CATALOG_INVALID_ATTRIBUTE_OPTIONS(HttpStatus.UNPROCESSABLE_CONTENT, "Tùy chọn thuộc tính không hợp lệ."),
    CATALOG_REQUIRED_ATTRIBUTE(HttpStatus.CONFLICT, "Không thể tắt thuộc tính bắt buộc của đồ điện tử.");

    private final HttpStatus status;
    private final String defaultMessage;

    CategoryAttributeErrorCode(HttpStatus status, String defaultMessage) {
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
