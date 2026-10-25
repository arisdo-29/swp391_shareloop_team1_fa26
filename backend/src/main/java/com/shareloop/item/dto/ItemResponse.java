package com.shareloop.item.dto;

import com.shareloop.item.entity.ItemCondition;
import com.shareloop.item.entity.ItemStatus;
import com.shareloop.item.entity.OfferType;
import com.shareloop.item.entity.OperatingStatus;
import com.shareloop.reputation.entity.Rank;
import java.time.Instant;
import java.util.List;

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
        List<String> images,
        ItemStatus status,
        Instant createdAt,
        Instant updatedAt,
        List<String> defectImages,
        OperatingStatus operatingStatus,
        // Hai cột do module listingfee quản lý; null khi bài chưa từng được duyệt / chưa từng được đẩy.
        Instant expireAt,
        Instant boostedUntil,
        String donorName,
        Integer donorTrustStars,
        Rank donorRank,
        Double donorAsDescribedRate) {

    public ItemResponse(
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
            List<String> images,
            ItemStatus status,
            Instant createdAt,
            Instant updatedAt) {
        this(
                id,
                donorId,
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
                status,
                createdAt,
                updatedAt,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public ItemResponse(
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
            Instant updatedAt) {
        this(
                id,
                donorId,
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
                status,
                createdAt,
                updatedAt,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public ItemResponse withDonorReputation(
            String donorName, Integer donorTrustStars, Rank donorRank, Double donorAsDescribedRate) {
        return new ItemResponse(
                id,
                donorId,
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
                status,
                createdAt,
                updatedAt,
                defectImages,
                operatingStatus,
                expireAt,
                boostedUntil,
                donorName,
                donorTrustStars,
                donorRank,
                donorAsDescribedRate);
    }
}
