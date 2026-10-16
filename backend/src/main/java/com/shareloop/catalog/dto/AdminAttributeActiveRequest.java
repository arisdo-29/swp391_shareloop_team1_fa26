package com.shareloop.catalog.dto;

import jakarta.validation.constraints.NotNull;

public record AdminAttributeActiveRequest(@NotNull Boolean active) {}
