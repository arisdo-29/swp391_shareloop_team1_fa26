package com.shareloop.request;

import com.shareloop.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** Mã lỗi của module request; tiền tố REQUEST_ và là một phần của contract (docs/api/request.md). */
public enum RequestErrorCode implements ErrorCode {
    REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu giao dịch."),
    REQUEST_INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "Chuyển trạng thái yêu cầu không hợp lệ."),
    REQUEST_ALREADY_EXISTS(HttpStatus.CONFLICT, "Bạn đã có một yêu cầu đang hoạt động cho bài đăng này."),
    REQUEST_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "Bạn đã đạt giới hạn tối đa 5 yêu cầu đang chờ xử lý."),
    REQUEST_SELF_OFFER_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "Không thể gửi yêu cầu cho bài đăng của chính mình."),
    REQUEST_ITEM_NOT_AVAILABLE(HttpStatus.CONFLICT, "Bài đăng không ở trạng thái sẵn sàng để gửi yêu cầu."),
    REQUEST_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy bài đăng."),
    REQUEST_SWAP_OFFERED_ITEM_REQUIRED(
            HttpStatus.UNPROCESSABLE_ENTITY, "Yêu cầu trao đổi bắt buộc phải chọn món đề nghị."),
    REQUEST_SCHEDULE_LOCKED(HttpStatus.CONFLICT, "Lịch hẹn đã được xác nhận và bị khoá, không thể chỉnh sửa."),
    REQUEST_SCHEDULE_PAST_TIME(HttpStatus.BAD_REQUEST, "Thời gian hẹn giao nhận phải ở tương lai."),
    REQUEST_WAIVER_REQUIRED(
            HttpStatus.BAD_REQUEST, "Hình thức giao hàng (Shipping) bắt buộc phải xác nhận cam kết chịu trách nhiệm."),
    REQUEST_SCHEDULE_NOT_SET(HttpStatus.BAD_REQUEST, "Chưa có thông tin lịch hẹn để xác nhận."),
    REQUEST_CONTACT_NOT_REVEALED(
            HttpStatus.CONFLICT, "Thông tin liên lạc chỉ được hiển thị sau khi hai bên đã chốt lịch hẹn."),
    REQUEST_FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền thao tác trên yêu cầu này.");

    private final HttpStatus status;
    private final String defaultMessage;

    RequestErrorCode(HttpStatus status, String defaultMessage) {
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
