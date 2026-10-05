package com.shareloop.item.dto;

import com.shareloop.item.entity.ItemCondition;
import com.shareloop.item.entity.ItemStatus;
import com.shareloop.item.entity.OfferType;
import java.time.Instant;

/**
 * DTO trả về thông tin bài đăng (UC-20, UC-22).
 */
public record ItemResponse(
        Long id,
        Long donorId,
        Long categoryId,
        Long areaId,
        OfferType offerType,
        String title,
        String description,
        String desiredItem,
        ItemCondition condition,
        String defectNote,
        String brand,
        ItemStatus status,
        Instant createdAt,
        Instant updatedAt) {}
