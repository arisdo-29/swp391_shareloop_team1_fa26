package com.shareloop.common.exception;

import java.util.Map;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String field;
    private final Map<String, Object> details;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null, null, null);
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, null, null);
    }

    public BusinessException(ErrorCode errorCode, String message, String field, Map<String, Object> details) {
        super(message != null ? message : errorCode.defaultMessage());
        this.errorCode = errorCode;
        this.field = field;
        this.details = details;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getField() {
        return field;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
