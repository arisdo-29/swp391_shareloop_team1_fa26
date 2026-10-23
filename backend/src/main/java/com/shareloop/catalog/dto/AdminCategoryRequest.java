package com.shareloop.catalog.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * sortOrder có thể bỏ trống: khi tạo mới thì danh mục được đặt xuống cuối danh sách, khi sửa thì giữ thứ tự cũ.
 * Muốn đổi thứ tự thì dùng PUT /api/v1/admin/categories/reorder.
 */
public record AdminCategoryRequest(
        @NotBlank @Size(max = 100) String name,
        Long parentId,
        @NotNull Boolean restricted,
        @NotNull Boolean boostable,
        @Min(0) Integer sortOrder) {}
