package com.shareloop.catalog;

import com.shareloop.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** Mã lỗi của module catalog; tiền tố CATALOG_ và là một phần của contract (docs/api/catalog.md). */
public enum CatalogErrorCode implements ErrorCode {
    CATALOG_CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy danh mục.");

    private final HttpStatus status;
    private final String defaultMessage;

    CatalogErrorCode(HttpStatus status, String defaultMessage) {
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
