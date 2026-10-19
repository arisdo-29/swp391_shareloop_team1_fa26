package com.shareloop.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record AuthenticityDecisionRequest(
        @Schema(
                description = "Lý do xử lý (bắt buộc khi từ chối hoặc thu hồi)",
                example = "Chứng từ không khớp thông tin sản phẩm")
        @NotBlank(message = "Lý do là bắt buộc.")
        String reason) {}
