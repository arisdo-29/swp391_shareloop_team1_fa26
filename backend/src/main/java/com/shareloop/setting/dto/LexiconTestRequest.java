package com.shareloop.setting.dto;

import jakarta.validation.constraints.NotBlank;

public record LexiconTestRequest(
        @NotBlank(message = "Text không được để trống.") String text) {}
