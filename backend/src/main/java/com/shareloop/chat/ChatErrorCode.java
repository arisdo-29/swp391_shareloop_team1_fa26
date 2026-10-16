package com.shareloop.chat;

import com.shareloop.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Mã lỗi của module chat; tiền tố CHAT_ và là một phần của contract (docs/api/chat.md).
 */
public enum ChatErrorCode implements ErrorCode {
    CHAT_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu giao dịch."),
    CHAT_FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền tham gia cuộc trò chuyện này."),
    CHAT_REQUEST_NOT_RESERVED(
            HttpStatus.CONFLICT, "Khung chat chỉ mở khi yêu cầu ở trạng thái đã được chọn (RESERVED)."),
    CHAT_MESSAGE_EMPTY(HttpStatus.BAD_REQUEST, "Nội dung tin nhắn không được để trống."),
    CHAT_MESSAGE_BLOCKED(HttpStatus.BAD_REQUEST, "Tin nhắn bị chặn do chứa thông tin liên hệ."),
    CHAT_IMAGE_BEFORE_LOGISTICS_FORBIDDEN(
            HttpStatus.CONFLICT, "Chỉ được gửi ảnh sau khi hai bên đã chốt lịch hẹn (LOGISTICS_CONFIRMED).");

    private final HttpStatus status;
    private final String defaultMessage;

    ChatErrorCode(HttpStatus status, String defaultMessage) {
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
