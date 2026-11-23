package com.shareloop.request.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * DTO không đồng ý với đề nghị huỷ (GN-06). Giao dịch chuyển sang sự cố giao nhận nên giải trình là bắt buộc.
 *
 * @param explanation giải trình, 5 đến 500 ký tự
 * @param evidenceMediaIds id ảnh hoặc video đã tải lên (không bắt buộc)
 */
public record RejectCancelRequest(
        @NotBlank(message = "Giải trình không được để trống")
        @Size(min = 5, max = 500, message = "Giải trình phải từ 5 đến 500 ký tự")
        String explanation,

        List<Long> evidenceMediaIds) {}
