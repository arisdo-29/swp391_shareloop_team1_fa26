package com.shareloop.catalog.dto;

/** Dữ liệu danh mục trả cho client; không bao giờ trả entity ra API. */
public record CategoryResponse(
        Long id, String name, Long parentId, boolean restricted, boolean boostable, int sortOrder) {}
