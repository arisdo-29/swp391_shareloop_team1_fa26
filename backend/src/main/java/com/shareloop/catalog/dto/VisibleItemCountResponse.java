package com.shareloop.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Số bài đang hiển thị công khai sẽ bị ẩn khỏi Tìm đồ khi tắt danh mục hoặc khu vực")
public record VisibleItemCountResponse(
        @Schema(
                description = "Số bài đang hiển thị công khai thuộc danh mục hoặc khu vực, gồm cả mục con",
                example = "12")
        long visibleItemCount) {}
