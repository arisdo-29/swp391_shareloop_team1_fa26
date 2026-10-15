package com.shareloop.catalog.dto;

import jakarta.validation.constraints.NotNull;

public record AdminCategoryActiveRequest(@NotNull Boolean active) {}
