package com.shareloop.media;

import com.shareloop.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum MediaErrorCode implements ErrorCode {
    MEDIA_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy ảnh."),
    MEDIA_INVALID_FILE(HttpStatus.UNPROCESSABLE_CONTENT, "Tệp không phải ảnh JPG, PNG hoặc WebP hợp lệ."),
    MEDIA_FILE_TOO_LARGE(HttpStatus.UNPROCESSABLE_CONTENT, "Ảnh vượt quá kích thước cho phép."),
    MEDIA_NOT_OWNER(HttpStatus.FORBIDDEN, "Bạn không sở hữu ảnh này."),
    MEDIA_NOT_UPLOADED(HttpStatus.CONFLICT, "Ảnh đã được gắn hoặc không còn dùng được."),
    MEDIA_STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể lưu ảnh.");

    private final HttpStatus status;
    private final String message;

    MediaErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
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
        return message;
    }
}
