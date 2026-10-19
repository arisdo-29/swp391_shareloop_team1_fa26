package com.shareloop.search;

import com.shareloop.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum SearchErrorCode implements ErrorCode {
    SEARCH_QUERY_INVALID(HttpStatus.BAD_REQUEST, "Nhu cầu tìm đồ không hợp lệ."),
    SEARCH_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản tìm kiếm."),
    SEARCH_AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Trợ lý tìm đồ hiện chưa sẵn sàng."),
    SEARCH_AI_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "Trợ lý tìm đồ đã quá thời gian chờ."),
    SEARCH_AI_FAILED(HttpStatus.BAD_GATEWAY, "Không thể xử lý yêu cầu tìm đồ bằng AI."),
    SEARCH_AI_INVALID_RESPONSE(HttpStatus.BAD_GATEWAY, "Trợ lý tìm đồ trả về bộ lọc không hợp lệ.");

    private final HttpStatus status;
    private final String message;

    SearchErrorCode(HttpStatus status, String message) {
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
