package com.shareloop.item.dto;

import java.util.List;

/**
 * Một trang danh sách bài.
 *
 * @param rotationSeed lượt xoay bài đẩy đã dùng cho trang này; FE gửi lại khi chuyển trang để các trang cùng một lượt
 *     (không trùng, không sót bài). null ở các danh sách không xoay bài đẩy (vd tab hồ sơ).
 */
public record ItemListPageResponse(
        List<ItemResponse> items, int page, int size, long totalElements, int totalPages, Long rotationSeed) {

    // Danh sách không xoay bài đẩy (tab hồ sơ) dùng constructor này, rotationSeed để null.
    public ItemListPageResponse(List<ItemResponse> items, int page, int size, long totalElements, int totalPages) {
        this(items, page, size, totalElements, totalPages, null);
    }
}
