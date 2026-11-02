package com.shareloop.search.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiDescriptionRequest(
        @NotBlank @Size(max = 150) String title) {}
