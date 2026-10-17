package com.shareloop.listingfee;

import com.shareloop.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** Mã lỗi của module listingfee; tiền tố LISTINGFEE_ và là một phần của contract. */
public enum ListingFeeErrorCode implements ErrorCode {
    LISTINGFEE_NOTHING_HELD(HttpStatus.CONFLICT, "Bài đăng không có khoản phí nào đang giữ."),
    LISTINGFEE_ALREADY_HELD(HttpStatus.CONFLICT, "Bài đăng đã có khoản phí đang giữ."),
    LISTINGFEE_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy bài đăng."),
    LISTINGFEE_NOT_OWNER(HttpStatus.FORBIDDEN, "Bạn không phải chủ bài đăng."),
    LISTINGFEE_EDIT_NOT_ALLOWED(HttpStatus.CONFLICT, "Bài đăng ở trạng thái hiện tại không được sửa."),
    LISTINGFEE_RENEW_NOT_ALLOWED(HttpStatus.CONFLICT, "Chỉ gia hạn được bài đang hiển thị."),
    LISTINGFEE_BOOST_NOT_ALLOWED(HttpStatus.CONFLICT, "Chỉ đẩy được bài đã duyệt và đang hiển thị."),
    LISTINGFEE_CATEGORY_NOT_BOOSTABLE(HttpStatus.CONFLICT, "Danh mục của bài đăng không cho phép đẩy bài."),
    LISTINGFEE_BOOST_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "Tổng thời gian đẩy vượt quá số ngày tối đa cho phép."),
    LISTINGFEE_BOOST_EXCEEDS_EXPIRY(HttpStatus.CONFLICT, "Thời gian đẩy không được vượt quá hạn hiển thị của bài.");

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
