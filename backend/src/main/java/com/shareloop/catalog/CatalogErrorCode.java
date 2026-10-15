package com.shareloop.catalog;

import com.shareloop.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** Mã lỗi của module catalog; tiền tố CATALOG_ và là một phần của contract (docs/api/catalog.md). */
public enum CatalogErrorCode implements ErrorCode {
    CATALOG_CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy danh mục."),
    CATALOG_INVALID_PARENT(HttpStatus.UNPROCESSABLE_CONTENT, "Danh mục cha không hợp lệ."),
    CATALOG_AREA_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy khu vực."),
    CATALOG_INVALID_AREA_PARENT(HttpStatus.UNPROCESSABLE_CONTENT, "Khu vực cha không hợp lệ."),
    CATALOG_AREA_HAS_CHILDREN(HttpStatus.CONFLICT, "Không thể chuyển khu vực cha thành khu vực con."),
    CATALOG_ATTRIBUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy thuộc tính."),
    CATALOG_INVALID_ATTRIBUTE_OPTIONS(HttpStatus.UNPROCESSABLE_CONTENT, "Tùy chọn thuộc tính không hợp lệ."),
    CATALOG_REQUIRED_ATTRIBUTE(HttpStatus.CONFLICT, "Không thể tắt thuộc tính bắt buộc của đồ điện tử.");

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
