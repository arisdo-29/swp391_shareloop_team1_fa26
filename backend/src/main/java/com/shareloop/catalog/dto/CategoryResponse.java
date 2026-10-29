package com.shareloop.catalog.dto;

/** Dữ liệu danh mục trả cho client; không bao giờ trả entity ra API. */
public record CategoryResponse(
        Long id,
        String name,
        Long parentId,
        boolean restricted,
        boolean boostable,
        int sortOrder,
        long activeItemCount) {
    public CategoryResponse(Long id, String name, Long parentId, boolean restricted, boolean boostable, int sortOrder) {
        this(id, name, parentId, restricted, boostable, sortOrder, 0);
    }
}
