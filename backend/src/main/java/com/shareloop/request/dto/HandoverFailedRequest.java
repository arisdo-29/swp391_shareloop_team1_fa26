package com.shareloop.request.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.shareloop.request.entity.CancelRequestCode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * DTO báo giao nhận không thành công (GN-06, GN-07).
 *
 * @param code mã lý do chọn trong danh sách; bỏ trống thì coi là OTHER
 * @param reason chữ người dùng nhập, bắt buộc từ 5 ký tự khi code là OTHER; tối đa 500 ký tự
 * @param evidenceMediaIds id ảnh hoặc video minh chứng đã tải lên (tuỳ chọn)
 */
public record HandoverFailedRequest(
        CancelRequestCode code,

        @Size(max = 500, message = "Lý do tối đa 500 ký tự") String reason,

        List<Long> evidenceMediaIds) {

    // Chọn mã có sẵn thì không cần chữ; còn lại (không chọn mã hoặc "Lý do khác") phải có lý do từ 5 ký tự.
    @JsonIgnore
    @AssertTrue(message = "Lý do phải từ 5 đến 500 ký tự")
    public boolean isReasonProvided() {
        boolean pickedFixedCode = code != null && code != CancelRequestCode.OTHER;
        return pickedFixedCode || (reason != null && reason.trim().length() >= 5);
    }

    // Dạng cũ chỉ có lý do chữ (code mặc định OTHER) để code và test đang gọi vẫn compile.
    public HandoverFailedRequest(String reason, List<Long> evidenceMediaIds) {
        this(null, reason, evidenceMediaIds);
    }
}
