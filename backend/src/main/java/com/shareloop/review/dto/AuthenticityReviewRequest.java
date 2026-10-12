package com.shareloop.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AuthenticityReviewRequest(
        @NotNull Boolean approved,
        @NotBlank @Size(max = 1000) String reason) {}
