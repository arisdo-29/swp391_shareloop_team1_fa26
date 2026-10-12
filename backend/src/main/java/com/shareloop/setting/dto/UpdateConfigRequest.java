package com.shareloop.setting.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateConfigRequest(@NotBlank String value, @NotBlank String reason) {}
