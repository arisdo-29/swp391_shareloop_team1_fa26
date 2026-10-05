package com.shareloop.item.dto;

import com.shareloop.item.entity.ItemCondition;
import com.shareloop.item.entity.OfferType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO gửi lên khi tạo bài đăng mới (POST /api/v1/items).
 */
public record CreateItemRequest(
        @NotNull(message = "Danh mục không được để trống") Long categoryId,
        @NotNull(message = "Khu vực không được để trống") Long areaId,

        @NotNull(message = "Hình thức đăng bài không được để trống")
        OfferType offerType,

        @NotBlank(message = "Tiêu đề không được để trống") @Size(max = 150, message = "Tiêu đề tối đa 150 ký tự")
        String title,

        @NotBlank(message = "Mô tả không được để trống") String description,
        String desiredItem,

        @NotNull(message = "Tình trạng đồ không được để trống")
        ItemCondition condition,

        String defectNote,

        @Size(max = 80, message = "Thương hiệu tối đa 80 ký tự")
        String brand) {}
