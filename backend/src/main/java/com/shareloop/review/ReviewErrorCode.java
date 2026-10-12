package com.shareloop.review;

import com.shareloop.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ReviewErrorCode implements ErrorCode {
    REVIEW_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy bài đăng."),
    REVIEW_AUTH_PROOF_NOT_FOUND(HttpStatus.UNPROCESSABLE_ENTITY, "Bài đăng chưa có chứng từ chính hãng hợp lệ."),
    REVIEW_AUTH_INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "Trạng thái xác minh chứng từ không hợp lệ.");

    private final HttpStatus status;
    private final String defaultMessage;

    ReviewErrorCode(HttpStatus status, String defaultMessage) {
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
