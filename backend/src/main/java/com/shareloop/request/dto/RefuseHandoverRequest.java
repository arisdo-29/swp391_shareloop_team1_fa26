package com.shareloop.request.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * DTO từ chối nhận món đồ lúc giao nhận (GN-07, BC-09).
 *
 * @param reason lý do từ chối, 5 đến 1000 ký tự; trở thành nội dung khiếu nại "Không đúng mô tả"
 * @param evidenceMediaIds id ảnh hoặc video minh chứng đã tải lên (tuỳ chọn)
 */
public record RefuseHandoverRequest(
        @NotBlank(message = "Lý do từ chối không được để trống")
        @Size(min = 5, max = 1000, message = "Lý do từ chối phải từ 5 đến 1000 ký tự")
        String reason,

        List<Long> evidenceMediaIds) {}
