package com.shareloop.audit;

import com.shareloop.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AuditErrorCode implements ErrorCode {
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "Khoảng ngày không hợp lệ (từ ngày phải trước hoặc bằng đến ngày).");

    private final HttpStatus status;
    private final String defaultMessage;

    AuditErrorCode(HttpStatus status, String defaultMessage) {
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
