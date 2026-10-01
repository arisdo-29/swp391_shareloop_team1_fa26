package com.shareloop.listingfee;

import com.shareloop.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** Mã lỗi của module listingfee; tiền tố LISTINGFEE_ và là một phần của contract. */
public enum ListingFeeErrorCode implements ErrorCode {
    LISTINGFEE_NOTHING_HELD(HttpStatus.CONFLICT, "Bài đăng không có khoản phí nào đang giữ."),
    LISTINGFEE_ALREADY_HELD(HttpStatus.CONFLICT, "Bài đăng đã có khoản phí đang giữ."),
    LISTINGFEE_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy bài đăng."),
    LISTINGFEE_NOT_OWNER(HttpStatus.FORBIDDEN, "Bạn không phải chủ bài đăng."),
    LISTINGFEE_EDIT_NOT_ALLOWED(HttpStatus.CONFLICT, "Bài đăng ở trạng thái hiện tại không được sửa.");

    private final HttpStatus status;
    private final String defaultMessage;

    ListingFeeErrorCode(HttpStatus status, String defaultMessage) {
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
