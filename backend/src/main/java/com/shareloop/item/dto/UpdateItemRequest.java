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

/** Full item content submitted for another review; omitted images keep the current set. */
public record UpdateItemRequest(
        @NotNull Long categoryId,
        @NotNull Long areaId,
        @NotNull OfferType offerType,
        @NotBlank @Size(max = 150) String title,
        @NotBlank String description,
        String desiredItem,
        @NotNull ItemCondition condition,
        String defectNote,
        @Size(max = 80) String brand,
        List<String> images,
        List<String> defectImages,
        OperatingStatus operatingStatus,

        @Positive(message = "Giá tham khảo phải lớn hơn 0")
        @Digits(integer = 16, fraction = 0, message = "Giá tham khảo phải là số nguyên tính bằng đồng")
        @DecimalMax(value = "9007199254740991", message = "Giá tham khảo vượt quá giới hạn cho phép")
        BigDecimal referencePrice) {

    public UpdateItemRequest(
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
}
