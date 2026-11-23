package com.shareloop.request.dto;

import java.time.Instant;

/**
 * Các mốc hạn của giao dịch do backend tính (GN-13), gộp vào RequestResponse bằng JsonUnwrapped. FE chỉ hiện, không tự
 * cộng ngày. Mốc nào không áp dụng ở trạng thái hiện tại thì null.
 */
public record RequestDeadlines(
        Instant autoCancelAt,
        Instant autoConfirmAt,
        Instant cancelRequestExpiresAt,
        Instant rescheduleExpiresAt,
        Instant sendDeadlineAt) {

    public static final RequestDeadlines EMPTY = new RequestDeadlines(null, null, null, null, null);
}
