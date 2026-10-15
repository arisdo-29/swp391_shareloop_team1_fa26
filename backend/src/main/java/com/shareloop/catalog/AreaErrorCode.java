package com.shareloop.catalog;

import com.shareloop.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AreaErrorCode implements ErrorCode {
    CATALOG_AREA_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy khu vực."),
    CATALOG_INVALID_AREA_PARENT(HttpStatus.UNPROCESSABLE_CONTENT, "Khu vực cha không hợp lệ."),
    CATALOG_AREA_HAS_CHILDREN(HttpStatus.CONFLICT, "Không thể chuyển khu vực cha thành khu vực con.");

    private final HttpStatus status;
    private final String defaultMessage;

    AreaErrorCode(HttpStatus status, String defaultMessage) {
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
