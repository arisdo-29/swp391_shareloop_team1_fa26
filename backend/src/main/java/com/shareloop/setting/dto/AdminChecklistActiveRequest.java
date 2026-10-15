package com.shareloop.setting.dto;

import jakarta.validation.constraints.NotNull;

public record AdminChecklistActiveRequest(
        @NotNull(message = "active không được để trống.") Boolean active) {}
