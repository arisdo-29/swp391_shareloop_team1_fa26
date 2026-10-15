package com.shareloop.catalog.dto;

import com.shareloop.catalog.entity.AttributeDataType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AdminAttributeRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull AttributeDataType dataType,
        List<@NotBlank @Size(max = 255) String> options,
        @NotNull Boolean required,
        @NotNull @Min(0) Integer sortOrder) {}
