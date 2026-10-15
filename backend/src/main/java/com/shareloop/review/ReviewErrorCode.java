package com.shareloop.review;

import com.shareloop.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ReviewErrorCode implements ErrorCode {
    REVIEW_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy bài đăng cần duyệt."),
    REVIEW_REJECT_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "Lý do từ chối là bắt buộc."),
    REVIEW_CHECKLIST_NOT_CONFIGURED(HttpStatus.CONFLICT, "Checklist duyệt bài chưa được cấu hình."),
    REVIEW_CHECKLIST_INCOMPLETE(HttpStatus.BAD_REQUEST, "Cần trả lời đạt tất cả mục checklist để duyệt bài.");

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
