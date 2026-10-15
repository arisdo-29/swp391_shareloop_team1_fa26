package com.shareloop.item.dto;

import com.shareloop.item.entity.ItemCondition;
import com.shareloop.item.entity.OfferType;
import com.shareloop.item.entity.OperatingStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

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
        String brand,

        List<String> images,
        List<String> defectImages,
        OperatingStatus operatingStatus) {

    public CreateItemRequest(
            Long categoryId,
            Long areaId,
            OfferType offerType,
            String title,
            String description,
            String desiredItem,
            ItemCondition condition,
            String defectNote,
            String brand,
            List<String> images) {
        this(
                categoryId,
                areaId,
                offerType,
                title,
                description,
                desiredItem,
                condition,
                defectNote,
                brand,
                images,
                null,
                null);
    }

    public CreateItemRequest(
            Long categoryId,
            Long areaId,
            OfferType offerType,
            String title,
            String description,
            String desiredItem,
            ItemCondition condition,
            String defectNote,
            String brand) {
        this(
                categoryId,
                areaId,
                offerType,
                title,
                description,
                desiredItem,
                condition,
                defectNote,
                brand,
                List.of(),
                null,
                null);
    }
}
