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
    LISTINGFEE_BOOST_EXCEEDS_EXPIRY(HttpStatus.CONFLICT, "Thời gian đẩy không được vượt quá hạn hiển thị của bài."),
    // Bốn mã dưới không ghi ngày giờ: mốc cụ thể trả qua boost-quote để giao diện tự hiển thị.
    LISTINGFEE_BOOST_COOLDOWN(HttpStatus.CONFLICT, "Bài vừa hết đợt đẩy, cần chờ hết thời gian nghỉ mới đẩy lại được."),
    LISTINGFEE_BOOST_CHAIN_LIMIT(HttpStatus.CONFLICT, "Đợt đẩy này đã đạt số ngày đẩy liên tục tối đa."),
    LISTINGFEE_BOOST_USER_LIMIT(HttpStatus.CONFLICT, "Bạn đã có đủ số bài đang đẩy cùng lúc cho phép."),
    LISTINGFEE_BOOST_SOLD_OUT(HttpStatus.CONFLICT, "Đã hết suất đẩy bài, vui lòng thử lại sau.");

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
