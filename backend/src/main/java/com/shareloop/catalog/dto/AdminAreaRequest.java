package com.shareloop.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminAreaRequest(@NotBlank @Size(max = 100) String name, Long parentId) {}
