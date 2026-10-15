package com.shareloop.item.dto;

import com.shareloop.item.entity.ItemCondition;
import com.shareloop.item.entity.OfferType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
        List<String> images) {}
