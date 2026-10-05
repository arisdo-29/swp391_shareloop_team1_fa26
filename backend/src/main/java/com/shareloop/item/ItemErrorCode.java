package com.shareloop.item;

import com.shareloop.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Mã lỗi của module item; tiền tố ITEM_ và là một phần của contract (docs/api/item.md).
 */
public enum ItemErrorCode implements ErrorCode {
    ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy bài đăng."),
    ITEM_SWAP_DESIRED_ITEM_REQUIRED(HttpStatus.BAD_REQUEST, "Bài đăng trao đổi bắt buộc phải có món mong muốn."),
    ITEM_DEFECT_NOTE_REQUIRED(HttpStatus.BAD_REQUEST, "Bài đăng đồ lỗi bắt buộc phải có mô tả lỗi.");

    private final HttpStatus status;
    private final String defaultMessage;

    ItemErrorCode(HttpStatus status, String defaultMessage) {
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
