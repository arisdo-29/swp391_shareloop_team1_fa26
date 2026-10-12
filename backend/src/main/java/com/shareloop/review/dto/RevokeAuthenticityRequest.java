package com.shareloop.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RevokeAuthenticityRequest(
        @NotBlank @Size(max = 1000) String reason) {}
