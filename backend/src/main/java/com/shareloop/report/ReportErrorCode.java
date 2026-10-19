package com.shareloop.report;

import com.shareloop.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ReportErrorCode implements ErrorCode {
    REPORT_NOT_PARTICIPANT(HttpStatus.FORBIDDEN, "Bạn không phải bên liên quan của khiếu nại."),
    REPORT_RESPONSE_NOT_ALLOWED(HttpStatus.CONFLICT, "Hồ sơ không còn nhận giải trình."),
    REPORT_TARGET_REQUIRED(HttpStatus.BAD_REQUEST, "Đối tượng báo cáo là bắt buộc."),
    REPORT_CANNOT_REPORT_SELF(HttpStatus.BAD_REQUEST, "Không thể tự báo cáo chính mình hoặc bài đăng của chính mình."),
    REPORT_ALREADY_REPORTED(HttpStatus.CONFLICT, "Bạn đã báo cáo đối tượng này trước đó."),
    REPORT_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy đối tượng cần báo cáo."),
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy báo cáo vi phạm."),
    DISPUTE_NOT_A_PARTY(HttpStatus.FORBIDDEN, "Bạn không phải là một bên trong giao dịch này."),
    DISPUTE_REQUEST_NOT_COMPLETED(HttpStatus.CONFLICT, "Chỉ có thể khiếu nại giao dịch đã hoàn thành."),
    DISPUTE_WINDOW_EXPIRED(HttpStatus.UNPROCESSABLE_ENTITY, "Đã quá thời hạn 7 ngày để khiếu nại giao dịch."),
    DISPUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy khiếu nại giao dịch."),
    DISPUTE_NOT_RESPONDENT(HttpStatus.FORBIDDEN, "Chỉ bên bị khiếu nại mới có quyền giải trình."),
    DISPUTE_ALREADY_CONCLUDED(HttpStatus.CONFLICT, "Khiếu nại này đã được kết luận, không thể giải trình thêm."),
    DISPUTE_ALREADY_RESPONDED(HttpStatus.CONFLICT, "Bạn đã gửi bản giải trình cho khiếu nại này rồi."),
    DISPUTE_RESPONSE_WINDOW_EXPIRED(HttpStatus.UNPROCESSABLE_ENTITY, "Đã quá thời hạn 3 ngày để gửi giải trình."),
    DISPUTE_INVALID_EVIDENCE(HttpStatus.BAD_REQUEST, "Bằng chứng không hợp lệ hoặc không thuộc quyền sở hữu của bạn."),
    REPORT_ALREADY_CONCLUDED(HttpStatus.CONFLICT, "Báo cáo này đã được kết luận trước đó.");

    private final HttpStatus status;
    private final String defaultMessage;

    ReportErrorCode(HttpStatus status, String defaultMessage) {
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
