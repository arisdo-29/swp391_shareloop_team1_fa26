package com.shareloop.item.dto;

import com.shareloop.item.entity.ItemCondition;
import com.shareloop.item.entity.ItemStatus;
import com.shareloop.item.entity.OfferType;
import com.shareloop.item.entity.OperatingStatus;
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
        String donorRank,
        Double donorAsDescribedRate,
        Boolean donorLocked) {

    public ItemResponse withDonor(
            String name, Integer trustStars, String rank, Double asDescribedRate, Boolean donorLocked) {
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
                name,
                trustStars,
                rank,
                asDescribedRate,
                donorLocked);
    }

    public ItemResponse withDonor(String name, Integer trustStars, String rank, Double asDescribedRate) {
        return withDonor(name, trustStars, rank, asDescribedRate, null);
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
            List<String> images,
            ItemStatus status,
            Instant createdAt,
            Instant updatedAt,
            List<String> defectImages,
            OperatingStatus operatingStatus,
            Instant expireAt,
            Instant boostedUntil,
            String donorName,
            Integer donorTrustStars,
            String donorRank,
            Double donorAsDescribedRate) {
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
                defectImages,
                operatingStatus,
                expireAt,
                boostedUntil,
                donorName,
                donorTrustStars,
                donorRank,
                donorAsDescribedRate,
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
}
