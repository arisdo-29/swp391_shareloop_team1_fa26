package com.shareloop.request.dto;

import java.time.Instant;

/**
 * Đề nghị huỷ đang chờ bên kia phản hồi (GN-06), gộp vào RequestResponse bằng JsonUnwrapped nên JSON vẫn phẳng. Mọi
 * trường null khi không có đề nghị nào đang chờ.
 *
 * @param cancelRequestedBy id người đề nghị
 * @param cancelRequestedAt lúc đề nghị
 * @param cancelRequestCode mã lý do, ví dụ BUSY, COUNTERPART_NO_SHOW
 * @param cancelRequestLabel nhãn tiếng Việt của mã lý do để hiện thẳng cho người dùng
 * @param cancelRequestReason chữ người dùng nhập thêm
 */
public record CancelRequestInfo(
        Long cancelRequestedBy,
        Instant cancelRequestedAt,
        String cancelRequestCode,
        String cancelRequestLabel,
        String cancelRequestReason) {

    public static final CancelRequestInfo EMPTY = new CancelRequestInfo(null, null, null, null, null);
}
