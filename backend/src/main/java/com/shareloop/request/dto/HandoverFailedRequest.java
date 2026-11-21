package com.shareloop.request.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * DTO báo giao nhận không thành công (GN-06, GN-07).
 *
 * @param reason lý do, 5 đến 500 ký tự; là lý do huỷ hoặc nội dung khiếu nại tuỳ bên kia đã bấm hoàn tất chưa
 * @param evidenceMediaIds id ảnh hoặc video minh chứng đã tải lên (tuỳ chọn)
 */
public record HandoverFailedRequest(
        @NotBlank(message = "Lý do không được để trống")
        @Size(min = 5, max = 500, message = "Lý do phải từ 5 đến 500 ký tự")
        String reason,

        List<Long> evidenceMediaIds) {}
