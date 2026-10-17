package com.shareloop.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Yêu cầu từ chối bài đăng")
public record RejectReviewRequest(
        @NotBlank(message = "Lý do từ chối là bắt buộc.")
        @Schema(
                description = "Lý do từ chối bài đăng (chọn từ mẫu hoặc tự nhập)",
                example = "Ảnh không phải ảnh thật của món đồ hoặc lấy từ mạng.")
        String reason) {}
