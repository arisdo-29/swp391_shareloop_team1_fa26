package com.shareloop.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Một thuộc tính động của bài đăng")
public record ReviewItemAttribute(
        @Schema(description = "ID thuộc tính", example = "5")
        Long attributeId,

        @Schema(description = "Tên thuộc tính; null nếu thuộc tính đã bị xoá khỏi danh mục", example = "Kích cỡ")
        String name,

        @Schema(description = "Giá trị người đăng nhập", example = "L")
        String value) {}
