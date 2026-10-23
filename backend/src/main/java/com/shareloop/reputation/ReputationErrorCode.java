package com.shareloop.reputation;

import com.shareloop.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** Mã lỗi của module reputation; tiền tố REPUTATION_ và là một phần của contract API. */
public enum ReputationErrorCode implements ErrorCode {
    REPUTATION_GRACE_STAR_ALREADY_USED(
            HttpStatus.CONFLICT, "Người dùng đã được cấp sao ân hạn, mỗi tài khoản chỉ được cấp một lần."),
    REPUTATION_GRACE_STAR_NOT_ELIGIBLE(HttpStatus.CONFLICT, "Chỉ cấp sao ân hạn khi người dùng đang có 0 sao.");

    private final HttpStatus status;
    private final String defaultMessage;

    ReputationErrorCode(HttpStatus status, String defaultMessage) {
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
