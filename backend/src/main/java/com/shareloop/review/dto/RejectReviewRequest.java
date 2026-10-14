package com.shareloop.review.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectReviewRequest(
        @NotBlank(message = "Lý do từ chối là bắt buộc.") String reason) {}
