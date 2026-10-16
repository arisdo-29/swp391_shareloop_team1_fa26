package com.shareloop.item;

import com.shareloop.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Mã lỗi của module item; tiền tố ITEM_ và là một phần của contract (docs/api/item.md).
 */
public enum ItemErrorCode implements ErrorCode {
    ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy bài đăng."),
    ITEM_SWAP_DESIRED_ITEM_REQUIRED(HttpStatus.BAD_REQUEST, "Bài đăng trao đổi bắt buộc phải có món mong muốn."),
    ITEM_DEFECT_NOTE_REQUIRED(HttpStatus.BAD_REQUEST, "Bài đăng đồ lỗi bắt buộc phải có mô tả lỗi."),
    ITEM_INVALID_IMAGE(HttpStatus.BAD_REQUEST, "Ảnh phải là URL trả về từ API upload media."),
    ITEM_INVALID_IMAGE_COUNT(HttpStatus.UNPROCESSABLE_CONTENT, "Bài đăng cần từ 3 đến 8 ảnh."),
    ITEM_ACTIVE_LIMIT_REACHED(HttpStatus.CONFLICT, "Bạn đã đạt giới hạn bài đăng đang hoạt động."),
    ITEM_DEFECT_IMAGES_REQUIRED(HttpStatus.UNPROCESSABLE_CONTENT, "Đồ có lỗi cần ít nhất một ảnh chụp chỗ lỗi."),
    ITEM_DEFECT_IMAGES_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_CONTENT, "Chỉ đồ có lỗi mới dùng ảnh lỗi."),
    ITEM_OPERATING_STATUS_REQUIRED(HttpStatus.UNPROCESSABLE_CONTENT, "Đồ điện tử cần khai trạng thái hoạt động."),
    ITEM_OPERATING_STATUS_NOT_ALLOWED(
            HttpStatus.UNPROCESSABLE_CONTENT, "Danh mục này không dùng trạng thái hoạt động."),
    ITEM_NOT_OWNER(HttpStatus.FORBIDDEN, "Bạn không phải chủ bài đăng."),
    ITEM_EDIT_NOT_ALLOWED(HttpStatus.CONFLICT, "Bài đăng đã có yêu cầu được giữ chỗ hoặc không thể sửa."),
    ITEM_REMOVE_NOT_ALLOWED(HttpStatus.CONFLICT, "Bài đăng đã được giữ chỗ hoặc không thể gỡ."),
    ITEM_MODERATION_BLOCKED(HttpStatus.UNPROCESSABLE_CONTENT, "Nội dung bài đăng không qua kiểm duyệt tầng 1."),
    ITEM_INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "Không thể chuyển trạng thái bài đăng.");

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
