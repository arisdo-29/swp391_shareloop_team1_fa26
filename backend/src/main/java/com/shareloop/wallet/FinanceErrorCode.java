package com.shareloop.wallet;

import com.shareloop.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum FinanceErrorCode implements ErrorCode {
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "Khoảng ngày không hợp lệ (từ ngày phải trước hoặc bằng đến ngày).");

    private final HttpStatus status;
    private final String defaultMessage;

    FinanceErrorCode(HttpStatus status, String defaultMessage) {
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
