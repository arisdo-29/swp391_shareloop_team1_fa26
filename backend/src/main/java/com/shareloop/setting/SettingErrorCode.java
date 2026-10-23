package com.shareloop.setting;

import com.shareloop.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum SettingErrorCode implements ErrorCode {
    SETTING_CONFIG_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy cấu hình."),
    SETTING_CONFIG_INVALID_TYPE(HttpStatus.BAD_REQUEST, "Giá trị cấu hình không đúng kiểu."),
    SETTING_CONFIG_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "Giá trị cấu hình nằm ngoài khoảng cho phép."),
    SETTING_RANK_THRESHOLD_ORDER(
            HttpStatus.BAD_REQUEST, "Điểm tối thiểu các hạng phải tăng dần: Bạc nhỏ hơn Vàng, Vàng nhỏ hơn Kim cương."),
    SETTING_LEXICON_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy từ khóa kiểm duyệt."),
    SETTING_LEXICON_INVALID_VALUE(HttpStatus.BAD_REQUEST, "Thông tin từ khóa kiểm duyệt không hợp lệ."),
    SETTING_LEXICON_DUPLICATE(HttpStatus.CONFLICT, "Từ khóa đã tồn tại trong nhóm và phạm vi này."),
    SETTING_CHECKLIST_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy mục checklist."),
    SETTING_CHECKLIST_INVALID_VALUE(HttpStatus.BAD_REQUEST, "Thông tin mục checklist không hợp lệ."),
    SETTING_CHECKLIST_AT_LEAST_ONE_REQUIRED(
            HttpStatus.BAD_REQUEST, "Phải luôn có ít nhất 1 mục checklist đang dùng (BR-A06).");

    private final HttpStatus status;
    private final String defaultMessage;

    SettingErrorCode(HttpStatus status, String defaultMessage) {
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
