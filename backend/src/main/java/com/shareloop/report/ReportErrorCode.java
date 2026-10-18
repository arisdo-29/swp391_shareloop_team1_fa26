package com.shareloop.report;

import com.shareloop.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ReportErrorCode implements ErrorCode {
    REPORT_TARGET_REQUIRED(HttpStatus.BAD_REQUEST, "Đối tượng báo cáo là bắt buộc."),
    REPORT_CANNOT_REPORT_SELF(HttpStatus.BAD_REQUEST, "Không thể tự báo cáo chính mình hoặc bài đăng của chính mình."),
    REPORT_ALREADY_REPORTED(HttpStatus.CONFLICT, "Bạn đã báo cáo đối tượng này trước đó."),
    REPORT_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy đối tượng cần báo cáo.");

    private final HttpStatus status;
    private final String defaultMessage;

    ReportErrorCode(HttpStatus status, String defaultMessage) {
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
