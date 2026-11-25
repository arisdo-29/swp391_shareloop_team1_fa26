package com.shareloop.item.dto;

import com.shareloop.item.entity.ItemCondition;
import com.shareloop.item.entity.OfferType;
import com.shareloop.item.entity.OperatingStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
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
        OperatingStatus operatingStatus,

        @NotNull(message = "Vui lòng nhập giá tham khảo")
        @Positive(message = "Giá tham khảo phải lớn hơn 0")
        @Digits(integer = 16, fraction = 0, message = "Giá tham khảo phải là số nguyên tính bằng đồng")
        @DecimalMax(value = "9007199254740991", message = "Giá tham khảo vượt quá giới hạn cho phép")
        BigDecimal referencePrice) {

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
            List<String> images,
            List<String> defectImages,
            OperatingStatus operatingStatus) {
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
                defectImages,
                operatingStatus,
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
