package com.shareloop.catalog.dto;

import jakarta.validation.constraints.NotNull;

public record AdminAreaActiveRequest(@NotNull Boolean active) {}
