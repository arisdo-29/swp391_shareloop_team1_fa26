package com.shareloop.catalog.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminCategoryRequest(
        @NotBlank @Size(max = 100) String name,
        Long parentId,
        @NotNull Boolean restricted,
        @NotNull Boolean boostable,
        @NotNull @Min(0) Integer sortOrder) {}
