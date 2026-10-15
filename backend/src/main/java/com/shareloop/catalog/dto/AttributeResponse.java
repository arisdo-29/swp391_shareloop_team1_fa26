package com.shareloop.catalog.dto;

import com.shareloop.catalog.entity.AttributeDataType;
import java.util.List;

public record AttributeResponse(
        Long id,
        Long categoryId,
        String name,
        AttributeDataType dataType,
        List<String> options,
        boolean required,
        int sortOrder) {}
