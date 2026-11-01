package com.shareloop.review;

import com.shareloop.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ReviewErrorCode implements ErrorCode {
    REVIEW_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy bài đăng cần duyệt."),
    REVIEW_REJECT_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "Lý do từ chối là bắt buộc."),
    REVIEW_CHECKLIST_NOT_CONFIGURED(HttpStatus.CONFLICT, "Checklist duyệt bài chưa được cấu hình."),
    REVIEW_CHECKLIST_INCOMPLETE(HttpStatus.BAD_REQUEST, "Cần trả lời đạt tất cả mục checklist để duyệt bài."),
    REVIEW_CHECKLIST_OUTDATED(HttpStatus.CONFLICT, "Checklist vừa được cập nhật, vui lòng tải lại trang."),
    // L5: bài của tài khoản bị khoá hoặc bị cấm không được hiện công khai.
    REVIEW_DONOR_LOCKED(HttpStatus.CONFLICT, "Chủ bài đang bị khoá hoặc bị cấm nên không duyệt được bài.");

    // Bốn mã AUTHENTICITY_* đã bỏ cùng chức năng nhãn chính hãng (L16).

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
