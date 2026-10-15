package com.shareloop.catalog.dto;

public record AdminCategoryResponse(
        Long id, String name, Long parentId, boolean restricted, boolean boostable, int sortOrder, boolean active) {}
